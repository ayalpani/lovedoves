package com.yalpani.lovedoves.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.yalpani.lovedoves.LoveInk

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
                LocalContentColor.current.copy(alpha = 0.72f)
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
        border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.3f)),
    ) {
        icon?.invoke()
        if (icon != null) Spacer(Modifier.width(10.dp))
        Text(
            label,
            color = if (icon == null) LocalContentColor.current else {
                LocalContentColor.current.copy(alpha = 0.72f)
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
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
        listOf("M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78L12 21.23l8.84-8.84a5.5 5.5 0 0 0 0-7.78"),
        description,
        modifier,
    )

@Composable
internal fun LockIcon(description: String? = null) = LoveIcon(
    listOf("M7 11V7a5 5 0 0 1 10 0v4", "M5 11h14v10H5z", "M12 15v2"),
    description,
)

@Composable
internal fun CameraIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
) = LoveIcon(
    listOf("M14.5 4H9.5L8 7H4v13h16V7h-4z", "M15.5 13.5a3.5 3.5 0 1 1-7 0 3.5 3.5 0 1 1 7 0"),
    description,
    modifier,
)

@Composable
internal fun ImageIcon(description: String? = null) = LoveIcon(
    listOf("M3 3h18v18H3z", "M8.5 10a1.5 1.5 0 1 1 0-3 1.5 1.5 0 1 1 0 3", "m21 15-5-5L5 21"),
    description,
)

@Composable
internal fun SendIcon(
    description: String? = null,
    modifier: Modifier = Modifier.size(24.dp),
    color: Color = LocalContentColor.current,
) = LoveIcon(
    listOf("m22 2-7 20-4-9-9-4z", "M22 2 11 13"),
    description,
    modifier,
    color,
)

@Composable
internal fun SettingsIcon(description: String? = null) = LoveIcon(
    listOf("M12 15.5a3.5 3.5 0 1 1 0-7 3.5 3.5 0 1 1 0 7", "M19.4 15a1.7 1.7 0 0 0 .34 1.88l.06.06-2.12 3.67-.08-.02a1.7 1.7 0 0 0-1.8-.44l-.08.03a1.7 1.7 0 0 0-1.03 1.44V22h-4.24v-.1a1.7 1.7 0 0 0-1.03-1.55l-.08-.03a1.7 1.7 0 0 0-1.8.44l-.08.02-2.12-3.67.06-.06A1.7 1.7 0 0 0 4.6 15l-.08-.06a1.7 1.7 0 0 0-1.64-.16l-.09.04-2.12-3.67.08-.06a1.7 1.7 0 0 0 1.03-1.44v-.1a1.7 1.7 0 0 0-1.03-1.44l-.08-.06L2.79 4.38l.09.04a1.7 1.7 0 0 0 1.64-.16l.08-.06a1.7 1.7 0 0 0 .34-1.88l-.06-.06L7 0.59l.08.02a1.7 1.7 0 0 0 1.8.44l.08-.03A1.7 1.7 0 0 0 10 0h4a1.7 1.7 0 0 0 1.03 1.44l.08.03a1.7 1.7 0 0 0 1.8-.44l.08-.02 2.12 3.67-.06.06a1.7 1.7 0 0 0 .34 1.88l.08.06a1.7 1.7 0 0 0 1.64.16l.09-.04 2.12 3.67-.08.06a1.7 1.7 0 0 0-1.03 1.44v.1a1.7 1.7 0 0 0 1.03 1.44l.08.06-2.12 3.67-.09-.04a1.7 1.7 0 0 0-1.64.16z"),
    description,
)

@Composable
internal fun QrIcon(description: String? = null) = LoveIcon(
    listOf("M3 3h7v7H3z", "M14 3h7v7h-7z", "M3 14h7v7H3z", "M14 14h3v3h-3z", "M19 14h2v2", "M19 19h2v2", "M14 19h2v2"),
    description,
)

@Composable
internal fun RefreshIcon(description: String? = null) = LoveIcon(
    listOf("M20 6v5h-5", "M4 18v-5h5", "M18.5 9a7 7 0 0 0-12-2L4 11", "M5.5 15a7 7 0 0 0 12 2l2.5-4"),
    description,
)

@Composable
internal fun ChevronLeftIcon(description: String? = null) = LoveIcon(
    listOf("m15 18-6-6 6-6"),
    description,
)

@Composable
internal fun ChevronRightIcon(description: String? = null) = LoveIcon(
    listOf("m9 18 6-6-6-6"),
    description,
)

@Composable
internal fun CloseIcon(description: String? = null) = LoveIcon(
    listOf("M18 6 6 18", "m6 6 12 12"),
    description,
)
