-- Super-administrateur de la plateforme et suspension des boutiques.
--
-- - platform_admins : comptes super-admin, attribués uniquement par SQL (aucun droit
--   d'écriture pour l'API). Le super-admin voit toutes les boutiques, modifie leurs
--   coordonnées, les suspend ou les réactive. Il ne voit pas le détail des ventes des
--   autres boutiques : seulement des indicateurs, via shop_overview().
-- - shops.suspended_at : une boutique suspendue n'est plus accessible à ses membres
--   (is_shop_member / is_shop_admin renvoient faux), ses données restent intactes.

-- ---------------------------------------------------------------------------
-- Table des super-admins
-- ---------------------------------------------------------------------------
create table public.platform_admins (
  user_id uuid primary key references public.profiles (id) on delete cascade,
  created_at timestamptz not null default now()
);
alter table public.platform_admins enable row level security;
-- Chacun peut seulement savoir s'il est lui-même super-admin (affichage de l'écran Boutiques)
create policy platform_admins_self on public.platform_admins for select to authenticated
  using (user_id = (select auth.uid()));
revoke all on public.platform_admins from anon, authenticated;
grant select on public.platform_admins to authenticated;
grant all on public.platform_admins to service_role;

create or replace function public.is_super_admin()
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.platform_admins a join public.profiles p on p.id = a.user_id
    where a.user_id = (select auth.uid()) and p.active
  );
$$;
revoke execute on function public.is_super_admin() from public, anon;
grant execute on function public.is_super_admin() to authenticated, service_role;

-- ---------------------------------------------------------------------------
-- Suspension d'une boutique
-- ---------------------------------------------------------------------------
alter table public.shops add column suspended_at timestamptz;

-- Appartenance : la boutique doit être active (ni supprimée ni suspendue)
create or replace function public.is_shop_member(p_shop uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.shop_members m
      join public.profiles p on p.id = m.user_id
      join public.shops s on s.id = m.shop_id
    where m.shop_id = p_shop and m.user_id = (select auth.uid()) and p.active
      and s.deleted_at is null and s.suspended_at is null
  );
$$;

create or replace function public.is_shop_admin(p_shop uuid)
returns boolean language sql stable security definer set search_path = '' as $$
  select exists (
    select 1 from public.shop_members m
      join public.profiles p on p.id = m.user_id
      join public.shops s on s.id = m.shop_id
    where m.shop_id = p_shop and m.user_id = (select auth.uid()) and m.role = 'admin' and p.active
      and s.deleted_at is null and s.suspended_at is null
  );
$$;

-- Seul un super-admin suspend ou réactive (l'admin d'une boutique ne peut pas se réactiver)
create or replace function public.check_shop_update()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if new.suspended_at is distinct from old.suspended_at and not public.is_super_admin()
     and coalesce(current_setting('request.jwt.claims', true)::jsonb ->> 'role', '') = 'authenticated' then
    raise exception 'suspension_reserved_to_super_admin' using errcode = 'P0001';
  end if;
  return new;
end;
$$;
create trigger trg_check_shop_update
  before update on public.shops
  for each row execute function public.check_shop_update();
revoke execute on function public.check_shop_update() from public, anon, authenticated;

-- ---------------------------------------------------------------------------
-- Droits du super-admin sur les boutiques (lecture et modification, pas de suppression)
-- ---------------------------------------------------------------------------
create policy shops_select_super on public.shops for select to authenticated
  using (public.is_super_admin());
create policy shops_update_super on public.shops for update to authenticated
  using (public.is_super_admin()) with check (public.is_super_admin());
grant update (name, address, phone, email, suspended_at) on public.shops to authenticated;

-- ---------------------------------------------------------------------------
-- Vues d'ensemble pour le super-admin (indicateurs, jamais le détail des ventes)
-- ---------------------------------------------------------------------------
create or replace function public.shop_overview()
returns table (
  shop_id uuid, name text, address text, phone text, email text,
  suspended_at timestamptz, created_at timestamptz,
  members integer, admins integer, products integer,
  sales_month integer, revenue_month numeric, last_sale_at timestamptz
) language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_super_admin() then
    raise exception 'forbidden' using errcode = '42501';
  end if;
  return query
  select s.id, s.name, s.address, s.phone, s.email, s.suspended_at, s.created_at,
    (select count(*)::int from public.shop_members m where m.shop_id = s.id),
    (select count(*)::int from public.shop_members m where m.shop_id = s.id and m.role = 'admin'),
    (select count(*)::int from public.products p where p.shop_id = s.id and p.deleted_at is null),
    (select count(*)::int from public.sales v where v.shop_id = s.id and v.deleted_at is null
       and v.sale_date >= date_trunc('month', now())),
    (select coalesce(sum(v.total_price - coalesce((select sum(r.refund_amount) from public.sale_returns r where r.sale_id = v.id), 0)), 0)
       from public.sales v where v.shop_id = s.id and v.deleted_at is null and v.sale_date >= date_trunc('month', now())),
    (select max(v.sale_date) from public.sales v where v.shop_id = s.id and v.deleted_at is null)
  from public.shops s
  where s.deleted_at is null
  order by s.name;
end;
$$;

create or replace function public.shop_members_overview(p_shop uuid)
returns table (user_id uuid, username text, lastname text, email text, telephone text, role text, active boolean)
language plpgsql stable security definer set search_path = '' as $$
begin
  if not public.is_super_admin() then
    raise exception 'forbidden' using errcode = '42501';
  end if;
  return query
  select p.id, p.username, p.lastname, p.email, p.telephone, m.role, p.active
  from public.shop_members m join public.profiles p on p.id = m.user_id
  where m.shop_id = p_shop
  order by (m.role = 'admin') desc, p.username;
end;
$$;

revoke execute on function public.shop_overview(), public.shop_members_overview(uuid) from public, anon;
grant execute on function public.shop_overview(), public.shop_members_overview(uuid) to authenticated, service_role;
