# Pilotes d'appareils

**Pour qui / pourquoi :** ajouter un type d'appareil, ou comprendre ce qu'un pilote doit fournir. Code :
`app/src/main/java/com/lielu/allcommand/device/`.

Ajouter un type = une valeur de `DeviceType` (avec `discoveryPort` et `ipHelp`), un `DeviceDriver`, son
enregistrement dans `AppContainer.deviceDrivers`.

## `DeviceDriver`

```kotlin
interface DeviceDriver {
    val type: DeviceType
    val isAvailable: Boolean get() = true
    suspend fun probe(host: String): Boolean = true   // est-ce bien un appareil de ce type ?
    suspend fun startPairing(host: String): PairingSession
    fun connect(device: Device): RemoteConnection
}
```

- `probe` : un port ouvert ne suffit pas. **Titan OS** : la connexion MQTT anonyme (port 8883) aboutit.
  **Android TV** : poignée de main TLS sur le port 6466. Sert au scan et à la détection du type d'une IP saisie.
- `startPairing(host)` → `PairingSession.complete(code)` renvoie les secrets, stockés dans `Device.credentials`
  (jamais sauvegardés dans le JSON ni l'Auto Backup).

## `RemoteConnection`

```kotlin
interface RemoteConnection {
    suspend fun open() = Unit           // ouvre sans rien envoyer (première touche instantanée)
    suspend fun reconnect() = open()    // repart d'une liaison neuve (retour dans l'app)
    suspend fun send(key: RemoteKey)
    suspend fun sendText(text: String)  // "\b" efface, "\n" valide ; par défaut : non pris en charge
    fun close()
}
```

`RemoteKey` est indépendant du type : chaque pilote le traduit (Titan OS : codes clavier Linux ; Android TV :
`KEYCODE_*`). Touches : directions, OK, retour, accueil, options, sources, volume ±, muet, marche, lecture-pause,
précédent, suivant, chaîne ±.

## `RemoteConnections`

Registre process-wide (`AppContainer.remoteConnections`) : `get(device)` renvoie la connexion de l'appareil,
la même tant que type, adresse et secrets ne changent pas (renommer ne la coupe pas) ; `retain(ids)` ferme celles
des appareils supprimés.

```kotlin
val connection = container.remoteConnections.get(device)
connection.send(RemoteKey.VOLUME_UP)
```
