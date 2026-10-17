-- Coordonnées de la boutique, imprimées sur les factures et les devis.
-- L'app est multi-boutique : ces informations ne peuvent plus être écrites dans le code.
-- Modifiables par l'admin de la boutique seulement (policy shops_update existante).

alter table public.shops
  add column address text not null default '',
  add column phone text not null default '',
  add column email text not null default '';

grant update (name, address, phone, email) on public.shops to authenticated;
