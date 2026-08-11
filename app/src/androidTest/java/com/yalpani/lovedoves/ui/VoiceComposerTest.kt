package com.yalpani.lovedoves.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import com.yalpani.lovedoves.LoveDovesTheme
import org.junit.Rule
import org.junit.Test

class VoiceComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun upwardDragLocksAndExposesPauseAndSend() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(VoiceRecordingMode.IDLE) }
            LoveDovesTheme {
                MessageComposer(
                    text = TextFieldValue(),
                    busy = false,
                    emojiPickerVisible = false,
                    voiceMode = mode,
                    voiceElapsedMillis = 1_800L,
                    focusRequester = remember { FocusRequester() },
                    onTextChange = {},
                    onTextFocus = {},
                    onEmoji = {},
                    onAttachment = {},
                    onSend = {},
                    onVoiceStart = { mode = VoiceRecordingMode.HOLDING },
                    onVoiceCancel = { mode = VoiceRecordingMode.IDLE },
                    onVoiceLock = { mode = VoiceRecordingMode.LOCKED },
                    onVoiceRelease = {},
                    onVoicePauseToggle = {
                        mode = if (mode == VoiceRecordingMode.PAUSED) {
                            VoiceRecordingMode.LOCKED
                        } else {
                            VoiceRecordingMode.PAUSED
                        }
                    },
                    onVoiceSend = { mode = VoiceRecordingMode.IDLE },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen")
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -320f))
                up()
            }

        composeRule.onNodeWithContentDescription("Aufnahme pausieren").assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription("Aufnahme fortsetzen").assertExists()
        composeRule.onNodeWithContentDescription("Sprachnachricht senden").assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen").assertExists()
    }
}
