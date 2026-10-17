-- Tests de sécurité et de règles métier du schéma v2 (à rejouer après chaque reset).
-- Exécution : supabase db query --linked -f supabase/tests/security_tests.sql
--             (ou --local). Vérifier AVANT le projet lié (supabase projects list).
-- Tout se passe dans un seul bloc annulé à la fin : aucune donnée ne reste.
-- Résultat attendu : une erreur « TESTS OK (n vérifications) ». Toute autre erreur
-- liste les vérifications en échec.
do $$
declare
  ok int := 0;
  failures text[] := '{}';
  v_admin_a uuid := gen_random_uuid();
  v_seller_a uuid := gen_random_uuid();
  v_admin_b uuid := gen_random_uuid();
  v_shop_a uuid := gen_random_uuid();
  v_shop_b uuid := gen_random_uuid();
  v_product uuid := gen_random_uuid();
  v_product2 uuid := gen_random_uuid();
  v_sale uuid := gen_random_uuid();
  v_item uuid := gen_random_uuid();
  v_quote uuid := gen_random_uuid();
  v_qsale uuid := gen_random_uuid();
  v_qty int;
  v_n int;

begin
  -- ---------------------------------------------------------------- préparation (propriétaire)
  insert into auth.users (id, email, aud, role, raw_user_meta_data, raw_app_meta_data, created_at, updated_at)
  values (v_admin_a, 'admin.a@test.local', 'authenticated', 'authenticated', '{"username":"Admin","lastname":"A"}', '{}', now(), now()),
         (v_seller_a, 'seller.a@test.local', 'authenticated', 'authenticated', '{"username":"Vendeur","lastname":"A"}', '{}', now(), now()),
         (v_admin_b, 'admin.b@test.local', 'authenticated', 'authenticated', '{}', '{}', now(), now());
  insert into public.shops (id, name) values (v_shop_a, 'Boutique A'), (v_shop_b, 'Boutique B');
  insert into public.shop_members (shop_id, user_id, role) values
    (v_shop_a, v_admin_a, 'admin'), (v_shop_a, v_seller_a, 'seller'), (v_shop_b, v_admin_b, 'admin');

  select count(*) into v_n from public.profiles where id in (v_admin_a, v_seller_a, v_admin_b);
  if v_n = 3 then ok := ok + 1; else failures := failures || 'profil créé par le trigger Auth'; end if;

  -- ---------------------------------------------------------------- anon
  execute 'set local role anon';
  begin
    perform count(*) from public.products;
    failures := failures || 'anon lit products';
  exception when insufficient_privilege then ok := ok + 1;
  end;
  execute 'reset role';

  -- ---------------------------------------------------------------- admin A : catalogue et stock
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin_a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  insert into public.products (id, shop_id, name, price, quantity) values (v_product, v_shop_a, 'Câble', 1500, 50);
  select quantity into v_qty from public.products where id = v_product;
  if v_qty = 0 then ok := ok + 1; else failures := failures || 'quantité forcée à 0 à la création'; end if;
  insert into public.stock_movements (shop_id, product_id, delta, reason, created_by)
    values (v_shop_a, v_product, 10, 'initial', v_admin_a);
  select quantity into v_qty from public.products where id = v_product;
  if v_qty = 10 then ok := ok + 1; else failures := failures || 'mouvement initial appliqué'; end if;
  execute 'reset role';

  -- ---------------------------------------------------------------- vendeur A
  perform set_config('request.jwt.claims', json_build_object('sub', v_seller_a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';

  select count(*) into v_n from public.products where shop_id = v_shop_a;
  if v_n = 1 then ok := ok + 1; else failures := failures || 'vendeur lit le catalogue de sa boutique'; end if;
  select count(*) into v_n from public.profiles where id = v_admin_b;
  if v_n = 0 then ok := ok + 1; else failures := failures || 'vendeur voit un profil d''une autre boutique'; end if;

  begin
    insert into public.products (id, shop_id, name, price) values (v_product2, v_shop_a, 'Interdit', 10);
    failures := failures || 'vendeur crée un produit';
  exception when insufficient_privilege then ok := ok + 1;
  end;

  update public.products set price = 1 where id = v_product;
  get diagnostics v_n = row_count;
  if v_n = 0 then ok := ok + 1; else failures := failures || 'vendeur modifie un prix'; end if;

  begin
    update public.products set quantity = 999 where id = v_product;
    failures := failures || 'quantité modifiable directement';
  exception when insufficient_privilege then ok := ok + 1;
  end;

  -- Vente : 3 unités
  insert into public.sales (id, shop_id, seller_id, total_price, payment_method)
    values (v_sale, v_shop_a, v_seller_a, 4500, 'espece');
  insert into public.sale_items (id, shop_id, sale_id, product_id, product_name, quantity, list_price, unit_price, total_price)
    values (v_item, v_shop_a, v_sale, v_product, 'Câble', 3, 1500, 1500, 4500);
  execute 'reset role';
  select quantity into v_qty from public.products where id = v_product;
  if v_qty = 7 then ok := ok + 1; else failures := failures || 'vente décrémente le stock (' || v_qty || ')'; end if;
  execute 'set local role authenticated';

  begin
    insert into public.sales (shop_id, seller_id, total_price, payment_method)
      values (v_shop_a, v_admin_a, 10, 'espece');
    failures := failures || 'vente au nom d''un autre vendeur';
  exception when insufficient_privilege then ok := ok + 1;
  end;

  -- Retours : 2 puis 2 de trop
  insert into public.sale_returns (shop_id, sale_id, sale_item_id, product_id, quantity, refund_amount, created_by)
    values (v_shop_a, v_sale, v_item, v_product, 2, 3000, v_seller_a);
  begin
    insert into public.sale_returns (shop_id, sale_id, sale_item_id, product_id, quantity, refund_amount, created_by)
      values (v_shop_a, v_sale, v_item, v_product, 2, 3000, v_seller_a);
    failures := failures || 'retour au-delà de la quantité vendue';
  exception when others then
    if sqlerrm = 'return_exceeds_sold' then ok := ok + 1; else failures := failures || ('retour excédentaire : ' || sqlerrm); end if;
  end;

  begin
    delete from public.sales where id = v_sale;
    failures := failures || 'suppression d''une vente';
  exception when insufficient_privilege then ok := ok + 1;
  end;

  begin
    perform public.set_updated_at();
    failures := failures || 'fonction de trigger appelable';
  exception when insufficient_privilege or feature_not_supported then ok := ok + 1;
  end;

  -- Profil : nom modifiable, état actif non
  update public.profiles set username = 'Ali' where id = v_seller_a;
  get diagnostics v_n = row_count;
  if v_n = 1 then ok := ok + 1; else failures := failures || 'vendeur modifie son prénom'; end if;
  begin
    update public.profiles set active = true where id = v_seller_a;
    failures := failures || 'vendeur modifie son état actif';
  exception when insufficient_privilege then ok := ok + 1;
  end;

  -- Devis converti une seule fois
  insert into public.quotes (id, shop_id, seller_id, total_price) values (v_quote, v_shop_a, v_seller_a, 1500);
  insert into public.sales (id, shop_id, seller_id, total_price, payment_method, source_quote_id)
    values (v_qsale, v_shop_a, v_seller_a, 1500, 'espece', v_quote);
  update public.quotes set status = 'converted', converted_sale_id = v_qsale where id = v_quote;
  ok := ok + 1;
  begin
    insert into public.sales (shop_id, seller_id, total_price, payment_method, source_quote_id)
      values (v_shop_a, v_seller_a, 1500, 'espece', v_quote);
    failures := failures || 'deuxième vente pour le même devis';
  exception when unique_violation then ok := ok + 1;
  end;
  begin
    update public.quotes set status = 'draft', converted_sale_id = null where id = v_quote;
    failures := failures || 'devis converti remis en brouillon';
  exception when others then
    if sqlerrm = 'quote_already_converted' then ok := ok + 1; else failures := failures || ('devis figé : ' || sqlerrm); end if;
  end;
  execute 'reset role';

  -- ---------------------------------------------------------------- admin A : annulation
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin_a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  update public.sales set deleted_at = now() where id = v_sale;
  execute 'reset role';
  select quantity into v_qty from public.products where id = v_product;
  -- 10 - 3 (vente) + 2 (retour) + 1 (annulation du reste) = 10
  if v_qty = 10 then ok := ok + 1; else failures := failures || 'annulation remet le non-retourné (' || v_qty || ')'; end if;

  -- ---------------------------------------------------------------- admin B : cloisonnement
  perform set_config('request.jwt.claims', json_build_object('sub', v_admin_b, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into v_n from public.sales where shop_id = v_shop_a;
  if v_n = 0 then ok := ok + 1; else failures := failures || 'admin B voit les ventes de A'; end if;
  begin
    insert into public.stock_movements (shop_id, product_id, delta, reason, created_by)
      values (v_shop_a, v_product, 5, 'restock', v_admin_b);
    failures := failures || 'admin B modifie le stock de A';
  exception when insufficient_privilege then ok := ok + 1;
  end;
  execute 'reset role';

  -- ---------------------------------------------------------------- vendeur désactivé
  update public.profiles set active = false where id = v_seller_a;
  perform set_config('request.jwt.claims', json_build_object('sub', v_seller_a, 'role', 'authenticated')::text, true);
  execute 'set local role authenticated';
  select count(*) into v_n from public.products;
  if v_n = 0 then ok := ok + 1; else failures := failures || 'vendeur désactivé lit encore'; end if;
  execute 'reset role';

  -- ---------------------------------------------------------------- service_role (Edge Functions)
  execute 'set local role service_role';
  begin
    insert into public.shop_members (shop_id, user_id, role) values (v_shop_b, v_seller_a, 'seller');
    ok := ok + 1;
  exception when others then failures := failures || ('service_role ajoute un membre : ' || sqlerrm);
  end;
  execute 'reset role';

  if cardinality(failures) > 0 then
    raise exception 'ÉCHECS (% ok) : %', ok, array_to_string(failures, ' | ');
  end if;
  raise exception 'TESTS OK (% vérifications)', ok;
end;
$$;
