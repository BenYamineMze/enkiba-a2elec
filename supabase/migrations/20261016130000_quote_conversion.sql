-- Conversion d'un devis en vente : une seule fois.
--
-- La vente issue d'un devis porte source_quote_id. L'index unique refuse une deuxième
-- vente pour le même devis (deux téléphones qui convertissent hors ligne) : la deuxième
-- vente est rejetée à l'insertion, avant toute ligne de vente, donc sans mouvement de stock.
-- Pas de clé étrangère vers quotes : la vente est envoyée avant la mise à jour du devis
-- (qui, lui, référence la vente via converted_sale_id).

alter table public.sales add column source_quote_id uuid;

create unique index sales_source_quote_uniq on public.sales (source_quote_id)
  where source_quote_id is not null;

-- Un devis converti est définitif, et ne peut l'être que vers la vente qui le cite.
create or replace function public.check_quote_conversion()
returns trigger language plpgsql security definer set search_path = '' as $$
begin
  if tg_op = 'UPDATE' and old.status = 'converted' then
    if new.status is distinct from 'converted'
       or new.converted_sale_id is distinct from old.converted_sale_id then
      raise exception 'quote_already_converted' using errcode = 'P0001';
    end if;
    return new;
  end if;

  if new.status = 'converted' then
    if new.converted_sale_id is null or not exists (
      select 1 from public.sales s
      where s.id = new.converted_sale_id and s.shop_id = new.shop_id
        and s.source_quote_id = new.id and s.deleted_at is null
    ) then
      raise exception 'invalid_quote_conversion' using errcode = 'P0001';
    end if;
  elsif new.converted_sale_id is not null then
    raise exception 'invalid_quote_conversion' using errcode = 'P0001';
  end if;
  return new;
end;
$$;

create trigger trg_check_quote_conversion
  before insert or update on public.quotes
  for each row execute function public.check_quote_conversion();

revoke execute on function public.check_quote_conversion() from public, anon, authenticated;
