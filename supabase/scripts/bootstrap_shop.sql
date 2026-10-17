-- Crée une boutique et son administrateur, après une base neuve (supabase db reset).
--
-- 1. Créer le compte dans le tableau de bord Supabase : Authentication → Users →
--    Add user (e-mail + mot de passe choisi par l'admin, « Auto Confirm User » coché).
--    Le trigger on_auth_user_created crée son profil.
-- 2. Modifier les deux valeurs ci-dessous.
-- 3. Vérifier le projet lié (supabase projects list), puis :
--    supabase db query --linked -f supabase/scripts/bootstrap_shop.sql
-- Les vendeurs se créent ensuite depuis l'app (écran Équipe).
do $$
declare
  v_email text := 'admin@exemple.com';   -- À MODIFIER : e-mail du compte créé à l'étape 1
  v_shop_name text := 'A2elec';          -- À MODIFIER : nom de la boutique
  v_user uuid;
  v_shop uuid;
begin
  if v_email = 'admin@exemple.com' then
    raise exception 'Renseigner v_email avant d''exécuter le script';
  end if;
  select id into v_user from auth.users where lower(email) = lower(v_email);
  if v_user is null then
    raise exception 'Compte introuvable : % (le créer d''abord dans Authentication → Users)', v_email;
  end if;
  if exists (select 1 from public.shop_members where user_id = v_user) then
    raise exception '% est déjà membre d''une boutique', v_email;
  end if;
  -- Profil normalement créé par le trigger ; filet de sécurité si le compte est antérieur au schéma
  insert into public.profiles (id, email) values (v_user, v_email) on conflict (id) do nothing;
  insert into public.shops (name) values (v_shop_name) returning id into v_shop;
  insert into public.shop_members (shop_id, user_id, role) values (v_shop, v_user, 'admin');
end;
$$;

select s.name as boutique, p.email as admin, m.role
from public.shop_members m
join public.shops s on s.id = m.shop_id
join public.profiles p on p.id = m.user_id
where m.role = 'admin';
