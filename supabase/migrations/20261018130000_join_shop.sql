-- Le super-administrateur peut se rattacher à une boutique comme admin, pour y travailler
-- (caisse, articles) après avoir changé de boutique dans l'app. Sans ce rattachement, il ne
-- voit que les indicateurs (shop_overview), jamais le détail des ventes.

create or replace function public.join_shop_as_admin(p_shop uuid)
returns void language plpgsql security definer set search_path = '' as $$
begin
  if not public.is_super_admin() then
    raise exception 'forbidden' using errcode = '42501';
  end if;
  if not exists (select 1 from public.shops where id = p_shop and deleted_at is null) then
    raise exception 'shop_not_found' using errcode = 'P0001';
  end if;
  insert into public.shop_members (shop_id, user_id, role)
  values (p_shop, (select auth.uid()), 'admin')
  on conflict (shop_id, user_id) do update set role = 'admin';
end;
$$;

revoke execute on function public.join_shop_as_admin(uuid) from public, anon;
grant execute on function public.join_shop_as_admin(uuid) to authenticated, service_role;
