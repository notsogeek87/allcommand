package com.lielu.allcommand.data.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    /** Apparence choisie dans Paramètres — [ThemeMode.SYSTEM] par défaut. */
    val themeMode: Flow<ThemeMode>

    suspend fun setThemeMode(mode: ThemeMode)

    val autoBackup: Flow<AutoBackupSettings>

    suspend fun setAutoBackupEnabled(enabled: Boolean)

    /** Mémorise le dossier de sauvegarde automatique ; `null` l'oublie (et la désactive). */
    suspend fun setAutoBackupFolder(uri: String?)

    suspend fun setAutoBackupKeep(keep: Int)

    val backupStatus: Flow<BackupStatus>

    suspend fun recordBackupSuccess(timestamp: Long)

    suspend fun recordBackupFailure(message: String)

    /** Retour haptique des touches de la télécommande. */
    val haptics: Flow<HapticSettings>

    suspend fun setHapticsEnabled(enabled: Boolean)

    suspend fun setHapticIntensity(intensity: HapticIntensity)

    /** `true` une fois l'invitation de bienvenue (« restaurer une sauvegarde ? ») traitée. */
    val welcomeShown: Flow<Boolean>

    suspend fun setWelcomeShown()

    /** Dernière télécommande ouverte : l'app s'y rouvre directement au lancement. */
    val lastDeviceId: Flow<String?>

    suspend fun setLastDeviceId(id: String)
}
