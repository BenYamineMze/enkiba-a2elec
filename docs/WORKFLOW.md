# Workflow

## Branches
- `main` : identique à la version publiée sur le Play Store
- `develop` : intégration des nouveautés
- `feature/<sujet>` : une branche par chantier, depuis `develop`
- `hotfix/<sujet>` : correctif urgent, depuis `main`

## Commits (Conventional Commits)
`feat:` `fix:` `refactor:` `docs:` `chore:` `test:`
Exemple : `feat: sauvegarde persistante des devis`

## Release
1. Incrémenter `versionCode` (toujours supérieur à la version publiée) et `versionName`
   (`X.Y.Z`) dans `app/build.gradle.kts`, sur `develop`.
2. PR `develop` → `main`, fusion après CI verte.
3. Tag sur `main` : `git tag vX.Y.Z && git push origin vX.Y.Z` (le tag doit être
   exactement `v` + `versionName`).
4. Le workflow **Release** construit l'AAB signé, vérifie la signature et crée une
   release GitHub **en brouillon** avec l'AAB et le fichier `mapping-*.txt`.
5. Play Console → Tester → **Test interne** → nouvelle version → importer l'AAB.
   Puis Test fermé, puis Production en déploiement progressif.
6. Publier la release GitHub (retirer le brouillon) une fois la version acceptée.

Garder le fichier `mapping-*.txt` de chaque version : sans lui, les plantages
remontés par la Play Console sont illisibles (code minifié par R8).

## Signature de la release
La même clé d'importation (upload key) sert pour toutes les versions publiées sous
`com.enkiba.app`. **Ne jamais la perdre ni la commiter.** En cas de perte, demander une
réinitialisation de la clé d'importation dans la Play Console (Play App Signing).

### Secrets GitHub (Settings → Secrets and variables → Actions)
| Secret | Contenu |
|---|---|
| `ENKIBA_KEYSTORE_BASE64` | le fichier `.jks` encodé : `base64 -i upload.jks \| pbcopy` (macOS) |
| `ENKIBA_KEYSTORE_PASSWORD` | mot de passe du keystore |
| `ENKIBA_KEY_ALIAS` | alias de la clé |
| `ENKIBA_KEY_PASSWORD` | mot de passe de la clé |

### En local (facultatif)
Créer `keystore.properties` à la racine (ignoré par Git) :

    storeFile=/chemin/absolu/vers/upload.jks
    storePassword=...
    keyAlias=...
    keyPassword=...

puis `./gradlew bundleRelease`. Sans ce fichier ni les variables `ENKIBA_*`, la release
est construite non signée (c'est le cas des builds de PR).

Attention : le build **release** pointe vers la base Supabase de **production**.
Ne jamais l'installer pour tester ; tester sur le build debug (base dev).

## Hotfix
1. Branche `hotfix/...` depuis `main`, PR vers `main`, tag `vX.Y.Z`
2. Fusionner `main` dans `develop`

## Configuration locale
Fichiers jamais commités : `local.properties`, `keystore.properties`, `*.jks`.
Variables attendues dans `local.properties` :

    supabase.url=...
    supabase.anon.key=...

## Règle d'or
Ne jamais modifier l'`applicationId` : c'est l'identité de l'app sur le Play Store.
