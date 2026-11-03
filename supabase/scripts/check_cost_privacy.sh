#!/bin/bash
# Preuve que le prix d'achat est invisible pour un vendeur, en appelant directement l'API
# (comme le ferait quelqu'un qui contourne l'app).
#   bash supabase/scripts/check_cost_privacy.sh            → projet de dev
#   ENKIBA_ENV=prod bash supabase/scripts/check_cost_privacy.sh
# L'e-mail et le mot de passe sont demandés au clavier ; rien n'est affiché ni enregistré.
set -euo pipefail

if [ "${ENKIBA_ENV:-dev}" = "prod" ]; then
  URL="https://brnlbtlhuamtvpwbwypm.supabase.co"; KEY="sb_publishable_Q7w6vjWFs5S7Gm1_28FWlA_bwQroePU"
else
  URL="https://kjggrqklyptngpfyuuon.supabase.co"; KEY="sb_publishable__OvL970ysqZ_0qBlvtrrCg_hcH7uCe8"
fi

read -r -p "E-mail du compte à tester : " EMAIL
read -r -s -p "Mot de passe : " PASSWORD; echo

TOKEN=$(EMAIL="$EMAIL" PASSWORD="$PASSWORD" python3 -I -c '
import json, os
print(json.dumps({"email": os.environ["EMAIL"], "password": os.environ["PASSWORD"]}))' |
  curl -s "$URL/auth/v1/token?grant_type=password" -H "apikey: $KEY" -H "Content-Type: application/json" -d @- |
  python3 -I -c 'import json, sys; print(json.load(sys.stdin).get("access_token", ""))')
unset PASSWORD
[ -n "$TOKEN" ] || { echo "Connexion refusée (e-mail ou mot de passe)."; exit 1; }

get() {  # nombre de lignes renvoyées par GET /rest/v1/$1
  curl -s "$URL/rest/v1/$1" -H "apikey: $KEY" -H "Authorization: Bearer $TOKEN" |
    python3 -I -c 'import json, sys; d = json.load(sys.stdin); print(len(d) if isinstance(d, list) else d)'
}

echo "Produits visibles (catalogue)     : $(get 'products?select=id&deleted_at=is.null')"
echo "Prix d'achat visibles             : $(get 'product_costs?select=product_id')"
echo "Coûts des lignes de vente visibles : $(get 'sale_item_costs?select=sale_item_id')"

# Tentative d'écriture dans la boutique, sur un produit qui n'existe pas : rien ne peut
# être modifié, même avec un compte admin (refus RLS pour un vendeur, clé étrangère sinon)
PRODUCT=$(curl -s "$URL/rest/v1/products?select=shop_id&limit=1" -H "apikey: $KEY" -H "Authorization: Bearer $TOKEN" |
  python3 -I -c 'import json, sys, uuid; d = json.load(sys.stdin); print(json.dumps({"product_id": str(uuid.uuid4()), "shop_id": d[0]["shop_id"], "cost_price": 1}) if d else "")')
if [ -n "$PRODUCT" ]; then
  CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$URL/rest/v1/product_costs" -H "apikey: $KEY" \
    -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" -H "Prefer: return=minimal" -d "$PRODUCT")
  echo "Écriture d'un prix d'achat        : HTTP $CODE (401/403 = refusé par la sécurité ; 409 = admin, bloqué par le produit inexistant)"
fi
echo "Vendeur : attendu 0 prix d'achat, 0 coût et une écriture refusée."
