package com.yalpani.lovedoves.ui

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.down
import androidx.compose.ui.test.moveBy
import androidx.compose.ui.test.up
import androidx.compose.ui.platform.testTag
import androidx.test.core.app.ApplicationProvider
import com.yalpani.lovedoves.LoveDovesTheme
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.domain.LoveDovesRepository
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class VoiceComposerTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun keyboardHeightIsPersistedPerOrientation() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        ComposerInputPreferences.persistKeyboardHeightPx(context, 10_001, 812)
        ComposerInputPreferences.persistKeyboardHeightPx(context, 10_002, 421)

        assertEquals(812, ComposerInputPreferences.keyboardHeightPx(context, 10_001))
        assertEquals(421, ComposerInputPreferences.keyboardHeightPx(context, 10_002))
    }

    @Test
    fun outgoingMessageMenuShowsRequestedActions() {
        val replied = AtomicBoolean(false)
        val copied = AtomicBoolean(false)
        val selected = AtomicBoolean(false)
        val message = ConversationEventEntity(
            id = "text-message",
            outgoing = true,
            kind = LoveDovesRepository.KIND_TEXT,
            body = "Hello",
            mediaId = null,
            createdAtEpochMillis = System.currentTimeMillis(),
            deliveryState = LoveDovesRepository.DELIVERY_READ,
        )
        composeRule.setContent {
            LoveDovesTheme {
                Box {
                    MessageActionMenu(
                        expanded = true,
                        message = message,
                        onDismiss = {},
                        onReply = { replied.set(true) },
                        onPin = {},
                        onEdit = {},
                        onCopyText = { copied.set(true) },
                        onDelete = {},
                        onSelect = { selected.set(true) },
                    )
                }
            }
        }

        composeRule.onNodeWithText("Reply").assertExists().performClick()
        composeRule.onNodeWithText("Pin").assertExists()
        composeRule.onNodeWithText("Edit").assertExists()
        composeRule.onNodeWithText("Copy Text").assertExists().performClick()
        composeRule.onNodeWithText("Delete").assertExists()
        composeRule.onNodeWithText("Select").assertExists().performClick()
        composeRule.onNodeWithContentDescription("Gelesen", substring = true)
            .assertExists()
        assertTrue(replied.get())
        assertTrue(copied.get())
        assertTrue(selected.get())
    }

    @Test
    fun roundVideoLongPressSelectsTheMessage() {
        val selected = AtomicBoolean(false)
        composeRule.setContent {
            LoveDovesTheme {
                RoundVideoGestureLayer(
                    onClick = {},
                    onLongPress = { selected.set(true) },
                    modifier = Modifier.size(240.dp).testTag("round video gesture"),
                )
            }
        }

        composeRule.onNodeWithTag("round video gesture").performTouchInput {
            down(center)
            advanceEventTime(1_000L)
            up()
        }

        composeRule.waitForIdle()
        assertTrue(selected.get())
    }

    @Test
    fun pinnedMediaHasAnExplicitBadge() {
        val message = ConversationEventEntity(
            id = "round-video",
            outgoing = true,
            kind = LoveDovesRepository.KIND_ROUND_VIDEO,
            body = null,
            mediaId = "thumbnail",
            createdAtEpochMillis = 0L,
            deliveryState = LoveDovesRepository.DELIVERY_SENT,
            pinned = true,
        )
        composeRule.setContent {
            LoveDovesTheme { PinnedMediaBadge(message) }
        }

        composeRule.onNodeWithContentDescription("Angepinnt").assertExists()
    }

    @Test
    fun quickTapSwitchesFromVoiceToRoundVideo() {
        val started = AtomicBoolean(false)
        val cancelled = AtomicBoolean(false)
        composeRule.setContent {
            var captureType by remember { mutableStateOf(ComposerCaptureType.VOICE) }
            var mode by remember { mutableStateOf(VoiceRecordingMode.IDLE) }
            LoveDovesTheme {
                MessageComposer(
                    text = TextFieldValue(),
                    busy = false,
                    emojiPickerVisible = false,
                    captureType = captureType,
                    voiceMode = mode,
                    voiceElapsedMillis = 0L,
                    focusRequester = remember { FocusRequester() },
                    onTextChange = {},
                    onTextFocus = {},
                    onEmoji = {},
                    onAttachment = {},
                    onSend = {},
                    onCaptureTap = {
                        cancelled.set(true)
                        mode = VoiceRecordingMode.IDLE
                        captureType = if (captureType == ComposerCaptureType.VOICE) {
                            ComposerCaptureType.ROUND_VIDEO
                        } else {
                            ComposerCaptureType.VOICE
                        }
                    },
                    onVoiceStart = {
                        started.set(true)
                        mode = VoiceRecordingMode.HOLDING
                    },
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
        composeRule.onNodeWithText(
            "Hold to record video. Tap to switch to audio.",
        ).assertExists()
        assertFalse(started.get())
        assertTrue(cancelled.get())
    }

    @Test
    fun quickTapSwitchesFromRoundVideoToVoice() {
        composeRule.setContent {
            var captureType by remember { mutableStateOf(ComposerCaptureType.ROUND_VIDEO) }
            var mode by remember { mutableStateOf(VoiceRecordingMode.IDLE) }
            LoveDovesTheme {
                MessageComposer(
                    text = TextFieldValue(),
                    busy = false,
                    emojiPickerVisible = false,
                    captureType = captureType,
                    voiceMode = mode,
                    voiceElapsedMillis = 0L,
                    focusRequester = remember { FocusRequester() },
                    onTextChange = {},
                    onTextFocus = {},
                    onEmoji = {},
                    onAttachment = {},
                    onSend = {},
                    onCaptureTap = {
                        mode = VoiceRecordingMode.IDLE
                        captureType = ComposerCaptureType.VOICE
                    },
                    onVoiceStart = { mode = VoiceRecordingMode.HOLDING },
                    onVoiceCancel = {},
                    onVoiceLock = {},
                    onVoiceRelease = {},
                    onVoicePauseToggle = {},
                    onVoiceSend = {},
                )
            }
        }

        composeRule.onNodeWithContentDescription("Rundes Video aufnehmen")
            .performTouchInput {
                down(center)
                advanceEventTime(200L)
                up()
            }
        composeRule.onNodeWithText(
            "Hold to record audio. Tap to switch to video.",
        ).assertExists()
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
        val idleInputBounds = composeRule.onNodeWithTag(COMPOSER_INPUT_TAG)
            .fetchSemanticsNode().boundsInRoot

        val startPadding = idleInputBounds.left - idleHostBounds.left
        assertEquals(startPadding, idleActionBounds.left - idleInputBounds.right, 1f)
        assertEquals(startPadding, idleHostBounds.right - idleActionBounds.right, 1f)

        composeRule.onNodeWithContentDescription("Sprachnachricht aufnehmen")
            .performTouchInput {
                down(center)
                advanceEventTime(501L)
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
