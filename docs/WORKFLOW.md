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
1. PR `develop` → `main`
2. Incrémenter `versionCode` et `versionName`
3. Tag `vX.Y.Z` sur `main`

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
