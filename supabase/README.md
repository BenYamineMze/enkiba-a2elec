# Supabase — Enkiba

## Migrations

Le schéma v2 tient en trois fichiers, à appliquer sur une base neuve :

| Fichier | Contenu |
|---|---|
| `migrations/20261017120000_tables.sql` | Tables, contraintes et index |
| `migrations/20261017120100_logic.sql` | Fonctions et triggers (updated_at, profil Auth, stock, retours, devis) |
| `migrations/20261017120200_security.sql` | RLS, policies, droits des rôles `authenticated` et `service_role` |

Toute évolution passe par une **nouvelle** migration datée, jamais par une modification
de ces fichiers ni par l'éditeur SQL du tableau de bord.

## Avant toute commande distante

```bash
supabase projects list
```

Vérifier que le projet lié (`"linked": true`) est **`enkiba-a2elec-dev`**. La prod ne se
touche que sur demande explicite.

## Vérifier une base

- `tests/security_tests.sql` : droits, cloisonnement entre boutiques, règles de stock,
  retours et devis. Tout est annulé à la fin ; le résultat attendu est l'erreur
  `TESTS OK (n vérifications)`.

  ```bash
  supabase db query --linked -f supabase/tests/security_tests.sql
  ```

- Après un `supabase db reset --linked`, **les comptes Auth sont effacés** eux aussi.
  Recréer l'admin dans Authentication → Users, puis lancer `scripts/bootstrap_shop.sql`
  (boutique + rôle admin). Les vendeurs se créent ensuite depuis l'app.

## Edge Functions

`functions/create-seller`, `functions/update-seller`, `functions/set-seller-status` :
déployées à part (`supabase functions deploy <nom>`), non concernées par un reset de la base.
