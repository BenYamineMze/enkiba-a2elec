-- Rétablit les droits de modification prévus par init_security (absents sur dev,
-- comme ceux corrigés par fix_authenticated_write_grants) :
-- - profiles : chacun modifie son prénom, son nom et son téléphone (écran « Mon profil »).
--   La policy profiles_update limite la modification à sa propre ligne ; ni le rôle,
--   ni l'état actif, ni l'e-mail ne sont modifiables par l'utilisateur.
-- - shops : l'admin renomme sa boutique (policy shops_update).

grant update (username, lastname, telephone) on public.profiles to authenticated;
grant update (name) on public.shops to authenticated;
