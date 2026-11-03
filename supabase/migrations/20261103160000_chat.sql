-- Discussion de la boutique : un canal commun à tous les membres.
-- Messages gardés 90 jours (purge chaque nuit par pg_cron).

create table public.chat_messages (
  id uuid primary key default gen_random_uuid(),
  shop_id uuid not null references public.shops (id),
  sender_id uuid not null references public.profiles (id),
  body text not null check (char_length(body) between 1 and 2000),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  deleted_at timestamptz
);
create index chat_messages_pull_idx on public.chat_messages (shop_id, updated_at);
create index chat_messages_created_idx on public.chat_messages (created_at);

create trigger trg_set_updated_at before insert or update on public.chat_messages
  for each row execute function public.set_updated_at();

alter table public.chat_messages enable row level security;

create policy chat_select on public.chat_messages for select to authenticated
  using (public.is_shop_member(shop_id));
create policy chat_insert on public.chat_messages for insert to authenticated
  with check (public.is_shop_member(shop_id) and sender_id = (select auth.uid()) and deleted_at is null);
-- Retirer un message (deleted_at) : son auteur, ou un admin de la boutique
create policy chat_update on public.chat_messages for update to authenticated
  using (public.is_shop_member(shop_id) and (sender_id = (select auth.uid()) or public.is_shop_admin(shop_id)))
  with check (public.is_shop_member(shop_id) and (sender_id = (select auth.uid()) or public.is_shop_admin(shop_id)));

grant select on public.chat_messages to authenticated;
grant insert (id, shop_id, sender_id, body) on public.chat_messages to authenticated;
grant update (deleted_at) on public.chat_messages to authenticated;
grant all on public.chat_messages to service_role;

-- Purge : messages de plus de 90 jours, chaque nuit à 3 h 30 (UTC)
create extension if not exists pg_cron;
select cron.schedule(
  'enkiba-chat-retention',
  '30 3 * * *',
  $$delete from public.chat_messages where created_at < now() - interval '90 days'$$
);
