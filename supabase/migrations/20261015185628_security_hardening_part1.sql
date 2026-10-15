-- 1. Vue qui n'expose que les noms (pour l'affichage de l'historique)
create or replace view public.user_names as
  select id, username, lastname from public.users;
grant select on public.user_names to anon, authenticated;

-- 2. handle_new_user : le rôle ne vient plus du client.
--    Seul app_metadata (modifiable côté serveur uniquement) peut donner un autre rôle que 'user'.
--    En cas de conflit, le rôle existant est conservé.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = ''
as $function$
begin
  insert into public.users (id, auth_id, email, role, username, lastname, telephone, active, updated_at)
  values (
    new.id, new.id, new.email,
    case when new.raw_app_meta_data->>'role' in ('admin','user')
         then new.raw_app_meta_data->>'role' else 'user' end,
    coalesce(new.raw_user_meta_data->>'username', ''),
    coalesce(new.raw_user_meta_data->>'lastname', ''),
    coalesce(new.raw_user_meta_data->>'telephone', ''),
    1, now()
  )
  on conflict (email) do update set
    id = excluded.id,
    auth_id = excluded.auth_id,
    username = excluded.username,
    lastname = excluded.lastname,
    telephone = excluded.telephone,
    active = 1,
    updated_at = now();
  return new;
end;
$function$;

-- 3. delete_product_by_id : search_path fixé (le contrôle de rôle est en partie 2)
alter function public.delete_product_by_id(text) set search_path = '';

-- 4. Droits inutiles retirés aux rôles de l'API
revoke truncate, references, trigger, maintain on all tables in schema public from anon, authenticated;
