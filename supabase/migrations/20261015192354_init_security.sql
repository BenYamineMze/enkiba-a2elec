-- Fonctions d'appartenance (SECURITY DEFINER : évitent la récursion de RLS)
create or replace function public.is_shop_member(p_shop uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.shop_members m join public.profiles p on p.id = m.user_id
    where m.shop_id = p_shop and m.user_id = (select auth.uid()) and p.active
  );
$$;

create or replace function public.is_shop_admin(p_shop uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.shop_members m join public.profiles p on p.id = m.user_id
    where m.shop_id = p_shop and m.user_id = (select auth.uid()) and m.role = 'admin' and p.active
  );
$$;

create or replace function public.shares_shop(p_user uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.shop_members a join public.shop_members b on a.shop_id = b.shop_id
    where a.user_id = (select auth.uid()) and b.user_id = p_user
  );
$$;

revoke execute on function public.is_shop_member(uuid), public.is_shop_admin(uuid), public.shares_shop(uuid) from public, anon;
grant execute on function public.is_shop_member(uuid), public.is_shop_admin(uuid), public.shares_shop(uuid) to authenticated;

-- RLS activée partout
alter table public.shops enable row level security;
alter table public.profiles enable row level security;
alter table public.shop_members enable row level security;
alter table public.products enable row level security;
alter table public.stock_movements enable row level security;
alter table public.clients enable row level security;
alter table public.sales enable row level security;
alter table public.sale_items enable row level security;
alter table public.quotes enable row level security;
alter table public.quote_items enable row level security;
alter table public.audit_log enable row level security;

-- shops / profiles / shop_members
create policy shops_select on public.shops for select to authenticated using (public.is_shop_member(id));
create policy shops_update on public.shops for update to authenticated using (public.is_shop_admin(id)) with check (public.is_shop_admin(id));
create policy profiles_select on public.profiles for select to authenticated
  using (id = (select auth.uid()) or public.shares_shop(id));
create policy profiles_update on public.profiles for update to authenticated
  using (id = (select auth.uid())) with check (id = (select auth.uid()));
create policy members_select on public.shop_members for select to authenticated
  using (user_id = (select auth.uid()) or public.is_shop_member(shop_id));

-- products : lecture membres, écriture admins
create policy products_select on public.products for select to authenticated using (public.is_shop_member(shop_id));
create policy products_insert on public.products for insert to authenticated with check (public.is_shop_admin(shop_id));
create policy products_update on public.products for update to authenticated
  using (public.is_shop_admin(shop_id)) with check (public.is_shop_admin(shop_id));

-- stock_movements : saisie directe réservée aux admins (les ventes passent par trigger)
create policy stock_select on public.stock_movements for select to authenticated using (public.is_shop_member(shop_id));
create policy stock_insert on public.stock_movements for insert to authenticated
  with check (public.is_shop_admin(shop_id) and created_by = (select auth.uid()));

-- clients : tous les membres
create policy clients_select on public.clients for select to authenticated using (public.is_shop_member(shop_id));
create policy clients_insert on public.clients for insert to authenticated with check (public.is_shop_member(shop_id));
create policy clients_update on public.clients for update to authenticated
  using (public.is_shop_member(shop_id)) with check (public.is_shop_member(shop_id));

-- sales / sale_items : création par les membres, correction par les admins
create policy sales_select on public.sales for select to authenticated using (public.is_shop_member(shop_id));
create policy sales_insert on public.sales for insert to authenticated
  with check (public.is_shop_member(shop_id) and seller_id = (select auth.uid()));
create policy sales_update on public.sales for update to authenticated
  using (public.is_shop_admin(shop_id)) with check (public.is_shop_admin(shop_id));
create policy sale_items_select on public.sale_items for select to authenticated using (public.is_shop_member(shop_id));
create policy sale_items_insert on public.sale_items for insert to authenticated with check (public.is_shop_member(shop_id));
create policy sale_items_update on public.sale_items for update to authenticated
  using (public.is_shop_admin(shop_id)) with check (public.is_shop_admin(shop_id));

-- quotes / quote_items
create policy quotes_select on public.quotes for select to authenticated using (public.is_shop_member(shop_id));
create policy quotes_insert on public.quotes for insert to authenticated
  with check (public.is_shop_member(shop_id) and seller_id = (select auth.uid()));
create policy quotes_update on public.quotes for update to authenticated
  using (seller_id = (select auth.uid()) or public.is_shop_admin(shop_id))
  with check (public.is_shop_member(shop_id));
create policy quote_items_select on public.quote_items for select to authenticated using (public.is_shop_member(shop_id));
create policy quote_items_insert on public.quote_items for insert to authenticated with check (public.is_shop_member(shop_id));
create policy quote_items_update on public.quote_items for update to authenticated
  using (public.is_shop_member(shop_id)) with check (public.is_shop_member(shop_id));

-- audit_log : ajout seul
create policy audit_select on public.audit_log for select to authenticated using (public.is_shop_member(shop_id));
create policy audit_insert on public.audit_log for insert to authenticated
  with check (public.is_shop_member(shop_id) and user_id = (select auth.uid()));

-- Droits : rien pour anon, minimum pour authenticated, jamais de DELETE
revoke all on all tables in schema public from anon, authenticated;
alter default privileges for role postgres in schema public revoke all on tables from anon, authenticated;

grant select on all tables in schema public to authenticated;
grant update (name) on public.shops to authenticated;
grant update (username, lastname, telephone) on public.profiles to authenticated;
grant insert (id, shop_id, name, category, description, qr_code, price, initial_stock, low_stock_threshold)
  on public.products to authenticated;
grant update (name, category, description, qr_code, price, initial_stock, low_stock_threshold, deleted_at)
  on public.products to authenticated;
grant insert (id, shop_id, product_id, delta, reason, created_by) on public.stock_movements to authenticated;
grant insert, update on public.clients, public.sales, public.sale_items,
  public.quotes, public.quote_items to authenticated;
grant insert on public.audit_log to authenticated;
