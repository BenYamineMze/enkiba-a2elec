-- updated_at imposé par le serveur
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
                           'clients','sales','sale_items','quotes','quote_items'] loop
    execute format('create trigger trg_set_updated_at before insert or update on public.%I
                    for each row execute function public.set_updated_at()', t);
  end loop;
end;
$$;

-- Profil créé automatiquement avec le compte Auth (jamais de rôle ici)
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

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

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

-- Annuler une vente (deleted_at) remet le stock
create or replace function public.restore_stock_on_sale_cancel()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if old.deleted_at is null and new.deleted_at is not null then
    insert into public.stock_movements (shop_id, product_id, delta, reason, ref_id, created_by)
    select i.shop_id, i.product_id, i.quantity, 'sale_cancel', i.id, (select auth.uid())
    from public.sale_items i where i.sale_id = new.id
    on conflict (reason, ref_id) where ref_id is not null do nothing;
  end if;
  return new;
end;
$$;

create trigger trg_sale_cancel
  after update of deleted_at on public.sales
  for each row execute function public.restore_stock_on_sale_cancel();
