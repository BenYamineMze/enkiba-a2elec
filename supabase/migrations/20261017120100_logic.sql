-- Enkiba-A2elec — schéma v2, partie 2/3 : logique serveur (fonctions et triggers).
-- Règles : updated_at imposé par le serveur, profil créé avec le compte Auth,
-- stock calculé uniquement à partir des mouvements, retours contrôlés,
-- conversion d'un devis en vente une seule fois.

-- ---------------------------------------------------------------------------
-- updated_at imposé par le serveur (base du pull incrémental)
-- ---------------------------------------------------------------------------
create or replace function public.set_updated_at()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

do $$
declare t text;
begin
  foreach t in array array['shops','profiles','shop_members','products','stock_movements',
                           'clients','sales','sale_items','sale_returns','quotes','quote_items'] loop
    execute format('create trigger trg_set_updated_at before insert or update on public.%I
                    for each row execute function public.set_updated_at()', t);
  end loop;
end;
$$;

-- ---------------------------------------------------------------------------
-- Profil créé automatiquement avec le compte Auth (jamais de rôle ici :
-- le rôle vient de shop_members, posé par l'Edge Function create-seller)
-- ---------------------------------------------------------------------------
create or replace function public.handle_new_user()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.profiles (id, email, username, lastname, telephone)
  values (
    new.id, new.email,
    coalesce(new.raw_user_meta_data->>'username', ''),
    coalesce(new.raw_user_meta_data->>'lastname', ''),
    coalesce(new.raw_user_meta_data->>'telephone', '')
  )
  on conflict (id) do nothing;
  return new;
end;
$$;

-- Le trigger vit dans le schéma auth : on le recrée proprement s'il reste d'un ancien schéma
drop trigger if exists on_auth_user_created on auth.users;
create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ---------------------------------------------------------------------------
-- Stock : la quantité d'un produit ne vient que des mouvements
-- ---------------------------------------------------------------------------

-- Un produit est toujours créé avec une quantité nulle (le stock initial est un mouvement)
create or replace function public.force_zero_quantity_on_product_insert()
returns trigger language plpgsql set search_path = '' as $$
begin
  new.quantity := 0;
  return new;
end;
$$;

create trigger trg_products_zero_quantity
  before insert on public.products
  for each row execute function public.force_zero_quantity_on_product_insert();

-- Un mouvement de stock met à jour la quantité du produit
create or replace function public.apply_stock_movement()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  update public.products set quantity = quantity + new.delta where id = new.product_id;
  return new;
end;
$$;

create trigger trg_apply_stock_movement
  after insert on public.stock_movements
  for each row execute function public.apply_stock_movement();

-- Une ligne de vente décrémente le stock
create or replace function public.stock_on_sale_item()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.stock_movements (shop_id, product_id, delta, reason, ref_id, created_by)
  values (new.shop_id, new.product_id, -new.quantity, 'sale', new.id, (select auth.uid()))
  on conflict (reason, ref_id) where ref_id is not null do nothing;
  return new;
end;
$$;

create trigger trg_stock_on_sale_item
  after insert on public.sale_items
  for each row execute function public.stock_on_sale_item();

-- Annuler une vente (deleted_at) remet la quantité qui n'a pas déjà été retournée
create or replace function public.restore_stock_on_sale_cancel()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.deleted_at is null and new.deleted_at is not null then
    insert into public.stock_movements (shop_id, product_id, delta, reason, ref_id, created_by)
    select i.shop_id, i.product_id, i.quantity - coalesce(r.returned, 0), 'sale_cancel', i.id, (select auth.uid())
    from public.sale_items i
    left join (
      select sale_item_id, sum(quantity) as returned
      from public.sale_returns group by sale_item_id
    ) r on r.sale_item_id = i.id
    where i.sale_id = new.id
      and i.quantity - coalesce(r.returned, 0) > 0
    on conflict (reason, ref_id) where ref_id is not null do nothing;
  end if;
  return new;
end;
$$;

create trigger trg_sale_cancel
  after update of deleted_at on public.sales
  for each row execute function public.restore_stock_on_sale_cancel();

-- ---------------------------------------------------------------------------
-- Retours
-- ---------------------------------------------------------------------------

-- Contrôle d'un retour : cohérent avec la ligne de vente et sans dépasser la quantité vendue.
-- La ligne de vente est verrouillée pour éviter deux retours concurrents qui dépasseraient.
create or replace function public.check_sale_return()
returns trigger language plpgsql security definer set search_path = '' as $$
declare
  v_item public.sale_items%rowtype;
  v_sale_deleted timestamptz;
  v_returned integer;
begin
  select * into v_item from public.sale_items where id = new.sale_item_id for update;
  if not found or v_item.shop_id <> new.shop_id then
    raise exception 'sale_item_not_found' using errcode = 'P0001';
  end if;

  select deleted_at into v_sale_deleted from public.sales where id = v_item.sale_id;
  if v_sale_deleted is not null then
    raise exception 'sale_cancelled' using errcode = 'P0001';
  end if;

  select coalesce(sum(quantity), 0) into v_returned
  from public.sale_returns where sale_item_id = new.sale_item_id;
  if v_returned + new.quantity > v_item.quantity then
    raise exception 'return_exceeds_sold' using errcode = 'P0001';
  end if;

  -- La vente et le produit viennent toujours de la ligne de vente, jamais du client
  new.sale_id := v_item.sale_id;
  new.product_id := v_item.product_id;
  return new;
end;
$$;

create trigger trg_check_sale_return
  before insert on public.sale_returns
  for each row execute function public.check_sale_return();

-- Un retour remet le stock (mouvement 'sale_cancel' dont ref_id = id du retour)
create or replace function public.stock_on_sale_return()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.stock_movements (shop_id, product_id, delta, reason, ref_id, created_by)
  values (new.shop_id, new.product_id, new.quantity, 'sale_cancel', new.id, new.created_by)
  on conflict (reason, ref_id) where ref_id is not null do nothing;
  return new;
end;
$$;

create trigger trg_stock_on_sale_return
  after insert on public.sale_returns
  for each row execute function public.stock_on_sale_return();

-- ---------------------------------------------------------------------------
-- Devis : un devis converti est définitif, et ne peut l'être que vers la vente qui le cite
-- ---------------------------------------------------------------------------
create or replace function public.check_quote_conversion()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'UPDATE' and old.status = 'converted' then
    if new.status is distinct from 'converted'
       or new.converted_sale_id is distinct from old.converted_sale_id then
      raise exception 'quote_already_converted' using errcode = 'P0001';
    end if;
    return new;
  end if;

  if new.status = 'converted' then
    if new.converted_sale_id is null or not exists (
      select 1 from public.sales s
      where s.id = new.converted_sale_id and s.shop_id = new.shop_id
        and s.source_quote_id = new.id and s.deleted_at is null
    ) then
      raise exception 'invalid_quote_conversion' using errcode = 'P0001';
    end if;
  elsif new.converted_sale_id is not null then
    raise exception 'invalid_quote_conversion' using errcode = 'P0001';
  end if;
  return new;
end;
$$;

create trigger trg_check_quote_conversion
  before insert or update on public.quotes
  for each row execute function public.check_quote_conversion();
