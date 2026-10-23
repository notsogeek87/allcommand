# Android TV Remote v2 — tel qu'implémenté

**Pour qui / pourquoi :** maintenir ou déboguer `androidtv/`. Statut : l'appairage a démarré sur une télé réelle
(code affiché par la télé) ; la saisie de texte et les touches média restent à valider.

Deux ports en **TLS** avec un **certificat client auto-signé** (RSA 2048, généré sans bibliothèque tierce,
`AndroidTvIdentity`). Le certificat de la télé est accepté tel quel (adresse IP, autorité privée). Messages :
protobuf écrit à la main (`Protobuf.kt`), chacun précédé de sa longueur (varint).

## Appairage — port 6467

`PairingMessage` : `protocol_version=2`, `status=200`, puis un message imbriqué.

| Étape | Envoi | Réponse attendue |
| --- | --- | --- |
| 1 | `pairing_request` (champ 10) : service `androidtvremote2`, nom du client | champ 11 |
| 2 | `pairing_option` (20) : encodage hexadécimal, longueur 6, rôle *input* | champ 20 |
| 3 | `pairing_configuration` (30) : idem, rôle *input* | champ 31 — la télé affiche le code |
| 4 | `pairing_secret` (40) : **secret** | champ 41 |

**Secret** = SHA-256 de : module et exposant de la clé publique du client, module et exposant de celle de la télé
(entiers sans octet de signe), puis le code **sans son premier octet**. Le premier octet du code doit égaler le
premier octet du hash : une faute de frappe est détectée localement.

```kotlin
val secret = AndroidTvMessages.pairingSecret(clientKey, serverKey, "25EC51") // null si code incorrect
```

Credentials stockés : `privateKey` (PKCS#8) et `certificate` (DER), en base64.

## Télécommande — port 6466

1. La télé envoie `remote_configure` (1) → on répond avec le nôtre (`code1=622`, infos appareil).
2. La télé envoie `remote_set_active` (2) → on répond `active=622`.
3. Touche : `remote_key_inject` (10) `{ key_code, direction=3 (SHORT) }`.
4. `remote_ping_request` (8) toutes les quelques secondes → réponse `remote_ping_response` (9) avec la même
   valeur ; un fil dédié lit en continu, sinon la télé coupe.

Texte : chaque caractère devient un `KEYCODE_*` (`AndroidTvText`) ; `\b` → `DEL`, `\n` → `ENTER` ; accents retirés,
majuscules abaissées.
