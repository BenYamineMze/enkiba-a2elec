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

## Sauvegarde de la production

Le workflow **Sauvegarde de la base** (`.github/workflows/db-backup.yml`) tourne chaque
nuit à 01:00 UTC, et à la demande (Actions → Sauvegarde de la base → Run workflow).
Il produit, en lecture seule sur la prod, trois fichiers : `roles.sql`, `schema.sql` et
`data.sql` (comptes `auth` et données `public`). Ils sont chiffrés en GPG (AES-256) puis
conservés **30 jours** comme artefact.

Le dépôt est **public** : un artefact peut être téléchargé par tout compte GitHub. Il
n'est jamais en clair, et la phrase de passe ne doit exister que dans les secrets GitHub
et dans ton gestionnaire de mots de passe.

### Secrets GitHub
| Secret | Contenu |
|---|---|
| `SUPABASE_DB_URL` | chaîne de connexion de la **prod**, mode *Session pooler* (tableau de bord → Connect), mot de passe encodé pour une URL |
| `BACKUP_PASSPHRASE` | phrase de passe de chiffrement, au moins 20 caractères |

Lancer une sauvegarde manuelle **avant toute opération sur la prod** (reset, migration
risquée).

### Restaurer
1. Télécharger l'artefact, puis le déchiffrer :

   ```bash
   gpg -d enkiba-prod-AAAA-MM-JJ-HHMM.tar.gz.gpg | tar -xzf -
   ```

2. Préparer une base au bon schéma : `supabase db reset --linked` sur le projet cible
   (après avoir vérifié le projet lié), ou un projet neuf avec `supabase db push`.
3. Charger les données (les triggers sont désactivés par le fichier lui-même : le stock
   n'est pas recalculé une deuxième fois) :

   ```bash
   psql "<chaîne de connexion du projet cible>" -v ON_ERROR_STOP=1 -f data.sql
   ```

4. Rejouer `tests/security_tests.sql`, puis vérifier dans l'app (connexion, stock, ventes).

`schema.sql` et `roles.sql` servent de référence : le schéma se recrée par les migrations.
Testé en local : sauvegarde d'une base de test, base vidée et recréée, restauration
complète (comptes, stock, mouvements, retours) et 27 tests de sécurité OK.
