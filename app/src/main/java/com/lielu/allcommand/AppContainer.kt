package com.lielu.allcommand

import android.content.Context
import com.lielu.allcommand.androidtv.AndroidTvDriver
import com.lielu.allcommand.backup.AutoBackupRunner
import com.lielu.allcommand.backup.BackupManager
import com.lielu.allcommand.backup.BackupSection
import com.lielu.allcommand.backup.DevicesBackupSection
import com.lielu.allcommand.backup.SafBackupFolder
import com.lielu.allcommand.backup.SettingsBackupSection
import com.lielu.allcommand.data.devices.DeviceRepository
import com.lielu.allcommand.data.devices.DeviceRepositoryImpl
import com.lielu.allcommand.data.devices.devicesDataStore
import com.lielu.allcommand.data.settings.SettingsRepository
import com.lielu.allcommand.data.settings.SettingsRepositoryImpl
import com.lielu.allcommand.data.settings.settingsDataStore
import com.lielu.allcommand.device.DeviceDrivers
import com.lielu.allcommand.device.DeviceNamer
import com.lielu.allcommand.device.MdnsDeviceNamer
import com.lielu.allcommand.titanos.TitanOsDriver
import com.lielu.allcommand.work.AutoBackupScheduler
import com.lielu.allcommand.work.AutoBackupWorker
import com.lielu.githubupdater.UpdateConfig
import com.lielu.githubupdater.UpdateManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Conteneur de dépendances écrit à la main : une app de ce modèle est petite et mono-module, un framework
 * d'injection ajouterait de la complexité de build (traitement d'annotations, graphes générés) sans bénéfice.
 *
 * Pour brancher les données de l'app dans la sauvegarde, ajouter une [BackupSection] à [backupSections].
 */
class AppContainer(
    context: Context,
) {
    private val appContext = context.applicationContext

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext.settingsDataStore)

    val deviceRepository: DeviceRepository = DeviceRepositoryImpl(appContext.devicesDataStore)

    /** Un pilote par type d'appareil : pour en ajouter un, le déclarer ici (voir `device/DeviceDriver.kt`). */
    val deviceDrivers = DeviceDrivers(listOf(TitanOsDriver(), AndroidTvDriver()))

    val deviceNamer: DeviceNamer = MdnsDeviceNamer(appContext)

    /**
     * Données incluses dans toute sauvegarde (manuelle ou automatique) et restaurées à l'import.
     * Chaque section a un identifiant stable : le renommer ou le retirer rend les anciennes sauvegardes
     * illisibles pour cette section.
     */
    private val backupSections: List<BackupSection> =
        listOf(
            SettingsBackupSection(settingsRepository),
            DevicesBackupSection(deviceRepository),
        )

    val backupManager = BackupManager(backupSections, appId = appContext.packageName)

    private val autoBackupRunner =
        AutoBackupRunner(
            settings = settingsRepository,
            backupManager = backupManager,
            openFolder = { uri -> SafBackupFolder.open(appContext, uri) },
            fileNamePrefix = BackupFileNames.prefix(appContext),
        )

    val autoBackupScheduler = AutoBackupScheduler(appContext)

    val workerFactory = AutoBackupWorker.Factory(autoBackupRunner)

    /** Utilisé par « Sauvegarder maintenant » et « Restaurer la dernière sauvegarde » (voir SettingsViewModel). */
    val autoBackup: AutoBackupRunner = autoBackupRunner

    /** Mise à jour de l'app depuis les releases GitHub de CE dépôt (pas celui de la bibliothèque). */
    val updateManager =
        UpdateManager(
            appContext,
            UpdateConfig(
                githubOwner = BuildConfig.GITHUB_REPOSITORY.substringBefore('/'),
                githubRepository = BuildConfig.GITHUB_REPOSITORY.substringAfter('/'),
                checkIntervalHours = 1,
            ),
        )

    /**
     * Les mises à jour viennent de la dernière release `main` : l'APK release du variant production. Un build
     * debug ou staging a un autre applicationId, Android refuserait d'installer cet APK par-dessus : on ne leur
     * propose donc rien.
     */
    val updatesEnabled: Boolean = !BuildConfig.DEBUG && BuildConfig.FLAVOR == "production"

    /**
     * Réapplique les réglages persistés au cas où le travail périodique n'aurait jamais été planifié.
     *
     * Appelée par [LieluApplication] une fois le conteneur assigné, pas depuis un bloc `init` : cela atteint
     * WorkManager, qui redemande à l'application sa Configuration (donc ce conteneur) pendant son
     * initialisation à la demande.
     */
    fun scheduleAutoBackup() {
        applicationScope.launch {
            autoBackupScheduler.apply(settingsRepository.autoBackup.first())
        }
    }
}
