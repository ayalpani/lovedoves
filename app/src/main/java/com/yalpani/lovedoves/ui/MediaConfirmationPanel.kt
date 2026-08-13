package com.yalpani.lovedoves.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// Kept in sync with Spur's AnimatedMediaConfirmationPanel.
@Composable
internal fun AnimatedMediaConfirmationPanel(
    landscape: Boolean,
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
    acceptLabel: String = "Foto verwenden",
    discardLabel: String = "Nochmal",
    metadata: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val visibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    AnimatedVisibility(
        visibleState = visibility,
        enter = if (landscape) {
            expandHorizontally(
                animationSpec = tween(durationMillis = 420),
                expandFrom = Alignment.End,
            ) + slideInHorizontally(
                animationSpec = tween(durationMillis = 420),
                initialOffsetX = { it },
            )
        } else {
            expandVertically(
                animationSpec = tween(durationMillis = 420),
                expandFrom = Alignment.Bottom,
            ) + slideInVertically(
                animationSpec = tween(durationMillis = 420),
                initialOffsetY = { it },
            )
        },
    ) {
        val panelModifier = if (landscape) {
            Modifier.width(260.dp).fillMaxHeight()
        } else {
            Modifier.fillMaxWidth()
        }
        Column(panelModifier.navigationBarsPadding()) {
            Surface(
                modifier = if (landscape) Modifier.fillMaxHeight() else Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                color = Color.White,
                shadowElevation = 16.dp,
            ) {
                Column(
                    modifier = (if (landscape) {
                        Modifier.fillMaxWidth().fillMaxHeight()
                    } else {
                        Modifier.fillMaxWidth()
                    }).padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(
                        space = 12.dp,
                        alignment = if (landscape) Alignment.CenterVertically else Alignment.Top,
                    ),
                ) {
                    metadata?.invoke(this)
                    LovePrimaryButton(acceptLabel, onClick = onAccept)
                    LoveSecondaryButton(discardLabel, onClick = onDiscard)
                }
            }
        }
    }
}
