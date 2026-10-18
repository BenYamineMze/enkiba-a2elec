import { createClient } from "npm:@supabase/supabase-js@2";

const cors = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, content-type, apikey",
};
const json = (body: unknown, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const anon = Deno.env.get("SUPABASE_ANON_KEY")!;
  const service = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const authHeader = req.headers.get("Authorization") ?? "";

  // 1. Identifier l'appelant à partir de son JWT
  const caller = createClient(url, anon, { global: { headers: { Authorization: authHeader } } });
  const { data: u, error: uErr } = await caller.auth.getUser();
  if (uErr || !u.user) return json({ error: "unauthorized" }, 401);

  let body: Record<string, string>;
  try { body = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const { shop_id, email, password, username, lastname, telephone } = body;
  if (!shop_id || !email || !password || password.length < 8) return json({ error: "invalid_input" }, 400);

  const admin = createClient(url, service);

  // 2. L'appelant doit être admin actif de cette boutique
  const { data: m, error: mErr0 } = await admin.from("shop_members").select("role")
    .eq("shop_id", shop_id).eq("user_id", u.user.id).eq("role", "admin").maybeSingle();
  const { data: pr } = await admin.from("profiles").select("active").eq("id", u.user.id).maybeSingle();
  if (mErr0 || !m || !pr?.active) return json({ error: "forbidden", detail: mErr0?.message }, 403);

  // Boutique active : une boutique suspendue ou supprimée ne gère plus son équipe
  const { data: shop } = await admin.from("shops").select("suspended_at, deleted_at").eq("id", shop_id).maybeSingle();
  if (!shop || shop.suspended_at || shop.deleted_at) return json({ error: "shop_suspended" }, 403);

  // 3. Créer le compte (le rôle ne vient jamais des métadonnées)
  const { data: created, error: cErr } = await admin.auth.admin.createUser({
    email, password, email_confirm: true,
    user_metadata: { username: username ?? "", lastname: lastname ?? "", telephone: telephone ?? "" },
  });
  if (cErr || !created.user) return json({ error: "create_failed", detail: cErr?.message }, 400);

  // 4. Rattacher à la boutique comme vendeur
  const { error: mErr } = await admin.from("shop_members")
    .insert({ shop_id, user_id: created.user.id, role: "seller" });
  if (mErr) {
    await admin.auth.admin.deleteUser(created.user.id);
    return json({ error: "membership_failed" }, 500);
  }
  return json({ id: created.user.id, email });
});
