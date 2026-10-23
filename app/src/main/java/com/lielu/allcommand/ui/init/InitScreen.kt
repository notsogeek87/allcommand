package com.lielu.allcommand.ui.init

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lielu.allcommand.R
import com.lielu.allcommand.device.Device

/** Accueil : liste des appareils pilotables, ajout, suppression ; la barre du haut mène aux Paramètres. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InitScreen(
    viewModel: InitViewModel,
    onSettingsClick: () -> Unit,
    onAddDeviceClick: () -> Unit,
    onDeviceClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val showWelcome by viewModel.showWelcome.collectAsState()
    val message by viewModel.message.collectAsState()
    val devices by viewModel.devices.collectAsState()
    var deviceToDelete by remember { mutableStateOf<Device?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    val importLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) viewModel.onImportRequested(context.contentResolver, uri)
        }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painterResource(R.drawable.ic_logo),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                        Text(stringResource(R.string.app_name))
                    }
                },
                actions = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddDeviceClick) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter un appareil")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        if (devices.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(paddingValues).padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Aucun appareil. Touchez + pour ajouter une télé.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
                items(devices, key = { it.id }) { device ->
                    ListItem(
                        headlineContent = { Text(device.name) },
                        supportingContent = { Text("${device.type.label} · ${device.host}") },
                        trailingContent = {
                            IconButton(onClick = { deviceToDelete = device }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer ${device.name}")
                            }
                        },
                        modifier = Modifier.clickable { onDeviceClick(device.id) },
                    )
                }
            }
        }
    }

    deviceToDelete?.let { device ->
        AlertDialog(
            onDismissRequest = { deviceToDelete = null },
            title = { Text("Supprimer « ${device.name} » ?") },
            text = { Text("L'appairage sera perdu : il faudra de nouveau saisir le code affiché sur l'appareil.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeviceRemoved(device.id)
                        deviceToDelete = null
                    },
                ) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { deviceToDelete = null }) { Text("Annuler") } },
        )
    }

    if (showWelcome) {
        AlertDialog(
            onDismissRequest = viewModel::onWelcomeDismissed,
            title = { Text("Bienvenue") },
            text = {
                Text(
                    "Si vous réinstallez l'app ou changez d'appareil, vous pouvez restaurer vos données " +
                        "depuis un fichier de sauvegarde. Vous pourrez aussi le faire plus tard dans Paramètres.",
                )
            },
            confirmButton = {
                TextButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                    Text("Restaurer un fichier…")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::onWelcomeDismissed) { Text("Plus tard") }
            },
        )
    }
}
