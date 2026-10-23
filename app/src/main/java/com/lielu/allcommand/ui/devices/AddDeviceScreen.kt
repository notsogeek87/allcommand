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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.lielu.allcommand.device.DeviceType

/** Ajout d'appareils : la liste de ceux trouvés sur le Wi-Fi, un nom, puis le code affiché sur l'appareil. */
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
    val alreadyAdded by viewModel.alreadyAdded.collectAsState()
    val batch by viewModel.batch.collectAsState()
    val error by viewModel.error.collectAsState()

    LaunchedEffect(step) {
        if (step == AddDeviceStep.Done) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ajouter une TV") },
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
                        type = batch?.device?.type ?: type,
                        batch = batch,
                        onSubmit = viewModel::submitCode,
                        onSkip = viewModel::skipCurrent,
                        onCancel = viewModel::cancelPairing,
                    )
                else ->
                    DeviceForm(
                        name = name,
                        host = host,
                        scanning = scanning,
                        found = found,
                        alreadyAdded = alreadyAdded,
                        viewModel = viewModel,
                    )
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DeviceForm(
    name: String,
    host: String,
    scanning: Boolean,
    found: List<FoundDevice>?,
    alreadyAdded: Int,
    viewModel: AddDeviceViewModel,
) {
    // Saisie manuelle : déverrouillée seulement sur demande explicite (rien trouvé, appareil hors du Wi-Fi).
    var manualAddress by rememberSaveable { mutableStateOf(false) }
    Text(
        "Allumez vos appareils et connectez ce téléphone au même Wi-Fi : ils s'affichent ci-dessous.",
        style = MaterialTheme.typography.bodyMedium,
    )
    when {
        scanning ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Recherche sur le réseau…")
            }
        found.isNullOrEmpty() ->
            Text(
                if (alreadyAdded > 0) {
                    "Tous les appareils trouvés sont déjà ajoutés. Pour en ajouter un autre, allumez-le et relancez " +
                        "la recherche, ou saisissez son adresse IP à la main ci-dessous."
                } else {
                    "Aucun appareil compatible trouvé. Vérifiez qu'il est allumé et sur le même Wi-Fi, puis " +
                        "relancez la recherche, ou saisissez son adresse IP à la main ci-dessous."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        else -> {
            Text("Appareils compatibles (touchez pour choisir) :", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                found.forEach { device ->
                    FilterChip(
                        selected = device.host == host,
                        onClick = {
                            manualAddress = false
                            viewModel.select(device)
                        },
                        label = { Text(device.title) },
                    )
                }
            }
            if (found.size > 1) {
                Button(onClick = viewModel::addAll, modifier = Modifier.fillMaxWidth()) {
                    Text("Tout ajouter (${found.size} appareils)")
                }
                Text(
                    "Chaque appareil affichera son code, à saisir l'un après l'autre.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
    if (!scanning) TextButton(onClick = viewModel::scan) { Text("Relancer la recherche") }

    OutlinedTextField(
        value = name,
        onValueChange = viewModel::onNameChange,
        label = { Text("Nom (ex. Salon)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    // Lecture seule : l'adresse vient du choix dans la liste, pour qu'un geste malheureux ne la change pas.
    OutlinedTextField(
        value = host,
        onValueChange = viewModel::onHostChange,
        readOnly = !manualAddress,
        label = { Text(if (manualAddress) "Adresse IP" else "Adresse IP (choisie dans la liste)") },
        placeholder = { Text("192.168.1.20") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        modifier = Modifier.fillMaxWidth(),
    )
    if (!manualAddress) {
        TextButton(onClick = { manualAddress = true }) { Text("Saisir l'adresse à la main") }
    }
    Button(
        onClick = viewModel::startPairing,
        enabled = host.isNotBlank(),
        modifier = Modifier.fillMaxWidth(),
    ) { Text("Appairer") }
}

@Composable
private fun CodeEntry(
    type: DeviceType,
    batch: BatchProgress?,
    onSubmit: (String) -> Unit,
    onSkip: () -> Unit,
    onCancel: () -> Unit,
) {
    var code by remember(batch?.position) { mutableStateOf("") }
    // Une Android TV affiche un code de 6 caractères hexadécimaux (« 25EC51 ») ; une Titan OS 4 chiffres.
    val hexadecimal = type == DeviceType.ANDROID_TV
    batch?.let {
        Text(
            "Appareil ${it.position}/${it.total} : ${it.device.title}",
            style = MaterialTheme.typography.titleMedium,
        )
    }
    Text(type.pairingHint, style = MaterialTheme.typography.bodyLarge)
    OutlinedTextField(
        value = code,
        onValueChange = { typed ->
            code =
                if (hexadecimal) {
                    typed.uppercase().filter { it in HEX_DIGITS }.take(HEX_CODE_LENGTH)
                } else {
                    typed.filter(Char::isDigit).take(DIGIT_CODE_LENGTH)
                }
        },
        label = { Text("Code affiché sur la télé") },
        singleLine = true,
        keyboardOptions =
            if (hexadecimal) {
                KeyboardOptions(keyboardType = KeyboardType.Ascii, capitalization = KeyboardCapitalization.Characters)
            } else {
                KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
            },
        modifier = Modifier.fillMaxWidth(),
    )
    Button(onClick = { onSubmit(code) }, modifier = Modifier.fillMaxWidth()) { Text("Valider") }
    if (batch != null) TextButton(onClick = onSkip) { Text("Passer cet appareil") }
    TextButton(onClick = onCancel) { Text(if (batch != null) "Tout annuler" else "Annuler") }
}

private const val HEX_DIGITS = "0123456789ABCDEF"
private const val HEX_CODE_LENGTH = 6
private const val DIGIT_CODE_LENGTH = 4
