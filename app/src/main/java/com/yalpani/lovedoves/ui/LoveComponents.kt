package com.yalpani.lovedoves.ui

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.yalpani.lovedoves.LoveInk

private const val IconTextLabelAlpha = 0.68f

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun LoveModalBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = Color.White,
    ) {
        LightSheetNavigationBar()
        content()
    }
}

@Composable
@Suppress("DEPRECATION")
private fun LightSheetNavigationBar() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val previousColor = window?.navigationBarColor
        val previousContrastEnforced = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced
        } else {
            null
        }
        val insetsController = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        val previousLightIcons = insetsController?.isAppearanceLightNavigationBars

        window?.navigationBarColor = Color.White.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window?.isNavigationBarContrastEnforced = false
        }
        insetsController?.isAppearanceLightNavigationBars = Color.White.luminance() > 0.5f

        onDispose {
            if (previousColor != null) window.navigationBarColor = previousColor
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && previousContrastEnforced != null) {
                window?.isNavigationBarContrastEnforced = previousContrastEnforced
            }
            if (previousLightIcons != null) {
                insetsController?.isAppearanceLightNavigationBars = previousLightIcons
            }
        }
    }
}

@Composable
internal fun LovePrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    destructive: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = CircleShape,
        colors = if (destructive) {
            ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            )
        } else {
            ButtonDefaults.buttonColors()
        },
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (icon == null) LocalContentColor.current else {
                LocalContentColor.current.copy(alpha = IconTextLabelAlpha)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun LoveSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(60.dp),
        shape = CircleShape,
        border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.5f)),
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (icon == null) LocalContentColor.current else {
                LocalContentColor.current.copy(alpha = IconTextLabelAlpha)
            },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun LoveIcon(
    paths: List<String>,
    contentDescription: String?,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
    strokeWidth: Float = 2f,
) {
    val parsed = remember(paths) { paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(
        modifier = if (contentDescription == null) modifier else {
            modifier.semantics { this.contentDescription = contentDescription }
        },
    ) {
        val scale = minOf(size.width, size.height) / 24f
        val left = (size.width - 24f * scale) / 2f
        val top = (size.height - 24f * scale) / 2f
        with(drawContext.canvas) {
            save()
            translate(left, top)
            scale(scale, scale)
            parsed.forEach { path ->
                drawPath(
                    path,
                    color,
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
            restore()
        }
    }
}

@Composable
internal fun HeartIcon(description: String? = null, modifier: Modifier = Modifier.size(24.dp)) =
    LoveIcon(
        listOf("M2 9.5a5.5 5.5 0 0 1 9.591-3.676.56.56 0 0 0 .818 0A5.49 5.49 0 0 1 22 9.5c0 2.29-1.5 4-3 5.5l-5.492 5.313a2 2 0 0 1-3 .019L5 15c-1.5-1.5-3-3.2-3-5.5"),
        description,
        modifier,
    )

@Composable
internal fun LockIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf(
        "M13 16A1 1 0 0 1 12 17 1 1 0 0 1 11 16 1 1 0 0 1 13 16Z",
        "M5 10H19A2 2 0 0 1 21 12V20A2 2 0 0 1 19 22H5A2 2 0 0 1 3 20V12A2 2 0 0 1 5 10Z",
        "M7 10V7a5 5 0 0 1 10 0v3",
    ),
    description,
    modifier,
    color,
)

@Composable
internal fun MicrophoneIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf(
        "M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z",
        "M19 10v2a7 7 0 0 1-14 0v-2",
        "M12 19v3",
        "M8 22h8",
    ),
    description,
    modifier,
    color,
)

@Composable
internal fun DeliveryCheckIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf("m5 12 4 4L19 6"),
    description,
    modifier,
    color,
)

@Composable
internal fun DeliveryCheckCheckIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf("M18 6 7 17l-5-5", "m22 10-7.5 7.5L13 16"),
    description,
    modifier,
    color,
)

@Composable
internal fun DeliveryClockIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf("M12 6v6l4 2", "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0"),
    description,
    modifier,
    color,
)

@Composable
internal fun DeliveryErrorIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf("M12 8v4", "M12 16h.01", "M22 12a10 10 0 1 1-20 0 10 10 0 1 1 20 0"),
    description,
    modifier,
    color,
)

@Composable
internal fun CameraIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "M13.997 4a2 2 0 0 1 1.76 1.05l.486.9A2 2 0 0 0 18.003 7H20a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2h1.997a2 2 0 0 0 1.759-1.048l.489-.904A2 2 0 0 1 10.004 4Z",
        "M15 13A3 3 0 0 1 12 16 3 3 0 0 1 9 13 3 3 0 0 1 15 13Z",
    ),
    description,
    modifier,
)

@Composable
internal fun ImageIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
        "M11 9A2 2 0 0 1 9 11 2 2 0 0 1 7 9 2 2 0 0 1 11 9Z",
        "m21 15-3.086-3.086a2 2 0 0 0-2.828 0L6 21",
    ),
    description,
    modifier,
)

@Composable
internal fun SmileIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "M8 14s1.5 2 4 2 4-2 4-2",
        "M9 9h.01",
        "M15 9h.01",
        "M21 12a9 9 0 1 1-18 0 9 9 0 1 1 18 0",
    ),
    description,
    modifier,
)

@Composable
internal fun KeyboardIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "M10 8h.01",
        "M12 12h.01",
        "M14 8h.01",
        "M16 12h.01",
        "M18 8h.01",
        "M6 8h.01",
        "M7 16h10",
        "M8 12h.01",
        "M22 6v12a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2h16a2 2 0 0 1 2 2",
    ),
    description,
    modifier,
)

@Composable
internal fun SearchIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "m21 21-4.34-4.34",
        "M19 11a8 8 0 1 1-16 0 8 8 0 1 1 16 0",
    ),
    description,
    modifier,
)

@Composable
internal fun PaperclipIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "m16 6-8.414 8.586a2 2 0 0 0 2.829 2.829L18.828 9a4 4 0 1 0-5.657-5.657L4.757 11.757a6 6 0 1 0 8.486 8.486L21.657 11.83",
    ),
    description,
    modifier,
)

@Composable
internal fun VideoIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "m16 13 5.223 3.482a.5.5 0 0 0 .777-.416V7.87a.5.5 0 0 0-.752-.432L16 10.5",
        "M4 6h10a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2",
    ),
    description,
    modifier,
)

@Composable
internal fun PlayIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    paths = listOf("m6 3 14 9-14 9z"),
    contentDescription = description,
    modifier = modifier,
    color = color,
)

@Composable
internal fun PauseIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    paths = listOf("M8 5v14", "M16 5v14"),
    contentDescription = description,
    modifier = modifier,
    color = color,
)

@Composable
internal fun SendIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf(
        "M14.536 21.686a.5.5 0 0 0 .937-.024l6.5-19a.496.496 0 0 0-.635-.635l-19 6.5a.5.5 0 0 0-.024.937l7.93 3.18a2 2 0 0 1 1.112 1.11Z",
        "m21.854 2.147-10.94 10.939",
    ),
    description,
    modifier,
    color,
)

@Composable
internal fun SettingsIcon(description: String? = null) = LoveIcon(
    listOf(
        "M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915",
        "M15 12A3 3 0 0 1 12 15 3 3 0 0 1 9 12 3 3 0 0 1 15 12Z",
    ),
    description,
)

@Composable
internal fun QrIcon(description: String? = null) = LoveIcon(
    listOf(
        "M4 3H7A1 1 0 0 1 8 4V7A1 1 0 0 1 7 8H4A1 1 0 0 1 3 7V4A1 1 0 0 1 4 3Z",
        "M17 3H20A1 1 0 0 1 21 4V7A1 1 0 0 1 20 8H17A1 1 0 0 1 16 7V4A1 1 0 0 1 17 3Z",
        "M4 16H7A1 1 0 0 1 8 17V20A1 1 0 0 1 7 21H4A1 1 0 0 1 3 20V17A1 1 0 0 1 4 16Z",
        "M21 16h-3a2 2 0 0 0-2 2v3",
        "M21 21v.01",
        "M12 7v3a2 2 0 0 1-2 2H7",
        "M3 12h.01",
        "M12 3h.01",
        "M12 16v.01",
        "M16 12h1",
        "M21 12v.01",
        "M12 21v-1",
    ),
    description,
)

@Composable
internal fun RefreshIcon(description: String? = null) = LoveIcon(
    listOf(
        "M3 12a9 9 0 0 1 9-9 9.75 9.75 0 0 1 6.74 2.74L21 8",
        "M21 3v5h-5",
        "M21 12a9 9 0 0 1-9 9 9.75 9.75 0 0 1-6.74-2.74L3 16",
        "M8 16H3v5",
    ),
    description,
)

@Composable
internal fun ChevronLeftIcon(description: String? = null) = LoveIcon(
    listOf("m15 18-6-6 6-6"),
    description,
    strokeWidth = 3f,
)

@Composable
internal fun ChevronRightIcon(description: String? = null) = LoveIcon(
    listOf("m9 18 6-6-6-6"),
    description,
    modifier = Modifier.size(20.dp),
)

@Composable
internal fun DeleteIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf(
        "M10 11v6",
        "M14 11v6",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6",
        "M3 6h18",
        "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
    ),
    description,
    modifier,
    color,
)

@Composable
internal fun CopyIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf(
        "M8 8h11a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2",
        "M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h1",
    ),
    description,
    modifier,
)

@Composable
internal fun SelectionCheckIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf("m5 12 4 4L19 6"),
    description,
    modifier,
    strokeWidth = 2.5f,
)

@Composable
internal fun CloseIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf("M18 6 6 18", "m6 6 12 12"),
    description,
    modifier,
)
