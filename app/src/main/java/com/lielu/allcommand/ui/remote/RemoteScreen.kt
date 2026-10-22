package com.lielu.allcommand.ui.remote

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.lielu.allcommand.device.RemoteKey

private val DPAD_BUTTON_SIZE = 72.dp

/** Télécommande de l'appareil choisi : la même disposition pour tous les types d'appareils. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onBackClick: () -> Unit,
) {
    val device by viewModel.device.collectAsState()
    val message by viewModel.message.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(device?.name ?: "Télécommande") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TopRow(viewModel::onKey)
            DirectionPad(viewModel::onKey)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextKey("Retour", RemoteKey.BACK, viewModel::onKey, Modifier.weight(1f))
                TextKey("Accueil", RemoteKey.HOME, viewModel::onKey, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextKey("Vol −", RemoteKey.VOLUME_DOWN, viewModel::onKey, Modifier.weight(1f))
                TextKey("Muet", RemoteKey.MUTE, viewModel::onKey, Modifier.weight(1f))
                TextKey("Vol +", RemoteKey.VOLUME_UP, viewModel::onKey, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                TextKey("Chaîne −", RemoteKey.CHANNEL_DOWN, viewModel::onKey, Modifier.weight(1f))
                TextKey("Lecture / Pause", RemoteKey.PLAY_PAUSE, viewModel::onKey, Modifier.weight(1f))
                TextKey("Chaîne +", RemoteKey.CHANNEL_UP, viewModel::onKey, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun TopRow(onKey: (RemoteKey) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        FilledTonalIconButton(onClick = { onKey(RemoteKey.POWER) }) {
            Icon(Icons.Default.PowerSettingsNew, contentDescription = "Marche / veille")
        }
        FilledTonalButton(onClick = { onKey(RemoteKey.SOURCES) }) { Text("Sources") }
        FilledTonalButton(onClick = { onKey(RemoteKey.OPTIONS) }) { Text("Options") }
    }
}

@Composable
private fun DirectionPad(onKey: (RemoteKey) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        DirectionKey(Icons.Default.KeyboardArrowUp, "Haut", RemoteKey.UP, onKey)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DirectionKey(Icons.Default.KeyboardArrowLeft, "Gauche", RemoteKey.LEFT, onKey)
            Button(onClick = { onKey(RemoteKey.OK) }, modifier = Modifier.size(DPAD_BUTTON_SIZE)) { Text("OK") }
            DirectionKey(Icons.Default.KeyboardArrowRight, "Droite", RemoteKey.RIGHT, onKey)
        }
        DirectionKey(Icons.Default.KeyboardArrowDown, "Bas", RemoteKey.DOWN, onKey)
    }
}

@Composable
private fun DirectionKey(
    icon: ImageVector,
    description: String,
    key: RemoteKey,
    onKey: (RemoteKey) -> Unit,
) {
    FilledTonalIconButton(onClick = { onKey(key) }, modifier = Modifier.size(DPAD_BUTTON_SIZE)) {
        Icon(icon, contentDescription = description)
    }
}

@Composable
private fun TextKey(
    label: String,
    key: RemoteKey,
    onKey: (RemoteKey) -> Unit,
    modifier: Modifier = Modifier,
) {
    FilledTonalButton(onClick = { onKey(key) }, modifier = modifier) { Text(label) }
}
