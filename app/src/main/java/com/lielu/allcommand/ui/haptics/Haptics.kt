package com.lielu.allcommand.ui.haptics

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import com.lielu.allcommand.data.settings.HapticIntensity
import com.lielu.allcommand.data.settings.HapticSettings

/** Nature d'une interaction : un appui ordinaire ou une validation, ressentie plus nettement. */
enum class HapticKind {
    KEY,
    CONFIRM,
}

/**
 * Retour haptique des touches, via `View.performHapticFeedback` : l'API prévue pour les interactions, qui ne
 * demande aucune permission et respecte d'elle-même le réglage système « vibration au toucher » (on ne force
 * jamais les vibrations). Le système joue un seul clic par appel : pas de double vibration.
 */
class Haptics(
    private val view: View,
    private val settings: HapticSettings,
) {
    fun perform(kind: HapticKind) {
        if (!settings.enabled) return
        view.performHapticFeedback(constantFor(kind, settings.intensity, Build.VERSION.SDK_INT))
    }

    companion object {
        /**
         * Android n'expose pas l'amplitude d'un clic : l'intensité choisit un clic plus ou moins marqué. Les
         * constantes récentes (API 30) cèdent la place à leur équivalent le plus proche sur les versions antérieures.
         */
        fun constantFor(
            kind: HapticKind,
            intensity: HapticIntensity,
            sdk: Int,
        ): Int =
            when (intensity) {
                HapticIntensity.LOW ->
                    if (kind ==
                        HapticKind.KEY
                    ) {
                        HapticFeedbackConstants.CLOCK_TICK
                    } else {
                        HapticFeedbackConstants.VIRTUAL_KEY
                    }
                HapticIntensity.MEDIUM ->
                    when {
                        kind == HapticKind.KEY -> HapticFeedbackConstants.VIRTUAL_KEY
                        sdk >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
                        else -> HapticFeedbackConstants.CONTEXT_CLICK
                    }
                HapticIntensity.HIGH ->
                    when {
                        kind == HapticKind.CONFIRM -> HapticFeedbackConstants.LONG_PRESS
                        sdk >= Build.VERSION_CODES.R -> HapticFeedbackConstants.CONFIRM
                        else -> HapticFeedbackConstants.KEYBOARD_TAP
                    }
            }

        /** Faux si l'utilisateur a coupé « vibration au toucher » dans les réglages Android : rien ne vibrera alors. */
        fun systemEnabled(context: Context): Boolean =
            Settings.System.getInt(context.contentResolver, Settings.System.HAPTIC_FEEDBACK_ENABLED, 1) != 0
    }
}

/** Retour haptique lié à la vue courante et aux réglages de l'utilisateur. */
@Composable
fun rememberHaptics(settings: HapticSettings): Haptics {
    val view = LocalView.current
    return remember(view, settings) { Haptics(view, settings) }
}
