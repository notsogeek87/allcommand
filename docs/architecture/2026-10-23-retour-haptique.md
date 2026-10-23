# 2026-10-23 — Retour haptique via `performHapticFeedback`

**Statut :** adopté. **Contexte :** chaque touche doit donner un petit clic physique, sans bourdonnement ni
permission inutile, en respectant les réglages du téléphone.

## Décision

- `View.performHapticFeedback(constante)` plutôt que `Vibrator` : aucune permission `VIBRATE`, et le **réglage
  système** « vibration au toucher » est respecté sans le forcer (aucun drapeau d'ignorance).
- Le système joue **un** clic par appel : pas de double vibration. Les composants Compose utilisés ne vibrent pas
  d'eux-mêmes.
- Les touches réagissent **au toucher** (`detectTapGestures.onPress`), pas au relâchement : sensation de clic.
- Appui prolongé : la commande se répète mais **sans** vibrer (`onPress` n'est appelé qu'une fois).
- Intensité : Android n'expose pas l'amplitude d'un clic ; chaque niveau choisit une constante plus ou moins nette
  (`Haptics.constantFor`) — Faible : `CLOCK_TICK`, Moyen : `VIRTUAL_KEY` (touche) / `CONFIRM` (OK), Fort :
  `CONFIRM` / `LONG_PRESS`. Avant Android 11, `CONFIRM` est remplacé par `CONTEXT_CLICK` / `KEYBOARD_TAP`.

```kotlin
val haptics = rememberHaptics(settings)       // settings : HapticSettings (activé, intensité)
haptics.perform(HapticKind.CONFIRM)           // KEY pour un appui ordinaire
```

## Conséquences

- Pas de motif de vibration personnalisé ni de réglage fin de durée.
- Les réglages (`haptics_enabled`, `haptics_intensity`) sont dans le DataStore ; ils ne sont pas dans la sauvegarde.

Voir [le guide](../guides/telecommande-et-haptique.md).
