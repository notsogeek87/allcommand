# 2026-10-23 — Connexions de télécommande gardées ouvertes

**Statut :** adopté. **Contexte :** la connexion appartenait à l'écran et se fermait en le quittant ; revenir ou
changer d'appareil imposait une reconnexion visible à la première touche.

## Décision

- Les connexions vivent dans `RemoteConnections`, détenu par `AppContainer` : elles survivent à la navigation.
- À l'ouverture de l'écran, `open()` réchauffe la liaison ; Titan OS détecte une liaison coupée (`isConnected`).
- Au **retour dans l'app après plus de 5 s**, `reconnect()` refait la liaison (Android peut avoir coupé ou figé le
  réseau, la télé ferme une liaison sans réponse). Les touches pressées pendant ce temps attendent (verrou de la
  connexion) et ne sont pas perdues.
- Un bandeau avec barre de progression explique l'attente à l'utilisateur.
- Fermeture : à la suppression de l'appareil (`retain`) ou à la mort du processus.

## Conséquences

- Pas de service d'avant-plan : si Android tue le processus, tout repart de zéro à la prochaine ouverture.
- Renommer un appareil ne coupe pas la liaison (comparaison sur type, adresse, secrets).
