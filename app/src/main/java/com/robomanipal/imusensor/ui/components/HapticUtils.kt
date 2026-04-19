package com.robomanipal.imusensor.ui.components

import android.os.Build
import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalView

/**
 * Haptic feedback utilities — uses the View-level API for broader
 * constant support than the Compose HapticFeedback interface.
 *
 * Three intensity levels:
 *   • tick   — light click for tab switches, chip selections, small toggles
 *   • click  — medium confirmation for buttons, exports, radio selections
 *   • heavy  — heavy thud for start/stop streaming, major state changes
 */
object HapticUtils {

    /** Light tick — tab switch, chip tap, minor interaction. */
    fun tick(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
    }

    /** Medium click — button press, export, confirm. */
    fun click(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }
    }

    /** Heavy thud — start/stop streaming, critical state change. */
    fun heavy(view: View) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            view.performHapticFeedback(HapticFeedbackConstants.REJECT)
        } else {
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        }
    }
}
