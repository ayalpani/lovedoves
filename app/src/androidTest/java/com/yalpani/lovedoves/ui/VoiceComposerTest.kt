package com.yalpani.lovedoves.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.platform.testTag
import com.yalpani.lovedoves.LoveDovesTheme
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VoiceComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun quickTapSwitchesFromVoiceToRoundVideo() {
        composeRule.setContent {
            var captureType by remember { mutableStateOf(ComposerCaptureType.VOICE) }
            LoveDovesTheme {
                MessageComposer(
                    text = TextFieldValue(),
                    busy = false,
                    emojiPickerVisible = false,
                    captureType = captureType,
                    voiceMode = VoiceRecordingMode.IDLE,
                    voiceElapsedMillis = 0L,
                    focusRequester = remember { FocusRequester() },
                    onTextChange = {},
                    onTextFocus = {},
                    onEmoji = {},
                    onAttachment = {},
                    onSend = {},
                    onCaptureTap = { captureType = ComposerCaptureType.ROUND_VIDEO },
                    onVoiceStart = {},
                    onVoiceCancel = {},
                    onVoiceLock = {},
                    onVoiceRelease = {},
                    onVoicePauseToggle = {},
                    onVoiceSend = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen")
            .performTouchInput {
                down(center)
                advanceEventTime(200L)
                up()
            }

        composeRule.onNodeWithContentDescription("Rundes Video aufnehmen").assertExists()
    }

    @Test
    fun upwardDragLocksAndExposesPauseAndSend() {
        composeRule.setContent {
            var mode by remember { mutableStateOf(VoiceRecordingMode.IDLE) }
            LoveDovesTheme {
                Box(Modifier.testTag("composer host")) {
                    MessageComposer(
                        text = TextFieldValue(),
                        busy = false,
                        emojiPickerVisible = false,
                        captureType = ComposerCaptureType.VOICE,
                        voiceMode = mode,
                        voiceElapsedMillis = 1_800L,
                        focusRequester = remember { FocusRequester() },
                        onTextChange = {},
                        onTextFocus = {},
                        onEmoji = {},
                        onAttachment = {},
                        onSend = {},
                        onCaptureTap = {},
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
        }

        val idleHostBounds = composeRule.onNodeWithTag("composer host")
            .fetchSemanticsNode().boundsInRoot
        val idleActionBounds = composeRule
            .onNodeWithContentDescription("Sprachnachricht aufnehmen")
            .fetchSemanticsNode().boundsInRoot

        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen")
            .performTouchInput {
                down(center)
                advanceEventTime(1_001L)
                moveBy(Offset(0f, -320f))
                up()
            }

        val pauseAction = composeRule.onNodeWithContentDescription("Aufnahme pausieren")
        pauseAction.assertExists()
        val lockedHostBounds = composeRule.onNodeWithTag("composer host")
            .fetchSemanticsNode().boundsInRoot
        val lockedActionBounds = composeRule
            .onNodeWithContentDescription("Sprachnachricht senden")
            .fetchSemanticsNode().boundsInRoot
        assertEquals(idleHostBounds.height, lockedHostBounds.height, 0f)
        assertEquals(idleActionBounds.width, lockedActionBounds.width, 0f)
        assertEquals(idleActionBounds.height, lockedActionBounds.height, 0f)
        pauseAction.performClick()
        composeRule.onNodeWithContentDescription("Aufnahme fortsetzen").assertExists()
        composeRule.onNodeWithContentDescription("Sprachnachricht senden").assertExists()
            .performClick()
        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen").assertExists()
    }

    @Test
    fun shortHorizontalDragDoesNotCancel() {
        val cancelled = AtomicBoolean(false)
        composeRule.setContent {
            var mode by remember { mutableStateOf(VoiceRecordingMode.IDLE) }
            LoveDovesTheme {
                MessageComposer(
                    text = TextFieldValue(),
                    busy = false,
                    emojiPickerVisible = false,
                    captureType = ComposerCaptureType.VOICE,
                    voiceMode = mode,
                    voiceElapsedMillis = 1_800L,
                    focusRequester = remember { FocusRequester() },
                    onTextChange = {},
                    onTextFocus = {},
                    onEmoji = {},
                    onAttachment = {},
                    onSend = {},
                    onCaptureTap = {},
                    onVoiceStart = { mode = VoiceRecordingMode.HOLDING },
                    onVoiceCancel = {
                        cancelled.set(true)
                        mode = VoiceRecordingMode.IDLE
                    },
                    onVoiceLock = { mode = VoiceRecordingMode.LOCKED },
                    onVoiceRelease = { mode = VoiceRecordingMode.IDLE },
                    onVoicePauseToggle = {},
                    onVoiceSend = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen")
            .performTouchInput {
                down(center)
                moveBy(Offset(-80f, 0f))
                up()
            }

        composeRule.waitForIdle()
        assertFalse(cancelled.get())
        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen").assertExists()
    }
}
