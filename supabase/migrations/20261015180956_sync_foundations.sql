create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;

create trigger trg_set_updated_at before insert or update on public.products   for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.clients    for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.history    for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.sales      for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.sale_items for each row execute function public.set_updated_at();
create trigger trg_set_updated_at before insert or update on public.users      for each row execute function public.set_updated_at();

create index if not exists idx_products_updated_at   on public.products   (updated_at);
create index if not exists idx_clients_updated_at    on public.clients    (updated_at);
create index if not exists idx_history_updated_at    on public.history    (updated_at);
create index if not exists idx_history_product_id    on public.history    (product_id);
create index if not exists idx_history_user_id       on public.history    (user_id);
create index if not exists idx_sales_updated_at      on public.sales      (updated_at);
create index if not exists idx_sales_user_id         on public.sales      (user_id);
create index if not exists idx_sales_client_id       on public.sales      (client_id);
create index if not exists idx_sales_sale_date       on public.sales      (sale_date);
create index if not exists idx_sale_items_updated_at on public.sale_items (updated_at);
create index if not exists idx_sale_items_sale_id    on public.sale_items  (sale_id);
create index if not exists idx_sale_items_product_id on public.sale_items  (product_id);
