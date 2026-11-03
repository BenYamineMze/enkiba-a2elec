-- Prix d'achat et marge (admins uniquement).
-- Le prix d'achat vit dans des tables à part : les droits PostgreSQL ne peuvent pas
-- cacher une colonne de products aux seuls vendeurs, alors que la RLS cache des lignes.

-- ---------------------------------------------------------------------------
-- Prix d'achat actuel de chaque produit (dernier prix payé)
-- ---------------------------------------------------------------------------
create table public.product_costs (
  product_id uuid primary key,
  shop_id uuid not null references public.shops (id),
  cost_price numeric(12,2) not null check (cost_price >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);
create index product_costs_pull_idx on public.product_costs (shop_id, updated_at);

-- ---------------------------------------------------------------------------
-- Prix d'achat figé sur chaque ligne de vente, à l'arrivée de la ligne sur le serveur.
-- Rempli par trigger : les vendeurs créent les ventes sans jamais voir ce prix.
-- Pas de ligne = produit sans prix d'achat à ce moment-là (exclu de la marge).
-- ---------------------------------------------------------------------------
create table public.sale_item_costs (
  sale_item_id uuid primary key references public.sale_items (id),
  shop_id uuid not null references public.shops (id),
  cost_price numeric(12,2) not null check (cost_price >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create index sale_item_costs_pull_idx on public.sale_item_costs (shop_id, updated_at);

create trigger trg_set_updated_at before insert or update on public.product_costs
  for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.sale_item_costs
  for each row execute function public.set_updated_at();

create or replace function public.snapshot_sale_item_cost()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  insert into public.sale_item_costs (sale_item_id, shop_id, cost_price)
  select new.id, new.shop_id, c.cost_price
  from public.product_costs c
  where c.product_id = new.product_id and c.shop_id = new.shop_id
  on conflict (sale_item_id) do nothing;
  return new;
end;
$$;
create trigger trg_snapshot_sale_item_cost after insert on public.sale_items
  for each row execute function public.snapshot_sale_item_cost();
revoke execute on function public.snapshot_sale_item_cost() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Sécurité : lecture et écriture réservées aux admins de la boutique
-- ---------------------------------------------------------------------------
alter table public.product_costs enable row level security;
alter table public.sale_item_costs enable row level security;

create policy product_costs_select on public.product_costs for select to authenticated
  using (public.is_shop_admin(shop_id));
create policy product_costs_insert on public.product_costs for insert to authenticated
  with check (public.is_shop_admin(shop_id));
create policy product_costs_update on public.product_costs for update to authenticated
  using (public.is_shop_admin(shop_id)) with check (public.is_shop_admin(shop_id));
create policy sale_item_costs_select on public.sale_item_costs for select to authenticated
  using (public.is_shop_admin(shop_id));

-- L'app envoie un PATCH (cost_price) puis un INSERT si la ligne n'existe pas encore
grant select on public.product_costs, public.sale_item_costs to authenticated;
grant insert (product_id, shop_id, cost_price) on public.product_costs to authenticated;
grant update (cost_price) on public.product_costs to authenticated;
grant all on public.product_costs, public.sale_item_costs to service_role;
