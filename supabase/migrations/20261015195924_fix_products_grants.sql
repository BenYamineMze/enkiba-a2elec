revoke insert, update on public.products from authenticated;
grant insert on public.products to authenticated;
grant update (name, category, description, qr_code, price, initial_stock, low_stock_threshold, deleted_at)
  on public.products to authenticated;
