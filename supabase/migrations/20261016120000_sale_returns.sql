-- Retours d'articles et quantité initiale des produits
--
-- 1. sale_returns : une ligne par retour. La vente d'origine reste intacte
--    (un vendeur ne peut pas modifier une vente) ; le stock est remis par un
--    mouvement 'sale_cancel' dont ref_id = id du retour.
-- 2. L'annulation complète d'une vente ne remet que ce qui n'a pas déjà été retourné.
-- 3. products.quantity est forcée à 0 à l'insertion : le stock ne vient que des mouvements.

-- ---------------------------------------------------------------------------
-- 1. Table des retours
-- ---------------------------------------------------------------------------
create table public.sale_returns (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  sale_id uuid not null,
  sale_item_id uuid not null references public.sale_items (id),
  product_id uuid not null,
  quantity integer not null check (quantity > 0),
  refund_amount numeric(12,2) not null check (refund_amount >= 0),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (sale_id, shop_id) references public.sales (id, shop_id),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);

create index idx_sale_returns_sync on public.sale_returns (shop_id, updated_at);
create index idx_sale_returns_item on public.sale_returns (sale_item_id);

create trigger trg_set_updated_at before insert or update on public.sale_returns
  for each row execute function public.set_updated_at();

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

-- Un retour remet le stock
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
-- 2. Annulation d'une vente : on ne remet que la quantité non retournée
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- 3. Un produit est toujours créé avec une quantité nulle
-- ---------------------------------------------------------------------------
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

-- ---------------------------------------------------------------------------
-- Sécurité : lecture par les membres, création par les membres en leur nom,
-- ni modification ni suppression (un retour est définitif)
-- ---------------------------------------------------------------------------
alter table public.sale_returns enable row level security;

create policy sale_returns_select on public.sale_returns for select to authenticated
  using (public.is_shop_member(shop_id));
create policy sale_returns_insert on public.sale_returns for insert to authenticated
  with check (public.is_shop_member(shop_id) and created_by = (select auth.uid()));

revoke all on public.sale_returns from anon, authenticated;
grant select, insert on public.sale_returns to authenticated;
grant all on public.sale_returns to service_role;

revoke execute on function public.check_sale_return(), public.stock_on_sale_return(),
  public.force_zero_quantity_on_product_insert() from public, anon, authenticated;
