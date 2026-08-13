package com.yalpani.lovedoves.security

import com.google.protobuf.ByteString
import com.yalpani.lovedoves.protocol.v1.InviteV1
import com.yalpani.lovedoves.protocol.v1.MailboxWriteV1
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PairingPayloadCodecTest {
    @Test
    fun qrContainsOnlyShortLivedBlindRendezvousReference() {
        val now = 1_800_000_000_000L
        val invite = invite(now + 10 * 60 * 1_000L)

        val qr = PairingPayloadCodec.qr(invite)
        val decoded = PairingPayloadCodec.decodeReference(qr, now)

        assertTrue(qr.length < 1_000)
        assertEquals(invite.inviteId, decoded.responseId)
        assertEquals(invite.inviteDeliveryId, decoded.inviteDeliveryId)
        assertEquals(invite.expiresAtEpochMs, decoded.expiresAtEpochMs)
    }

    @Test
    fun remoteSecretStaysInUrlFragmentAndTamperingIsRejected() {
        val now = 1_800_000_000_000L
        val link = PairingPayloadCodec.remoteLink(invite(now + 60_000L))
        assertTrue(link.startsWith("https://lovedoves.yalpani.com/i#"))
        assertTrue(!link.substringBefore('#').contains("pair/v1"))

        val tampered = link.dropLast(1) + "%"
        assertThrows(Exception::class.java) {
            PairingPayloadCodec.decodeReference(tampered, now)
        }
    }

    @Test
    fun expiredReferenceIsRejected() {
        val now = 1_800_000_000_000L
        val expired = PairingPayloadCodec.qr(invite(now - 1))

        assertThrows(IllegalArgumentException::class.java) {
            PairingPayloadCodec.decodeReference(expired, now)
        }
    }

    private fun invite(expiresAt: Long): InviteV1 = InviteV1.newBuilder()
        .setVersion(1)
        .setInviteId("invite-00000000000001")
        .setExpiresAtEpochMs(expiresAt)
        .setMailbox(
            MailboxWriteV1.newBuilder()
                .setRelayUrl("https://lovedoves.yalpani.com")
                .setMailboxId("mailbox-0000000000001"),
        )
        .setInviteDeliveryId("delivery-000000000001")
        .setInviteDeliverySecret(ByteString.copyFrom(ByteArray(32) { 7 }))
        .setInviteDeliveryCapability(ByteString.copyFrom(ByteArray(32) { 9 }))
        .build()
}
