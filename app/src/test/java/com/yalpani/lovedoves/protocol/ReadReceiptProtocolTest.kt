package com.yalpani.lovedoves.protocol

import com.yalpani.lovedoves.protocol.v1.ConversationEventV1
import com.yalpani.lovedoves.protocol.v1.DeliveryReceiptV1
import com.yalpani.lovedoves.protocol.v1.ReadReceiptV1
import org.junit.Assert.assertEquals
import org.junit.Test

class ReadReceiptProtocolTest {
    @Test
    fun readReceiptRoundTripsAsVersionedEncryptedPayload() {
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId("receipt-event")
            .setCreatedAtEpochMs(1234L)
            .setDeliveryReceipt(
                DeliveryReceiptV1.newBuilder()
                    .setMessageId("message-one")
                    .setStoredAtEpochMs(1200L)
                    .setRead(true)
                    .addAllMessageIds(listOf("message-one", "message-two")),
            )
            .build()

        val decoded = ConversationEventV1.parseFrom(event.toByteArray())

        assertEquals(ConversationEventV1.PayloadCase.DELIVERY_RECEIPT, decoded.payloadCase)
        assertEquals(listOf("message-one", "message-two"), decoded.deliveryReceipt.messageIdsList)
        assertEquals(1200L, decoded.deliveryReceipt.storedAtEpochMs)
        assertEquals(true, decoded.deliveryReceipt.read)
    }

    @Test
    @Suppress("DEPRECATION")
    fun preReleaseReadReceiptRemainsDecodable() {
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId("legacy-receipt")
            .setCreatedAtEpochMs(1234L)
            .setReadReceipt(
                ReadReceiptV1.newBuilder()
                    .addMessageIds("message-one")
                    .setReadAtEpochMs(1200L),
            )
            .build()

        val decoded = ConversationEventV1.parseFrom(event.toByteArray())

        assertEquals(ConversationEventV1.PayloadCase.READ_RECEIPT, decoded.payloadCase)
        assertEquals(listOf("message-one"), decoded.readReceipt.messageIdsList)
    }
}
