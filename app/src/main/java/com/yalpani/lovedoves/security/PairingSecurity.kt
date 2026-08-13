package com.yalpani.lovedoves.security

import com.yalpani.lovedoves.protocol.v1.InviteV1
import com.yalpani.lovedoves.protocol.v1.PairingReferenceV1
import java.security.MessageDigest
import java.util.Base64

internal object PairingPayloadCodec {
    private const val QR_PREFIX = "lovedoves://pair/v1/"
    private const val REMOTE_PREFIX = "https://lovedoves.yalpani.com/i#"

    fun qr(invite: InviteV1): String = QR_PREFIX + encode(reference(invite).toByteArray())

    fun remoteLink(invite: InviteV1): String = REMOTE_PREFIX + encode(reference(invite).toByteArray())

    fun responseReference(responseId: String): String = "lovedoves://response/v1/$responseId"

    fun isRemote(payload: String): Boolean = payload.startsWith(REMOTE_PREFIX)

    fun decodeReference(
        payload: String,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): PairingReferenceV1 {
        val encoded = when {
            payload.startsWith(QR_PREFIX) -> payload.removePrefix(QR_PREFIX)
            payload.startsWith(REMOTE_PREFIX) -> payload.removePrefix(REMOTE_PREFIX)
            else -> error("Unknown Love Doves invitation")
        }
        val reference = PairingReferenceV1.parseFrom(decode(encoded))
        require(reference.version == 1) { "Unsupported invitation version" }
        require(reference.responseId.isNotBlank()) { "Invitation id is missing" }
        require(reference.inviteDeliveryId.isNotBlank()) { "Invitation delivery id is missing" }
        require(reference.inviteDeliverySecret.size() == CAPABILITY_BYTES)
        require(reference.inviteDeliveryCapability.size() == CAPABILITY_BYTES)
        require(reference.expiresAtEpochMs in nowEpochMillis..(nowEpochMillis + MAX_INVITE_AGE_MS)) {
            "Invitation is expired or has an invalid lifetime"
        }
        return reference
    }

    private fun reference(invite: InviteV1): PairingReferenceV1 = PairingReferenceV1.newBuilder()
        .setVersion(1)
        .setRelayUrl(invite.mailbox.relayUrl)
        .setResponseId(invite.inviteId)
        .setInviteDeliveryId(invite.inviteDeliveryId)
        .setInviteDeliverySecret(invite.inviteDeliverySecret)
        .setInviteDeliveryCapability(invite.inviteDeliveryCapability)
        .setExpiresAtEpochMs(invite.expiresAtEpochMs)
        .build()

    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)

    private const val CAPABILITY_BYTES = 32
    private const val MAX_INVITE_AGE_MS = 10 * 60 * 1_000L
}

internal object SafetyWords {
    fun indices(
        firstIdentity: ByteArray,
        secondIdentity: ByteArray,
        inviteNonce: ByteArray,
    ): List<Int> {
        require(inviteNonce.size == 32)
        val ordered = listOf(firstIdentity, secondIdentity).sortedWith(::compareUnsigned)
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("LoveDovesSafetyWordsV1".encodeToByteArray())
        digest.update(inviteNonce)
        ordered.forEach(digest::update)
        return takeElevenBitValues(digest.digest(), WORD_COUNT)
    }

    fun render(indices: List<Int>, words: List<String>): String {
        require(words.size == WORD_LIST_SIZE) { "Safety word list must contain 2048 words" }
        require(indices.size == WORD_COUNT)
        return indices.joinToString(" ") { words[it] }
    }

    private fun takeElevenBitValues(bytes: ByteArray, count: Int): List<Int> {
        var bitOffset = 0
        return List(count) {
            var value = 0
            repeat(11) {
                val byteIndex = bitOffset / 8
                val shift = 7 - bitOffset % 8
                value = (value shl 1) or ((bytes[byteIndex].toInt() ushr shift) and 1)
                bitOffset++
            }
            value
        }
    }

    private fun compareUnsigned(left: ByteArray, right: ByteArray): Int {
        val common = minOf(left.size, right.size)
        for (index in 0 until common) {
            val comparison = (left[index].toInt() and 0xff).compareTo(right[index].toInt() and 0xff)
            if (comparison != 0) return comparison
        }
        return left.size.compareTo(right.size)
    }

    private const val WORD_COUNT = 6
    private const val WORD_LIST_SIZE = 2048
}
