package com.lielu.allcommand.ui.remote

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Input
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.lielu.allcommand.device.RemoteKey
import com.lielu.allcommand.ui.haptics.HapticKind
import com.lielu.allcommand.ui.haptics.rememberHaptics
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val DPAD_CELL = 88.dp
private val KEY_HEIGHT = 56.dp
private const val REPEAT_DELAY_MILLIS = 450L
private const val REPEAT_INTERVAL_MILLIS = 130L

private enum class Tab(
    val label: String,
    val icon: ImageVector,
) {
    TV("TV", Icons.Default.Tv),
    KEYBOARD("Clavier", Icons.Default.Keyboard),
}

/**
 * Télécommande à une main : les commandes fréquentes sont groupées en bas, sans défilement, et chaque touche
 * donne un petit clic haptique (voir `ui/haptics`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RemoteScreen(
    viewModel: RemoteViewModel,
    onSettingsClick: () -> Unit,
) {
    val device by viewModel.device.collectAsState()
    val message by viewModel.message.collectAsState()
    val connection by viewModel.connection.collectAsState()
    val hapticSettings by viewModel.haptics.collectAsState()
    val haptics = rememberHaptics(hapticSettings)
    val snackbarHostState = remember { SnackbarHostState() }
    var tab by rememberSaveable { mutableStateOf(Tab.TV) }
    var renaming by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        val text = message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(text)
        viewModel.onMessageShown()
    }

    // Au retour dans l'app, la liaison avec l'appareil est refaite (voir RemoteViewModel.onForegrounded).
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { viewModel.onBackgrounded() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { viewModel.onForegrounded() }

    Scaffold(
        topBar = {
            // Pas de TopAppBar ici : c'est donc à l'en-tête de laisser la place à la barre d'état.
            Column(modifier = Modifier.statusBarsPadding()) {
                RemoteHeader(
                    name = device?.name ?: "Télécommande",
                    connection = connection,
                    onRename = { renaming = true },
                    onPower = {
                        haptics.perform(HapticKind.KEY)
                        viewModel.onKey(RemoteKey.POWER)
                    },
                    onSettings = onSettingsClick,
                )
                ConnectionNotice(connection)
            }
        },
        bottomBar = {
            NavigationBar {
                Tab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = {
                            if (tab != entry) haptics.perform(HapticKind.KEY)
                            tab = entry
                        },
                        icon = { Icon(entry.icon, contentDescription = null) },
                        label = { Text(entry.label) },
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            // Contenu poussé vers le bas (zone du pouce) ; défile seulement si l'écran est trop petit.
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = maxHeight)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.Bottom),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val press: (RemoteKey, HapticKind) -> Unit = { key, kind ->
                    haptics.perform(kind)
                    viewModel.onKey(key)
                }
                // Répétition d'un appui prolongé : la touche part sans nouvelle vibration.
                val repeat: (RemoteKey) -> Unit = { viewModel.onKey(it) }
                when (tab) {
                    Tab.TV -> TvPanel(press, repeat)
                    Tab.KEYBOARD -> KeyboardPanel(viewModel::onText, press)
                }
            }
        }
    }

    if (renaming) {
        RenameDialog(
            current = device?.name.orEmpty(),
            onDismiss = { renaming = false },
            onConfirm = {
                viewModel.onRename(it)
                renaming = false
            },
        )
    }
}

/** Explique l'attente pendant la (re)connexion : on ne laisse pas l'utilisateur croire à un plantage. */
@Composable
private fun ConnectionNotice(connection: ConnectionState) {
    val text =
        when (connection) {
            ConnectionState.RECONNECTING ->
                "Reconnexion à l'appareil… C'est normal après un changement d'application : Android coupe " +
                    "la liaison en arrière-plan. Quelques secondes ; vos touches partiront dès que c'est prêt."
            ConnectionState.CONNECTING -> "Connexion à l'appareil… Quelques secondes."
            else -> return
        }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun RemoteHeader(
    name: String,
    connection: ConnectionState,
    onRename: () -> Unit,
    onPower: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(
                        1f,
                    ).clickable(onClickLabel = "Renommer", onClick = onRename)
                    .padding(vertical = 6.dp),
        ) {
            Text(name, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                val color =
                    when (connection) {
                        ConnectionState.CONNECTED -> MaterialTheme.colorScheme.primary
                        ConnectionState.CONNECTING, ConnectionState.RECONNECTING -> MaterialTheme.colorScheme.outline
                        ConnectionState.OFFLINE -> MaterialTheme.colorScheme.error
                    }
                Box(Modifier.size(8.dp).background(color, CircleShape))
                Text(
                    when (connection) {
                        ConnectionState.CONNECTED -> "Connecté"
                        ConnectionState.CONNECTING -> "Connexion…"
                        ConnectionState.RECONNECTING -> "Reconnexion…"
                        ConnectionState.OFFLINE -> "Hors ligne"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onPower, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Default.PowerSettingsNew,
                contentDescription = "Marche / veille",
                tint = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.Settings, contentDescription = "Paramètres")
        }
    }
}

@Composable
private fun TvPanel(
    press: (RemoteKey, HapticKind) -> Unit,
    repeat: (RemoteKey) -> Unit,
) {
    // Retour / Accueil / Sources (la liste des sources se parcourt ensuite avec le pavé)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        PadKey(Icons.AutoMirrored.Filled.ArrowBack, "Retour", Modifier.weight(1f).height(KEY_HEIGHT)) {
            press(RemoteKey.BACK, HapticKind.KEY)
        }
        PadKey(
            Icons.Default.Home,
            "Accueil",
            Modifier.weight(1f).height(KEY_HEIGHT),
        ) { press(RemoteKey.HOME, HapticKind.KEY) }
        PadKey(Icons.Default.Input, "Sources", Modifier.weight(1f).height(KEY_HEIGHT)) {
            press(RemoteKey.SOURCES, HapticKind.KEY)
        }
    }
    // Pavé directionnel : un seul ensemble, OK au centre
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.size(DPAD_CELL))
            DirectionKey(Icons.Default.KeyboardArrowUp, "Haut", RemoteKey.UP, press, repeat)
            Spacer(Modifier.size(DPAD_CELL))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            DirectionKey(Icons.Default.KeyboardArrowLeft, "Gauche", RemoteKey.LEFT, press, repeat)
            RemoteButton(
                label = "OK",
                onPress = { press(RemoteKey.OK, HapticKind.CONFIRM) },
                modifier = Modifier.size(DPAD_CELL),
                shape = CircleShape,
                container = MaterialTheme.colorScheme.primary,
                content = MaterialTheme.colorScheme.onPrimary,
            ) { Text("OK", style = MaterialTheme.typography.titleLarge) }
            DirectionKey(Icons.Default.KeyboardArrowRight, "Droite", RemoteKey.RIGHT, press, repeat)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Spacer(Modifier.size(DPAD_CELL))
            DirectionKey(Icons.Default.KeyboardArrowDown, "Bas", RemoteKey.DOWN, press, repeat)
            Spacer(Modifier.size(DPAD_CELL))
        }
    }
    // Volume
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        PadKey(
            Icons.AutoMirrored.Filled.VolumeDown,
            "Volume moins",
            Modifier.weight(1f).height(KEY_HEIGHT),
            onRepeat = { repeat(RemoteKey.VOLUME_DOWN) },
        ) { press(RemoteKey.VOLUME_DOWN, HapticKind.KEY) }
        PadKey(Icons.AutoMirrored.Filled.VolumeOff, "Muet", Modifier.weight(1f).height(KEY_HEIGHT)) {
            press(RemoteKey.MUTE, HapticKind.KEY)
        }
        PadKey(
            Icons.AutoMirrored.Filled.VolumeUp,
            "Volume plus",
            Modifier.weight(1f).height(KEY_HEIGHT),
            onRepeat = { repeat(RemoteKey.VOLUME_UP) },
        ) { press(RemoteKey.VOLUME_UP, HapticKind.KEY) }
    }
    // Lecture
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        PadKey(Icons.Default.SkipPrevious, "Précédent", Modifier.weight(1f).height(KEY_HEIGHT)) {
            press(RemoteKey.PREVIOUS, HapticKind.KEY)
        }
        PlayPauseKey(Modifier.weight(1f).height(KEY_HEIGHT)) { press(RemoteKey.PLAY_PAUSE, HapticKind.KEY) }
        PadKey(Icons.Default.SkipNext, "Suivant", Modifier.weight(1f).height(KEY_HEIGHT)) {
            press(RemoteKey.NEXT, HapticKind.KEY)
        }
    }
}

/** Clavier : le texte tapé ici est envoyé à la télé au fil de la frappe. */
@Composable
private fun KeyboardPanel(
    onText: (String) -> Unit,
    press: (RemoteKey, HapticKind) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Text(
        "Ouvrez un champ de texte sur la télé (recherche…), puis tapez ici : le texte est envoyé au fil de la frappe.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    OutlinedTextField(
        value = text,
        onValueChange = { new ->
            val old = text
            text = new
            when {
                new.startsWith(old) -> onText(new.substring(old.length))
                old.startsWith(new) -> onText("\b".repeat(old.length - new.length))
            }
        },
        label = { Text("Texte à envoyer") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
        keyboardActions =
            KeyboardActions(onSend = {
                onText("\n")
                text = ""
            }),
        modifier = Modifier.fillMaxWidth().focusRequester(focus),
    )
    Button(
        onClick = {
            press(RemoteKey.OK, HapticKind.CONFIRM)
            text = ""
        },
        modifier = Modifier.fillMaxWidth().height(KEY_HEIGHT),
    ) { Text("Valider (OK)") }
}

@Composable
private fun RenameDialog(
    current: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nom de l'appareil") },
        text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true) },
        confirmButton = { TextButton(onClick = { onConfirm(name) }) { Text("Enregistrer") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}

@Composable
private fun DirectionKey(
    icon: ImageVector,
    description: String,
    key: RemoteKey,
    press: (RemoteKey, HapticKind) -> Unit,
    repeat: (RemoteKey) -> Unit,
) {
    RemoteButton(
        label = description,
        onPress = { press(key, HapticKind.KEY) },
        onRepeat = { repeat(key) },
        modifier = Modifier.size(DPAD_CELL),
        shape = RoundedCornerShape(24.dp),
        container = MaterialTheme.colorScheme.secondaryContainer,
        content = MaterialTheme.colorScheme.onSecondaryContainer,
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(36.dp)) }
}

/** Lecture / Pause : une seule touche qui fait les deux, donc les deux symboles côte à côte. */
@Composable
private fun PlayPauseKey(
    modifier: Modifier,
    onPress: () -> Unit,
) {
    RemoteButton(
        label = "Lecture / Pause",
        onPress = onPress,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(28.dp))
            Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(28.dp))
        }
    }
}

/** Touche rectangulaire à icône (retour, volume, lecture…). */
@Composable
private fun PadKey(
    icon: ImageVector,
    description: String,
    modifier: Modifier,
    onRepeat: (() -> Unit)? = null,
    onPress: () -> Unit,
) {
    RemoteButton(
        label = description,
        onPress = onPress,
        onRepeat = onRepeat,
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        container = MaterialTheme.colorScheme.surfaceVariant,
        content = MaterialTheme.colorScheme.onSurfaceVariant,
    ) { Icon(icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
}

/**
 * Touche de télécommande. Elle réagit dès le doigt posé (pas au relâchement) : c'est ce qui donne la sensation d'un
 * clic physique avec le retour haptique, et la touche s'enfonce légèrement. Un appui prolongé répète la commande
 * si [onRepeat] est fourni, sans relancer de vibration ([onPress] n'est appelé qu'une fois par appui).
 */
@Composable
private fun RemoteButton(
    label: String,
    onPress: () -> Unit,
    modifier: Modifier,
    shape: Shape,
    container: Color,
    content: Color,
    onRepeat: (() -> Unit)? = null,
    body: @Composable () -> Unit,
) {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (pressed) PRESSED_SCALE else 1f, label = "keyScale")
    Surface(
        modifier = modifier.scale(scale),
        shape = shape,
        color = if (pressed) container.copy(alpha = PRESSED_ALPHA) else container,
        contentColor = content,
        border = if (pressed) BorderStroke(2.dp, content.copy(alpha = PRESSED_ALPHA)) else null,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .semantics(mergeDescendants = true) {
                        role = Role.Button
                        contentDescription = label
                        onClick(label) {
                            onPress()
                            true
                        }
                    }.pointerInput(onRepeat) {
                        detectTapGestures(
                            onPress = {
                                pressed = true
                                onPress()
                                try {
                                    coroutineScope {
                                        val repeating =
                                            if (onRepeat != null) {
                                                launch {
                                                    delay(REPEAT_DELAY_MILLIS)
                                                    while (isActive) {
                                                        onRepeat()
                                                        delay(REPEAT_INTERVAL_MILLIS)
                                                    }
                                                }
                                            } else {
                                                null
                                            }
                                        tryAwaitRelease()
                                        repeating?.cancel()
                                    }
                                } finally {
                                    pressed = false
                                }
                            },
                        )
                    },
            contentAlignment = Alignment.Center,
        ) { body() }
    }
}

private const val PRESSED_SCALE = 0.94f
private const val PRESSED_ALPHA = 0.8f
