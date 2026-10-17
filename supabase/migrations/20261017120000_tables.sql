-- Enkiba-A2elec — schéma v2, partie 1/3 : tables et index.
-- Regroupe les migrations du 15 et du 16/10/2026 (init_tables, sale_returns,
-- quote_conversion). À appliquer sur une base neuve (supabase db reset).
-- Partie 2 : logique (triggers) ; partie 3 : sécurité (RLS, policies, droits).

-- ---------------------------------------------------------------------------
-- Boutiques, profils, appartenance
-- ---------------------------------------------------------------------------
create table public.shops (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);

-- Un profil par compte Auth (créé par trigger, voir partie 2)
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  username text not null default '',
  lastname text not null default '',
  telephone text not null default '',
  email text,
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

-- Rôle dans la boutique : admin ou vendeur
create table public.shop_members (
  shop_id uuid not null references public.shops (id) on delete cascade,
  user_id uuid not null references public.profiles (id) on delete cascade,
  role text not null check (role in ('admin', 'seller')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (shop_id, user_id)
);

-- ---------------------------------------------------------------------------
-- Catalogue et stock
-- ---------------------------------------------------------------------------
create table public.products (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  name text not null,
  category text not null default '',
  description text,
  qr_code text,
  price numeric(12,2) not null check (price >= 0),
  quantity integer not null default 0,          -- maintenu par trigger (stock_movements)
  initial_stock integer not null default 0,     -- stock de référence (barres de progression)
  low_stock_threshold integer not null default 5,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, shop_id)
);
create unique index products_qr_uniq on public.products (shop_id, qr_code)
  where qr_code is not null and deleted_at is null;

-- Toute variation de stock est un mouvement (la quantité du produit en découle)
create table public.stock_movements (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null,
  product_id uuid not null,
  delta integer not null check (delta <> 0),
  reason text not null check (reason in ('initial', 'restock', 'adjustment', 'sale', 'sale_cancel')),
  ref_id uuid,
  created_by uuid references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);
-- Un seul mouvement par ligne de vente, retour ou annulation (envoi idempotent)
create unique index stock_movements_ref_uniq on public.stock_movements (reason, ref_id)
  where ref_id is not null;

-- ---------------------------------------------------------------------------
-- Clients
-- ---------------------------------------------------------------------------
create table public.clients (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  firstname text,
  lastname text,
  address text,
  phone text,
  email text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, shop_id)
);

-- ---------------------------------------------------------------------------
-- Ventes
-- ---------------------------------------------------------------------------
create table public.sales (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  seller_id uuid not null references public.profiles (id),
  client_id uuid,
  total_price numeric(12,2) not null check (total_price >= 0),
  payment_method text not null check (payment_method in ('espece', 'virement', 'mobile_money', 'cheque', 'carte')),
  has_negotiation boolean not null default false,
  sale_date timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  -- Devis d'origine. Pas de clé étrangère : la vente est envoyée avant la mise à jour du devis
  source_quote_id uuid,
  unique (id, shop_id),
  foreign key (client_id, shop_id) references public.clients (id, shop_id)
);
-- Un devis ne donne qu'une vente (deux téléphones qui convertissent hors ligne :
-- la deuxième vente est refusée avant toute ligne, donc sans mouvement de stock)
create unique index sales_source_quote_uniq on public.sales (source_quote_id)
  where source_quote_id is not null;

create table public.sale_items (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null,
  sale_id uuid not null,
  product_id uuid not null,
  product_name text not null,                    -- copie au moment de la vente
  quantity integer not null check (quantity > 0),
  list_price numeric(12,2) not null check (list_price >= 0),   -- prix catalogue
  unit_price numeric(12,2) not null check (unit_price >= 0),   -- prix négocié
  total_price numeric(12,2) not null check (total_price >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (sale_id, shop_id) references public.sales (id, shop_id),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);

-- Retours : une ligne par retour, la vente d'origine reste intacte
create table public.sale_returns (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  sale_id uuid not null,
  sale_item_id uuid not null references public.sale_items (id),
  product_id uuid not null,
  quantity integer not null check (quantity > 0),
  refund_amount numeric(12,2) not null check (refund_amount >= 0),
  created_by uuid not null references public.profiles (id),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (sale_id, shop_id) references public.sales (id, shop_id),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);

-- ---------------------------------------------------------------------------
-- Devis
-- ---------------------------------------------------------------------------
create table public.quotes (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  seller_id uuid not null references public.profiles (id),
  client_id uuid,
  status text not null default 'draft' check (status in ('draft', 'sent', 'accepted', 'rejected', 'converted', 'expired')),
  total_price numeric(12,2) not null default 0 check (total_price >= 0),
  valid_until date,
  notes text,
  converted_sale_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz,
  unique (id, shop_id),
  foreign key (client_id, shop_id) references public.clients (id, shop_id),
  foreign key (converted_sale_id, shop_id) references public.sales (id, shop_id)
);

create table public.quote_items (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null,
  quote_id uuid not null,
  product_id uuid not null,
  product_name text not null,
  quantity integer not null check (quantity > 0),
  list_price numeric(12,2) not null check (list_price >= 0),
  unit_price numeric(12,2) not null check (unit_price >= 0),
  total_price numeric(12,2) not null check (total_price >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  foreign key (quote_id, shop_id) references public.quotes (id, shop_id),
  foreign key (product_id, shop_id) references public.products (id, shop_id)
);

-- ---------------------------------------------------------------------------
-- Journal d'audit (ajout seul)
-- ---------------------------------------------------------------------------
create table public.audit_log (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  user_id uuid references public.profiles (id),
  entity text not null,
  entity_id uuid,
  operation text not null,
  old_value jsonb,
  new_value jsonb,
  created_at timestamptz not null default now()
);

-- ---------------------------------------------------------------------------
-- Index : pull incrémental par boutique (shop_id, updated_at) et clés étrangères
-- ---------------------------------------------------------------------------
create index idx_shop_members_user on public.shop_members (user_id);
create index idx_products_sync on public.products (shop_id, updated_at);
create index idx_stock_mov_sync on public.stock_movements (shop_id, updated_at);
create index idx_stock_mov_product on public.stock_movements (product_id);
create index idx_clients_sync on public.clients (shop_id, updated_at);
create index idx_sales_sync on public.sales (shop_id, updated_at);
create index idx_sales_date on public.sales (shop_id, sale_date);
create index idx_sales_seller on public.sales (seller_id);
create index idx_sales_client on public.sales (client_id);
create index idx_sale_items_sync on public.sale_items (shop_id, updated_at);
create index idx_sale_items_sale on public.sale_items (sale_id);
create index idx_sale_items_product on public.sale_items (product_id);
create index idx_sale_returns_sync on public.sale_returns (shop_id, updated_at);
create index idx_sale_returns_item on public.sale_returns (sale_item_id);
create index idx_quotes_sync on public.quotes (shop_id, updated_at);
create index idx_quote_items_sync on public.quote_items (shop_id, updated_at);
create index idx_quote_items_quote on public.quote_items (quote_id);
create index idx_audit_shop on public.audit_log (shop_id, created_at);
