# 📱 Contexte du Projet : Enkiba-A2elec (anciennement GStock)
*Ce document sert de "Super-Prompt" pour résumer l'architecture et les fonctionnalités de l'application.*

## 1. Identité de l'Application
GStock est une application Android native de **Gestion de Stock et Point de Vente (POS) fonctionnant en mode "Offline-First"**. 
* **Langage :** Java (Android)
* **Base de données locale :** SQLite (via `SQLiteOpenHelper` et DAOs personnalisés).
* **Backend distant :** Supabase (PostgreSQL) avec API REST.
* **Réseau :** Retrofit2 pour communiquer avec Supabase.

## 2. L'Architecture Actuelle (Comment ça marche)
L'application est conçue pour fonctionner sans internet, puis se synchroniser quand la connexion revient.

* **Le Modèle de Données (Les piliers) :**
  * `Product` : Gère le stock actuel (`quantity`), le stock de référence (`initial_stock`), le prix, et le code QR. **Utilise des UUID (String)** pour éviter les doublons hors-ligne.
  * `SaleHeader` & `SaleItem` : Représentent un ticket de caisse et ses articles. Gèrent les prix négociés (`has_negotiation`).
  * `ProductHistory` : Trace chaque action (ajout, modification, vente, suppression) pour l'audit.
  * `User` & `Client` : Gestion des rôles (Admin/Vendeur) et des clients pour les factures.
  * `Quote` (Devis) : Structure préparée pour sauvegarder des propositions commerciales sans impacter le stock.

* **Le Cœur du Système : La Synchronisation (`SynchronizationManager`)**
  1. **Phase PUSH :** Le système lit les tables locales (SQLite). Tout ce qui a un `sync_status = 'pending'` ou `'deleted'` est envoyé à Supabase via Retrofit.
  2. **Phase PULL :** Le système demande à Supabase toutes les lignes modifiées depuis la dernière date de synchro (`updated_at > last_sync`), et met à jour SQLite.
  3. **Résolution des IDs :** Les ventes créées hors-ligne ont un ID local négatif (ex: `-123`). Après l'envoi, Supabase renvoie le vrai ID positif, et l'appli met à jour ses tables locales (`updateLocalId`).

* **Sécurité Distante (Supabase RLS) :**
  Des règles (Row Level Security) strictes sont en place sur Supabase pour autoriser la lecture universelle (pour que l'historique affiche les noms), mais limiter l'écriture aux utilisateurs connectés via l'appli. De plus, la suppression d'un produit (via RPC) efface en cascade ses ventes et son historique.

## 3. Les Dernières Grandes Évolutions (Ce qu'on a fait)
1. **Migration vers UUID :** Remplacement des IDs entiers (1, 2, 3) des produits par des chaînes uniques (UUID) pour éradiquer les conflits quand 2 vendeurs créent un produit en même temps sans internet.
2. **Génération de Documents PDF :** L'application génère des Factures (après une vente) et des Devis (sans toucher au stock).
3. **Statistiques Visuelles :** Ajout de graphiques circulaires (PieChart) et de barres de progression cliquables pour modifier rapidement un stock critique.
4. **Vente Flexible :** Gestion du "Prix Négocié" (alerte si réduction > 20%) et refonte du panier (BottomSheet).

---

## 🚀 Plan de Modernisation (Ce qu'il faut améliorer)

Pour rendre l'application plus évolutive, lisible et "moderne", voici la feuille de route architecturale :

### A. Modernisation de l'Architecture (La priorité)
* **Passer à MVVM (Model-View-ViewModel) :** Actuellement, les `Activity` font tout (UI, requêtes SQL, appels réseaux). Il faut séparer la logique :
  * Les `ViewModels` prépareront la donnée.
  * Les `Repositories` s'occuperont de décider s'il faut lire SQLite ou Supabase.
* **Migration vers Room Database :** Remplacer le vieux `DatabaseHelper` et les requêtes SQL en texte brut par `Room`. Cela divisera le code de base de données par 3, évitera les crashs SQL (vérification à la compilation) et facilitera les relations (LiveData/Flow).

### B. Modernisation du Code et de l'UI
* **ViewBinding :** Supprimer tous les `findViewById` qui alourdissent le code et causent parfois des `NullPointerException`.
* **Design Material 3 :** Harmoniser les couleurs, utiliser les nouveaux composants Google (Cards, Bottom Sheets fluides, typographie moderne).
* *(Optionnel mais recommandé)* **Introduction progressive de Kotlin :** Commencer à écrire les nouvelles fonctionnalités en Kotlin (100% interopérable avec votre Java actuel).

### C. Évolution Fonctionnelle (À venir)
* Sauvegarde persistante des devis en base de données locale et distante.
* Transformation en 1 clic d'un Devis enregistré vers une Vente définitive.
* Gestion du multi-boutique (Si l'entreprise a plusieurs dépôts).
```eof

### Comment utiliser ce résumé pour la suite ?

Quand vous serez prêt à attaquer un gros chantier (par exemple, "Je veux refaire l'écran de vente" ou "Je veux passer ma base de données sous Room"), vous pourrez me dire :
*"En te basant sur le fichier GStock_Architecture_Context, aide-moi à refaire l'architecture de la base de données locale en utilisant Room Database."*

Par quoi aimeriez-vous commencer pour cette reprise ? 
1. **L'architecture interne** (Nettoyer le code, séparer les Vues et la logique, passer à Room/MVVM) ?
2. **Le Design visuel** (Refaire les écrans pour que ça fasse plus moderne et professionnel) ?
3. **Une nouvelle fonctionnalité** (Finir la sauvegarde des devis en base de données) ?
