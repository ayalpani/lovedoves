package com.yalpani.lovedoves.protocol

import com.yalpani.lovedoves.protocol.v1.VideoMessageV1
import org.junit.Assert.assertTrue
import org.junit.Test

class RoundVideoProtocolTest {
    @Test
    fun roundMarkerSurvivesSerialization() {
        val encoded = VideoMessageV1.newBuilder().setRound(true).build().toByteArray()

        assertTrue(VideoMessageV1.parseFrom(encoded).round)
    }
}
