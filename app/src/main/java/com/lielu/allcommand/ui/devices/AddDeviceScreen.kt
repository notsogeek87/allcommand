package com.lielu.allcommand.ui.devices

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lielu.allcommand.device.DeviceType

/** Ajout d'un appareil : type, nom, adresse, puis appairage par le code affiché sur l'appareil. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddDeviceScreen(
    viewModel: AddDeviceViewModel,
    onBackClick: () -> Unit,
    onDone: () -> Unit,
) {
    val type by viewModel.type.collectAsState()
    val name by viewModel.name.collectAsState()
    val host by viewModel.host.collectAsState()
    val step by viewModel.step.collectAsState()
    val scanning by viewModel.scanning.collectAsState()
    val found by viewModel.found.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(step) {
        if (step == AddDeviceStep.Done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter un appareil") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (step) {
                AddDeviceStep.Working -> CircularProgressIndicator()
                AddDeviceStep.AwaitingCode ->
                    CodeEntry(
                        hint = type.pairingHint,
                        onSubmit = viewModel::submitCode,
                        onCancel = viewModel::cancelPairing,
                    )
                else ->
                    DeviceForm(
                        type = type,
                        name = name,
                        host = host,
                        scanning = scanning,
                        found = found,
                        isAvailable = viewModel.isAvailable(type),
                        viewModel = viewModel,
                    )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DeviceForm(
    type: DeviceType,
    name: String,
    host: String,
    scanning: Boolean,
    found: List<String>?,
    isAvailable: Boolean,
    viewModel: AddDeviceViewModel,
) {
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        DeviceType.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = type == option,
                onClick = { viewModel.onTypeChange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = DeviceType.entries.size),
            ) {
                Text(option.label)
            }
        }
    }
    OutlinedTextField(
        value = name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Nom (ex. Salon)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    DiscoveryPanel(
        type = type,
        scanning = scanning,
        found = found,
        selected = host,
        onSelect = viewModel::onHostChange,
        onRescan = viewModel::scan,
    )
    OutlinedTextField(
        value = host,
        onValueChange = viewModel::onHostChange,
        label = { Text("Adresse IP (remplie si trouvée ci-dessus)") },
        placeholder = { Text("192.168.1.20") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    if (isAvailable) {
        Text(type.pairingHint, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = viewModel::startPairing, modifier = Modifier.fillMaxWidth()) { Text("Appairer") }
    } else {
        Text(
            "Le pilotage de ce type d'appareil n'est pas encore pris en charge.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Appairer") }
    }
}

/** Évite de deviner l'IP : propose les appareils trouvés sur le Wi-Fi, ou dit où la lire sur la télé. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoveryPanel(
    type: DeviceType,
    scanning: Boolean,
    found: List<String>?,
    selected: String,
    onSelect: (String) -> Unit,
    onRescan: () -> Unit,
) {
    Text(
        "Allumez la télé et connectez ce téléphone au même Wi-Fi : l'adresse se trouve toute seule.",
        style = MaterialTheme.typography.bodyMedium,
    )
    when {
        scanning ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Recherche sur le réseau…")
            }
        found.isNullOrEmpty() -> {
            Text(
                "Aucun appareil trouvé. Vérifiez qu'elle est allumée et sur le même Wi-Fi, puis relancez la " +
                    "recherche, ou saisissez l'adresse à la main. ${type.ipHelp}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onRescan) { Text("Relancer la recherche") }
        }
        else -> {
            Text("Appareils trouvés (touchez pour choisir) :", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                found.forEach { ip ->
                    FilterChip(selected = ip == selected, onClick = { onSelect(ip) }, label = { Text(ip) })
                }
            }
            TextButton(onClick = onRescan) { Text("Relancer la recherche") }
        }
    }
}

@Composable
private fun CodeEntry(
    hint: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember { mutableStateOf("") }
    Text(hint, style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = code,
        onValueChange = { code = it.filter(Char::isDigit).take(MAX_CODE_LENGTH) },
        label = { Text("Code affiché sur la télé") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth()) { Text("Valider") }
    TextButton(onClick = onCancel) { Text("Annuler") }
}

private const val MAX_CODE_LENGTH = 8
