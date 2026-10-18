-- Donne le rôle de super-administrateur à un compte existant (jamais depuis l'app).
-- 1. Modifier l'e-mail ci-dessous.
-- 2. Vérifier le projet lié (supabase projects list), puis :
--    supabase db query --linked -f supabase/scripts/grant_super_admin.sql
-- Retirer le rôle : delete from public.platform_admins where user_id = (…);
do $$
declare
  v_email text := 'admin@exemple.com';   -- À MODIFIER
  v_user uuid;
begin
  if v_email = 'admin@exemple.com' then raise exception 'Renseigner v_email avant d''exécuter le script'; end if;
  select id into v_user from auth.users where lower(email) = lower(v_email);
  if v_user is null then raise exception 'Compte introuvable : %', v_email; end if;
  insert into public.platform_admins (user_id) values (v_user) on conflict do nothing;
end;
$$;

select p.email as super_admin from public.platform_admins a join public.profiles p on p.id = a.user_id;
