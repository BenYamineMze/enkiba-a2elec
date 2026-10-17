import { createClient } from "npm:@supabase/supabase-js@2";

const cors = { "Access-Control-Allow-Origin": "*", "Access-Control-Allow-Headers": "authorization, content-type, apikey" };
const json = (b: unknown, s = 200) => new Response(JSON.stringify(b), { status: s, headers: { ...cors, "Content-Type": "application/json" } });

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: cors });
  if (req.method !== "POST") return json({ error: "method_not_allowed" }, 405);

  const url = Deno.env.get("SUPABASE_URL")!;
  const caller = createClient(url, Deno.env.get("SUPABASE_ANON_KEY")!, { global: { headers: { Authorization: req.headers.get("Authorization") ?? "" } } });
  const { data: u, error: uErr } = await caller.auth.getUser();
  if (uErr || !u.user) return json({ error: "unauthorized" }, 401);

  let body: Record<string, string>;
  try { body = await req.json(); } catch { return json({ error: "invalid_json" }, 400); }
  const { shop_id, user_id, username, lastname, telephone, password } = body;
  if (!shop_id || !user_id) return json({ error: "invalid_input" }, 400);
  if (user_id === u.user.id) return json({ error: "cannot_target_self" }, 400);
  if (password !== undefined && password !== "" && password.length < 8) return json({ error: "weak_password" }, 400);

  const admin = createClient(url, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!);

  const { data: me } = await admin.from("shop_members").select("role").eq("shop_id", shop_id).eq("user_id", u.user.id).eq("role", "admin").maybeSingle();
  const { data: myProfile } = await admin.from("profiles").select("active").eq("id", u.user.id).maybeSingle();
  if (!me || !myProfile?.active) return json({ error: "forbidden" }, 403);

  const { data: target } = await admin.from("shop_members").select("role").eq("shop_id", shop_id).eq("user_id", user_id).maybeSingle();
  if (!target) return json({ error: "not_found" }, 404);
  if (target.role !== "seller") return json({ error: "only_sellers" }, 403);

  const { error: pErr } = await admin.from("profiles").update({
    username: username ?? "", lastname: lastname ?? "", telephone: telephone ?? "",
  }).eq("id", user_id);
  if (pErr) return json({ error: "update_failed" }, 500);
  if (password) await admin.auth.admin.updateUserById(user_id, { password });
  return json({ ok: true });
});
