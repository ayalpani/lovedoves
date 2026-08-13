package com.yalpani.lovedoves.protocol

import com.google.protobuf.ByteString
import com.yalpani.lovedoves.protocol.v1.ConversationEventV1
import com.yalpani.lovedoves.protocol.v1.EncryptedMediaV1
import com.yalpani.lovedoves.protocol.v1.VoiceMessageV1
import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceMessageProtocolTest {
    @Test
    fun voiceMessageRoundTripsThroughConversationEvent() {
        val audio = EncryptedMediaV1.newBuilder()
            .setMediaId("0d5f3ca1-5248-4a33-9628-bf507cc8ceeb")
            .setMimeType("audio/mp4")
            .setEncryptedSize(4_096)
            .setMediaKey(ByteString.copyFrom(ByteArray(32) { it.toByte() }))
            .setMediaNonce(ByteString.copyFrom(ByteArray(12) { (it + 1).toByte() }))
            .setSha256(ByteString.copyFrom(ByteArray(32) { (it + 2).toByte() }))
            .build()
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId("voice-event")
            .setCreatedAtEpochMs(123_456L)
            .setVoice(
                VoiceMessageV1.newBuilder()
                    .setAudio(audio)
                    .setDurationMs(8_750L),
            )
            .build()

        val decoded = ConversationEventV1.parseFrom(event.toByteArray())

        assertEquals(ConversationEventV1.PayloadCase.VOICE, decoded.payloadCase)
        assertEquals("audio/mp4", decoded.voice.audio.mimeType)
        assertEquals(8_750L, decoded.voice.durationMs)
        assertEquals(audio.sha256, decoded.voice.audio.sha256)
    }
}
