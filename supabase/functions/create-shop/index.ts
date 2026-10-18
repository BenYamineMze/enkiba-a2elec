import { createClient } from "npm:@supabase/supabase-js@2";

// Création d'une boutique et de son administrateur, réservée au super-administrateur.
// Corps : { name, address?, phone?, email?, admin_email, admin_password, admin_username?, admin_lastname?, admin_telephone? }

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
  const name = (body.name ?? "").trim();
  const adminEmail = (body.admin_email ?? "").trim();
  const adminPassword = body.admin_password ?? "";
  if (!name || name.length > 60 || !adminEmail || adminPassword.length < 8) return json({ error: "invalid_input" }, 400);

  const admin = createClient(url, service);

  // 2. L'appelant doit être super-administrateur actif
  const { data: sa } = await admin.from("platform_admins").select("user_id").eq("user_id", u.user.id).maybeSingle();
  const { data: pr } = await admin.from("profiles").select("active").eq("id", u.user.id).maybeSingle();
  if (!sa || !pr?.active) return json({ error: "forbidden" }, 403);

  // 3. Créer la boutique
  const { data: shop, error: sErr } = await admin.from("shops").insert({
    name,
    address: (body.address ?? "").trim(),
    phone: (body.phone ?? "").trim(),
    email: (body.email ?? "").trim(),
  }).select("id").single();
  if (sErr || !shop) return json({ error: "shop_failed", detail: sErr?.message }, 500);

  // 4. Créer le compte de l'administrateur (le rôle ne vient jamais des métadonnées)
  const { data: created, error: cErr } = await admin.auth.admin.createUser({
    email: adminEmail, password: adminPassword, email_confirm: true,
    user_metadata: {
      username: body.admin_username ?? "", lastname: body.admin_lastname ?? "", telephone: body.admin_telephone ?? "",
    },
  });
  if (cErr || !created.user) {
    await admin.from("shops").delete().eq("id", shop.id);
    return json({ error: "create_failed", detail: cErr?.message }, 400);
  }

  // 5. Rattacher l'administrateur à la boutique
  const { error: mErr } = await admin.from("shop_members").insert({ shop_id: shop.id, user_id: created.user.id, role: "admin" });
  if (mErr) {
    await admin.auth.admin.deleteUser(created.user.id);
    await admin.from("shops").delete().eq("id", shop.id);
    return json({ error: "membership_failed" }, 500);
  }
  return json({ shop_id: shop.id, admin_id: created.user.id });
});
