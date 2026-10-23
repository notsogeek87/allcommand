# Ajouter un appareil

**Pour qui / pourquoi :** l'utilisateur qui veut piloter une télé depuis l'app sans connaître son adresse IP.

## Déroulé

1. Allumer la télé, connecter le téléphone au **même Wi-Fi**.
2. **Ajouter une TV** sur l'écran d'accueil : l'app cherche les appareils compatibles. Chacun apparaît avec son nom quand on le
   connaît (`Salon · 192.168.1.20`), sinon avec son type (`Philips Titan OS · 192.168.1.20`).
3. Toucher un appareil (remplit l'IP et le nom), puis **Appairer**. L'adresse IP est en **lecture seule** pour éviter
   un changement par mégarde.
   - Plusieurs trouvés : **Tout ajouter** appaire à la suite ceux qui ne sont pas déjà enregistrés ; chaque télé
     affiche son code, à saisir l'un après l'autre. *Passer cet appareil* saute celui en cours.
4. Saisir le code affiché sur la télé :
   - **Titan OS** : 4 chiffres.
   - **Android TV** : 6 caractères hexadécimaux (`0-9`, `A-F`, ex. `25EC51`) — le clavier accepte les lettres.

Les appareils **déjà enregistrés** n'apparaissent plus dans la liste. Si rien n'est trouvé, toucher **Saisir l'adresse à la main**
(ce qui déverrouille le champ) : le type est alors détecté automatiquement. Où la lire sur la télé : Titan OS → *Paramètres › Réseau ›
Informations sur le réseau* ; Android TV → *Paramètres › Réseau et Internet*.

## Ce qui est écarté

Une box, un aspirateur connecté… peuvent avoir un port ouvert sans être pilotables. Chaque pilote **vérifie** que
l'appareil est bien le sien avant de l'afficher (voir [la décision](../architecture/2026-10-23-decouverte-des-appareils.md)).

## Renommer

Dans l'écran de télécommande, toucher le nom en en-tête.
