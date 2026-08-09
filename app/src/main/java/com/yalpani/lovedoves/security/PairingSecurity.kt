package com.yalpani.lovedoves.security

import com.yalpani.lovedoves.protocol.v1.InviteV1
import java.security.MessageDigest
import java.util.Base64

internal object PairingPayloadCodec {
    private const val QR_PREFIX = "lovedoves://pair/v1/"
    private const val REMOTE_PREFIX = "https://lovedoves.yalpani.com/i#"

    fun qr(invite: InviteV1): String = QR_PREFIX + encode(invite.toByteArray())

    fun remoteLink(invite: InviteV1): String = REMOTE_PREFIX + encode(invite.toByteArray())

    fun decodeInvite(payload: String, nowEpochMillis: Long = System.currentTimeMillis()): InviteV1 {
        val encoded = when {
            payload.startsWith(QR_PREFIX) -> payload.removePrefix(QR_PREFIX)
            payload.startsWith(REMOTE_PREFIX) -> payload.removePrefix(REMOTE_PREFIX)
            else -> error("Unknown Love Doves invitation")
        }
        val invite = InviteV1.parseFrom(decode(encoded))
        require(invite.version == 1) { "Unsupported invitation version" }
        require(invite.inviteId.isNotBlank()) { "Invitation id is missing" }
        require(invite.nonce.size() == NONCE_BYTES) { "Invalid invitation nonce" }
        require(invite.rendezvousSecret.size() == CAPABILITY_BYTES) {
            "Invalid rendezvous capability"
        }
        require(invite.expiresAtEpochMs in nowEpochMillis..(nowEpochMillis + MAX_INVITE_AGE_MS)) {
            "Invitation is expired or has an invalid lifetime"
        }
        return invite
    }

    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    private fun decode(value: String): ByteArray =
        Base64.getUrlDecoder().decode(value)

    private const val NONCE_BYTES = 32
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
