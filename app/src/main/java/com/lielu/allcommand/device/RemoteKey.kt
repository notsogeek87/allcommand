package com.lielu.allcommand.device

/**
 * Touches de la télécommande, indépendantes du type d'appareil : chaque pilote les traduit dans le protocole
 * de son appareil.
 */
enum class RemoteKey {
    UP,
    DOWN,
    LEFT,
    RIGHT,
    OK,
    BACK,
    HOME,
    OPTIONS,
    SOURCES,
    VOLUME_UP,
    VOLUME_DOWN,
    MUTE,
    POWER,
    PLAY_PAUSE,
    PREVIOUS,
    NEXT,
    CHANNEL_UP,
    CHANNEL_DOWN,
}
