package com.yalpani.lovedoves.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.yalpani.lovedoves.LoveDovesTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ExplicitPhotoCoverTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun asksForIntentionalRevealBeforeShowingPhoto() {
        var revealed = false
        compose.setContent {
            LoveDovesTheme {
                Box(Modifier.size(260.dp)) {
                    ExplicitPhotoCover(
                        screeningUnavailable = false,
                        onReveal = { revealed = true },
                    )
                }
            }
        }

        compose.onNodeWithText("Möglicherweise intimes Foto").assertExists()
        compose.onNodeWithText("Tippen, um es anzuzeigen").performClick()
        compose.runOnIdle { assertTrue(revealed) }
    }
}
