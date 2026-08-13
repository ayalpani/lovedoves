package com.yalpani.lovedoves.protocol

import com.yalpani.lovedoves.protocol.v1.ConversationEventV1
import com.yalpani.lovedoves.protocol.v1.MessageMutationV1
import com.yalpani.lovedoves.protocol.v1.TextMessageV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageInteractionProtocolTest {
    @Test
    fun replyMetadataRoundTripsWithMessage() {
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId("message-two")
            .setCreatedAtEpochMs(2L)
            .setReplyToEventId("message-one")
            .setText(TextMessageV1.newBuilder().setText("reply"))
            .build()

        val decoded = ConversationEventV1.parseFrom(event.toByteArray())

        assertEquals("message-one", decoded.replyToEventId)
        assertEquals("reply", decoded.text.text)
    }

    @Test
    fun editAndPinMutationsRoundTrip() {
        val edit = mutation(MessageMutationV1.Action.EDIT, "updated")
        val pin = mutation(MessageMutationV1.Action.PIN)

        assertEquals(MessageMutationV1.Action.EDIT, edit.messageMutation.action)
        assertEquals("updated", edit.messageMutation.text)
        assertEquals(MessageMutationV1.Action.PIN, pin.messageMutation.action)
        assertTrue(pin.messageMutation.text.isEmpty())
    }

    private fun mutation(
        action: MessageMutationV1.Action,
        text: String = "",
    ): ConversationEventV1 = ConversationEventV1.parseFrom(
        ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId("mutation")
            .setCreatedAtEpochMs(3L)
            .setMessageMutation(
                MessageMutationV1.newBuilder()
                    .setTargetEventId("message-one")
                    .setAction(action)
                    .setText(text),
            )
            .build()
            .toByteArray(),
    )
}
