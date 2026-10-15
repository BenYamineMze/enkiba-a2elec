# Enkiba-A2elec

Application Android de gestion de stock et point de vente (POS), fonctionnant
hors-ligne avec synchronisation Supabase. Anciennement nommée « GStock ».

## Stack
Java (migration Kotlin progressive) · SQLite → Room · Retrofit2 · Supabase (PostgreSQL)

## Documentation
- [Architecture](docs/ARCHITECTURE.md)
- [Workflow Git et releases](docs/WORKFLOW.md)

## Lancer le projet
1. Cloner le dépôt
2. Créer `local.properties` (voir `docs/WORKFLOW.md`, section Configuration)
3. Ouvrir dans Android Studio, puis Run

## Branches
`main` = production · `develop` = intégration · `feature/*`, `hotfix/*`
