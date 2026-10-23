# 2026-10-23 — Découverte des appareils : scan, sonde, noms

**Statut :** adopté. **Contexte :** saisir une IP à la main est un obstacle ; un simple port ouvert fait aussi
apparaître box et aspirateurs connectés.

## Décision

1. **Scan** (`LanScanner`) : connexion TCP sur le port de chaque type, sur les 254 adresses du /24 du téléphone
   (64 sondes en parallèle, 400 ms). Aucune permission requise.
2. **Sonde** (`DeviceDriver.probe`) : seul un appareil que le pilote sait vraiment piloter est listé.
3. **Nom** (`MdnsDeviceNamer`) : mDNS (`_androidtvremote2._tcp`, `_googlecast._tcp`, attribut `fn`), à défaut DNS
   inverse. Un nom absent ne bloque rien.
4. Les appareils **déjà enregistrés** (même IP) sont retirés de la liste.

## Conséquences

- Le scan ne couvre que le /24 du téléphone ; au-delà, saisie manuelle (type détecté par les sondes).
- Les sondes ajoutent quelques secondes ; la Titan OS doit accepter la connexion MQTT anonyme.
- Pas de cache : le scan est refait à l'ouverture de l'écran d'ajout.

Voir [le guide](../guides/ajouter-un-appareil.md) et [les pilotes](../api/pilotes-appareils.md).
