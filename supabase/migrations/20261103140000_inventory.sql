-- Inventaire : les membres comptent, un admin valide.
-- Une seule session ouverte par boutique. Chaque comptage garde le stock attendu au moment
-- du comptage (vu par le téléphone) : l'écart = compté - attendu, appliqué à la validation.
-- Ainsi une vente faite après le comptage d'un article reste bien déduite.

alter table public.stock_movements drop constraint stock_movements_reason_check;
alter table public.stock_movements add constraint stock_movements_reason_check
  check (reason in ('initial', 'restock', 'adjustment', 'sale', 'sale_cancel', 'inventory'));

create table public.inventory_sessions (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  status text not null default 'open' check (status in ('open', 'validated', 'cancelled')),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  closed_by uuid references public.profiles (id),
  closed_at timestamptz,
  updated_at timestamptz not null default now(),
  unique (id, shop_id)
);
create unique index inventory_sessions_one_open on public.inventory_sessions (shop_id) where status = 'open';
create index inventory_sessions_pull_idx on public.inventory_sessions (shop_id, updated_at);

create table public.inventory_counts (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null,
  session_id uuid not null,
  product_id uuid not null,
  counted integer not null check (counted >= 0),
  expected integer not null,
  counted_by uuid not null references public.profiles (id),
  counted_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (session_id, product_id),
  foreign key (session_id, shop_id) references public.inventory_sessions (id, shop_id),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);
create index inventory_counts_pull_idx on public.inventory_counts (shop_id, updated_at);

create trigger trg_set_updated_at before insert or update on public.inventory_sessions
  for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.inventory_counts
  for each row execute function public.set_updated_at();

-- Un comptage n'est accepté que dans une session ouverte
create or replace function public.check_inventory_count()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if not exists (select 1 from public.inventory_sessions s
                 where s.id = new.session_id and s.shop_id = new.shop_id and s.status = 'open') then
    raise exception 'Inventaire clos ou introuvable' using errcode = 'P0001';
  end if;
  return new;
end;
$$;
create trigger trg_check_inventory_count before insert or update on public.inventory_counts
  for each row execute function public.check_inventory_count();

-- ---------------------------------------------------------------------------
-- Sécurité
-- ---------------------------------------------------------------------------
alter table public.inventory_sessions enable row level security;
alter table public.inventory_counts enable row level security;

create policy inventory_sessions_select on public.inventory_sessions for select to authenticated
  using (public.is_shop_member(shop_id));
-- Ouvrir un inventaire : admin ; le clore passe par les fonctions ci-dessous
create policy inventory_sessions_insert on public.inventory_sessions for insert to authenticated
  with check (public.is_shop_admin(shop_id) and created_by = (select auth.uid()) and status = 'open');

create policy inventory_counts_select on public.inventory_counts for select to authenticated
  using (public.is_shop_member(shop_id));
create policy inventory_counts_insert on public.inventory_counts for insert to authenticated
  with check (public.is_shop_member(shop_id) and counted_by = (select auth.uid()));
create policy inventory_counts_update on public.inventory_counts for update to authenticated
  using (public.is_shop_member(shop_id))
  with check (public.is_shop_member(shop_id) and counted_by = (select auth.uid()));

grant select on public.inventory_sessions, public.inventory_counts to authenticated;
grant insert (id, shop_id, created_by) on public.inventory_sessions to authenticated;
grant insert (id, shop_id, session_id, product_id, counted, expected, counted_by) on public.inventory_counts to authenticated;
-- Recompter un article : dernière saisie retenue
grant update (counted, expected, counted_by, counted_at) on public.inventory_counts to authenticated;
grant all on public.inventory_sessions, public.inventory_counts to service_role;

-- ---------------------------------------------------------------------------
-- Valider (admin) : un mouvement « inventory » par écart, puis session close.
-- Rejouable sans double effet (index unique reason + ref_id = id du comptage).
-- ---------------------------------------------------------------------------
create or replace function public.validate_inventory(p_session uuid)
returns integer language plpgsql security definer set search_path = '' as $$
declare
  v_shop uuid;
  v_n integer;
begin
  select shop_id into v_shop from public.inventory_sessions where id = p_session and status = 'open' for update;
  if v_shop is null then raise exception 'Inventaire clos ou introuvable' using errcode = 'P0001'; end if;
  if not public.is_shop_admin(v_shop) then raise exception 'Réservé aux admins' using errcode = '42501'; end if;

  insert into public.stock_movements (shop_id, product_id, delta, reason, ref_id, created_by)
  select c.shop_id, c.product_id, c.counted - c.expected, 'inventory', c.id, (select auth.uid())
  from public.inventory_counts c
  where c.session_id = p_session and c.counted <> c.expected
  on conflict (reason, ref_id) where ref_id is not null do nothing;
  get diagnostics v_n = row_count;

  update public.inventory_sessions
     set status = 'validated', closed_by = (select auth.uid()), closed_at = now()
   where id = p_session;
  return v_n;
end;
$$;

create or replace function public.cancel_inventory(p_session uuid)
returns void language plpgsql security definer set search_path = '' as $$
declare v_shop uuid;
begin
  select shop_id into v_shop from public.inventory_sessions where id = p_session and status = 'open' for update;
  if v_shop is null then raise exception 'Inventaire clos ou introuvable' using errcode = 'P0001'; end if;
  if not public.is_shop_admin(v_shop) then raise exception 'Réservé aux admins' using errcode = '42501'; end if;
  update public.inventory_sessions set status = 'cancelled', closed_by = (select auth.uid()), closed_at = now()
   where id = p_session;
end;
$$;

revoke execute on function public.check_inventory_count(), public.validate_inventory(uuid), public.cancel_inventory(uuid)
  from public, anon;
grant execute on function public.validate_inventory(uuid), public.cancel_inventory(uuid) to authenticated;
revoke execute on function public.check_inventory_count() from authenticated;
