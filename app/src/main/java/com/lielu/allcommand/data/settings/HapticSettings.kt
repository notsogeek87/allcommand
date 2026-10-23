package com.lielu.allcommand.data.settings

/** Intensité du retour haptique. Android ne laisse pas régler l'amplitude d'un clic : chaque niveau choisit un clic plus ou moins marqué. */
enum class HapticIntensity {
    LOW,
    MEDIUM,
    HIGH,
    ;

    companion object {
        fun fromStorageValue(value: String?): HapticIntensity = entries.firstOrNull { it.name == value } ?: MEDIUM
    }
}

/** Réglages du retour haptique de la télécommande : activé par défaut, intensité moyenne. */
data class HapticSettings(
    val enabled: Boolean = true,
    val intensity: HapticIntensity = HapticIntensity.MEDIUM,
)
