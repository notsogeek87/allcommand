# Documentation allCommand

Point d'entrée de la documentation. Le [README racine](../README.md) décrit le modèle de dépôt (CI, mise à jour,
sauvegarde) ; ces pages décrivent l'app elle-même.

## Guides — comment faire

- [Ajouter un appareil](guides/ajouter-un-appareil.md) — recherche automatique, « Tout ajouter », code d'appairage.
- [La télécommande et le retour haptique](guides/telecommande-et-haptique.md) — disposition, réglages, maquette.

## API — référence

- [Pilotes d'appareils](api/pilotes-appareils.md) — `DeviceDriver`, `RemoteConnection`, `RemoteConnections`.
- [Android TV Remote v2](api/android-tv-remote-v2.md) — le protocole tel qu'implémenté.

## Architecture — choix techniques (ADR)

- [2026-10-23 — Découverte des appareils : scan, sonde, noms](architecture/2026-10-23-decouverte-des-appareils.md)
- [2026-10-23 — Connexions de télécommande gardées ouvertes](architecture/2026-10-23-connexions-gardees-ouvertes.md)
- [2026-10-23 — Retour haptique via `performHapticFeedback`](architecture/2026-10-23-retour-haptique.md)

## Legacy

[`legacy/`](legacy/README.md) — documentation obsolète conservée.
