-- NE PAS APPLIQUER avant vérification du code (en-tête Authorization de Retrofit).

create or replace function public.is_admin()
returns boolean language sql stable security definer set search_path = ''
as $$ select exists (select 1 from public.users where auth_id = auth.uid() and role = 'admin') $$;

-- users : fermer la lecture/écriture publique
drop policy "Enable ALL access for all roles" on public.users;
create policy users_select on public.users for select to authenticated
  using (auth_id = auth.uid() or public.is_admin());
create policy users_admin_write on public.users for all to authenticated
  using (public.is_admin()) with check (public.is_admin());

-- Policies « testing » redondantes
drop policy "Allow ALL writes for history testing" on public.history;
drop policy "Allow anon and authenticated to insert history" on public.history;
drop policy "Allow ALL writes for sales sync testing" on public.sales;
drop policy "Allow anon and authenticated to insert sales" on public.sales;
drop policy "Allow ALL writes for sale_items sync testing" on public.sale_items;
drop policy "Allow anon and authenticated to insert sale_items" on public.sale_items;
drop policy "Allow ALL writes for sale_items_archive sync testing" on public.sale_items_archive;
drop policy "Allow anon insert on sale_items_archive" on public.sale_items_archive;
drop policy "Allow anon insert on sale_archive" on public.sale_archive;

-- Suppression de produit réservée aux admins
create or replace function public.delete_product_by_id(product_id_to_delete text)
returns void language plpgsql security definer set search_path = ''
as $function$
begin
  if not public.is_admin() then raise exception 'forbidden'; end if;
  delete from public.sale_items where product_id::text = product_id_to_delete;
  delete from public.history    where product_id::text = product_id_to_delete;
  delete from public.products   where id::text = product_id_to_delete;
end;
$function$;
revoke execute on function public.delete_product_by_id(text) from public, anon;
grant  execute on function public.delete_product_by_id(text) to authenticated;
