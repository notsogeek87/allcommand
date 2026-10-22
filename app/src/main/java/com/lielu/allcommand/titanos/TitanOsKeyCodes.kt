package com.lielu.allcommand.titanos

import com.lielu.allcommand.device.RemoteKey

/**
 * Codes de touche attendus par le sujet MQTT `rc` d'une Philips Titan OS : ce sont les codes clavier Linux
 * (`linux/input-event-codes.h`), tels que documentés par le projet communautaire
 * philips-titan-os-55pml9019-remote (docs/TITAN_OS_API.md).
 */
internal fun RemoteKey.titanKeyCode(): Int =
    when (this) {
        RemoteKey.UP -> 103
        RemoteKey.DOWN -> 108
        RemoteKey.LEFT -> 105
        RemoteKey.RIGHT -> 106
        RemoteKey.OK -> 352
        RemoteKey.BACK -> 158
        RemoteKey.HOME -> 102
        RemoteKey.OPTIONS -> 357
        RemoteKey.SOURCES -> 394
        RemoteKey.VOLUME_UP -> 115
        RemoteKey.VOLUME_DOWN -> 114
        RemoteKey.MUTE -> 113
        RemoteKey.POWER -> 116
        RemoteKey.PLAY_PAUSE -> 164
        RemoteKey.CHANNEL_UP -> 402
        RemoteKey.CHANNEL_DOWN -> 403
    }
