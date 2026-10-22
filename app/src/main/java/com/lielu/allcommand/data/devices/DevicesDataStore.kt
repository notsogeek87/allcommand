package com.lielu.allcommand.data.devices

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore

/**
 * Stockage dédié aux appareils (fichier `datastore/devices.preferences_pb`), séparé des réglages : il contient
 * des secrets d'appairage et est exclu de la sauvegarde Android (voir `res/xml/backup_rules.xml`). Renommer
 * [DEVICES_STORE_NAME] impose de mettre à jour ces règles d'exclusion.
 */
const val DEVICES_STORE_NAME = "devices"

val Context.devicesDataStore by preferencesDataStore(name = DEVICES_STORE_NAME)
