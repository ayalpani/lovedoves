package com.yalpani.lovedoves.ui

import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.domain.LoveDovesRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MessagePresentationTest {
    @Test
    fun voicePlaybackContinuesToTheImmediatelyFollowingVoiceFromTheSameSender() {
        val messages = listOf(
            message("first", outgoing = true, kind = LoveDovesRepository.KIND_VOICE),
            message("second", outgoing = true, kind = LoveDovesRepository.KIND_VOICE),
        )

        assertEquals("second", nextVoiceMessageIdInStreak(messages, "first"))
    }

    @Test
    fun voicePlaybackStreakStopsAtAnotherSenderOrMessageKind() {
        val senderChanges = listOf(
            message("first", outgoing = true, kind = LoveDovesRepository.KIND_VOICE),
            message("second", outgoing = false, kind = LoveDovesRepository.KIND_VOICE),
        )
        val kindChanges = listOf(
            message("first", outgoing = true, kind = LoveDovesRepository.KIND_VOICE),
            message("second", outgoing = true, kind = LoveDovesRepository.KIND_TEXT),
            message("third", outgoing = true, kind = LoveDovesRepository.KIND_VOICE),
        )

        assertNull(nextVoiceMessageIdInStreak(senderChanges, "first"))
        assertNull(nextVoiceMessageIdInStreak(kindChanges, "first"))
        assertNull(nextVoiceMessageIdInStreak(kindChanges, "third"))
        assertNull(nextVoiceMessageIdInStreak(kindChanges, "missing"))
    }

    private fun message(id: String, outgoing: Boolean, kind: String) = ConversationEventEntity(
        id = id,
        outgoing = outgoing,
        kind = kind,
        body = null,
        mediaId = if (kind == LoveDovesRepository.KIND_VOICE) "media-$id" else null,
        createdAtEpochMillis = id.hashCode().toLong(),
        deliveryState = LoveDovesRepository.DELIVERY_READ,
    )
}
