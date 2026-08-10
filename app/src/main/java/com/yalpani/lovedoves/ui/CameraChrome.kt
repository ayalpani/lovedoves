@file:Suppress("DEPRECATION")

package com.yalpani.lovedoves.ui

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.view.Surface
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

// Ported from Spur's CameraChrome. Love Doves keeps its capture pipeline memory-only.
private val CameraChrome = Color.Black.copy(alpha = 0.42f)

@Composable
internal fun CameraOrientation(): Boolean {
    val activity = LocalContext.current.findComponentActivity()
    DisposableEffect(activity) {
        val previousOrientation = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
        onDispose {
            if (previousOrientation != null) activity.requestedOrientation = previousOrientation
        }
    }
    return LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
}

@Composable
internal fun CameraSystemBars() {
    val activity = LocalContext.current.findComponentActivity()
    DisposableEffect(activity) {
        val window = activity?.window
        if (activity == null || window == null) return@DisposableEffect onDispose { }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        val previousStatusColor = window.statusBarColor
        val previousNavigationColor = window.navigationBarColor
        val previousLightStatus = controller.isAppearanceLightStatusBars
        val previousLightNavigation = controller.isAppearanceLightNavigationBars
        val previousNavigationContrast = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced
        } else {
            null
        }

        activity.enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.Transparent.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(Color.Black.toArgb()),
        )
        window.navigationBarColor = Color.Black.toArgb()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        onDispose {
            window.statusBarColor = previousStatusColor
            window.navigationBarColor = previousNavigationColor
            previousNavigationContrast?.let { window.isNavigationBarContrastEnforced = it }
            controller.isAppearanceLightStatusBars = previousLightStatus
            controller.isAppearanceLightNavigationBars = previousLightNavigation
        }
    }
}

internal fun cameraTargetRotation(displayRotation: Int?): Int = when (displayRotation) {
    Surface.ROTATION_0,
    Surface.ROTATION_90,
    Surface.ROTATION_180,
    Surface.ROTATION_270,
    -> displayRotation
    else -> Surface.ROTATION_0
}

@Composable
internal fun BoxScope.CameraCloseButton(
    contentDescription: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.TopStart)
            .statusBarsPadding()
            .padding(18.dp)
            .size(52.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome,
            contentColor = Color.White,
        ),
    ) {
        CloseIcon()
    }
}

@Composable
internal fun BoxScope.CameraSwitchButton(
    contentDescription: String,
    landscape: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 26.dp, bottom = if (landscape) 18.dp else 25.dp)
            .size(58.dp)
            .semantics { this.contentDescription = contentDescription },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome,
            contentColor = Color.White,
        ),
    ) {
        LoveIcon(
            paths = listOf(
                "M11 19H4a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h5",
                "M13 5h7a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-5",
                "M15 12a3 3 0 1 1-6 0 3 3 0 1 1 6 0",
                "m18 22-3-3 3-3",
                "m6 2 3 3-3 3",
            ),
            contentDescription = null,
            modifier = Modifier.size(28.dp),
            strokeWidth = 2.5f,
        )
    }
}

@Composable
internal fun BoxScope.CameraCaptureButton(
    enabled: Boolean,
    contentDescription: String,
    landscape: Boolean,
    shape: Shape = CircleShape,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .align(if (landscape) Alignment.CenterEnd else Alignment.BottomCenter)
            .navigationBarsPadding()
            .padding(
                end = if (landscape) 18.dp else 0.dp,
                bottom = if (landscape) 0.dp else 18.dp,
            )
            .size(78.dp)
            .border(4.dp, Color.White, CircleShape)
            .padding(7.dp)
            .background(Color.White, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics {
                this.contentDescription = contentDescription
                role = Role.Button
            },
    )
}

@Composable
internal fun BoxScope.CameraRotateButton(
    landscape: Boolean,
    onClick: () -> Unit,
) {
    val systemInset = if (landscape) Modifier.navigationBarsPadding() else Modifier
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .align(Alignment.BottomStart)
            .then(systemInset)
            .padding(18.dp)
            .size(52.dp)
            .semantics { contentDescription = "Foto 90 Grad nach links drehen" },
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = CameraChrome,
            contentColor = Color.White,
        ),
    ) {
        LoveIcon(
            paths = listOf(
                "M20 9V7a2 2 0 0 0-2-2h-6",
                "m15 2-3 3 3 3",
                "M20 13v5a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2h2",
            ),
            contentDescription = null,
        )
    }
}

private tailrec fun Context.findComponentActivity(): ComponentActivity? = when (this) {
    is ComponentActivity -> this
    is ContextWrapper -> baseContext.findComponentActivity()
    else -> null
}
