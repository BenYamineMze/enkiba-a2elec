-- Rétablit les droits d'écriture prévus par init_security pour le rôle authenticated.
-- Constat sur dev : seuls products et sale_returns acceptaient des insertions ;
-- clients, sales, sale_items, stock_movements, quotes, quote_items et audit_log
-- étaient en lecture seule, ce qui bloquait toute synchronisation des ventes.
-- Les policies RLS (inchangées) restent le vrai contrôle d'accès.

grant insert (id, shop_id, product_id, delta, reason, created_by) on public.stock_movements to authenticated;
grant insert, update on public.clients, public.sales, public.sale_items,
  public.quotes, public.quote_items to authenticated;
grant insert on public.audit_log to authenticated;
