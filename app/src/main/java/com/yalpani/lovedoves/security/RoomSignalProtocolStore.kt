package com.yalpani.lovedoves.security

import com.google.protobuf.ByteString
import com.yalpani.lovedoves.data.SignalRecordDao
import com.yalpani.lovedoves.data.SignalRecordEntity
import com.yalpani.lovedoves.protocol.v1.PublicPreKeyBundleV1
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.InvalidKeyIdException
import org.signal.libsignal.protocol.NoSessionException
import org.signal.libsignal.protocol.ReusedBaseKeyException
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.groups.state.SenderKeyRecord
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import org.signal.libsignal.protocol.state.IdentityKeyStore
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyBundle
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SessionRecord
import org.signal.libsignal.protocol.state.SignalProtocolStore
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.util.KeyHelper
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

internal class RoomSignalProtocolStore private constructor(
    private val records: SignalRecordDao,
    private val localIdentity: IdentityKeyPair,
    private val registrationId: Int,
) : SignalProtocolStore {
    override fun getIdentityKeyPair(): IdentityKeyPair = localIdentity

    override fun getLocalRegistrationId(): Int = registrationId

    override fun saveIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
    ): IdentityKeyStore.IdentityChange {
        val key = addressKey(address)
        val existing = records.get(REMOTE_IDENTITY, key)
        records.put(SignalRecordEntity(REMOTE_IDENTITY, key, identityKey.serialize()))
        return if (existing != null && !existing.contentEquals(identityKey.serialize())) {
            IdentityKeyStore.IdentityChange.REPLACED_EXISTING
        } else {
            IdentityKeyStore.IdentityChange.NEW_OR_UNCHANGED
        }
    }

    override fun isTrustedIdentity(
        address: SignalProtocolAddress,
        identityKey: IdentityKey,
        direction: IdentityKeyStore.Direction,
    ): Boolean {
        val existing = records.get(REMOTE_IDENTITY, addressKey(address)) ?: return true
        return existing.contentEquals(identityKey.serialize())
    }

    override fun getIdentity(address: SignalProtocolAddress): IdentityKey? =
        records.get(REMOTE_IDENTITY, addressKey(address))?.let(::IdentityKey)

    override fun loadPreKey(preKeyId: Int): PreKeyRecord =
        records.get(PRE_KEY, preKeyId.toString())?.let(::PreKeyRecord)
            ?: throw InvalidKeyIdException("No pre-key $preKeyId")

    override fun storePreKey(preKeyId: Int, record: PreKeyRecord) {
        records.put(SignalRecordEntity(PRE_KEY, preKeyId.toString(), record.serialize()))
    }

    override fun containsPreKey(preKeyId: Int): Boolean =
        records.get(PRE_KEY, preKeyId.toString()) != null

    override fun removePreKey(preKeyId: Int) {
        records.delete(PRE_KEY, preKeyId.toString())
    }

    override fun loadSession(address: SignalProtocolAddress): SessionRecord? =
        records.get(SESSION, addressKey(address))?.let(::SessionRecord)

    override fun loadExistingSessions(
        addresses: MutableList<SignalProtocolAddress>,
    ): MutableList<SessionRecord> = addresses.mapTo(mutableListOf()) { address ->
        loadSession(address) ?: throw NoSessionException(address, "No session for $address")
    }

    override fun getSubDeviceSessions(name: String): MutableList<Int> =
        records.keys(SESSION)
            .mapNotNull(::parseAddressKey)
            .filter { (storedName, deviceId) -> storedName == name && deviceId != PRIMARY_DEVICE_ID }
            .mapTo(mutableListOf()) { it.second }

    override fun storeSession(address: SignalProtocolAddress, record: SessionRecord) {
        records.put(SignalRecordEntity(SESSION, addressKey(address), record.serialize()))
    }

    override fun containsSession(address: SignalProtocolAddress): Boolean =
        records.get(SESSION, addressKey(address)) != null

    override fun deleteSession(address: SignalProtocolAddress) {
        records.delete(SESSION, addressKey(address))
    }

    override fun deleteAllSessions(name: String) {
        records.keys(SESSION)
            .mapNotNull(::parseAddressKey)
            .filter { it.first == name }
            .forEach { records.delete(SESSION, "$name${ADDRESS_SEPARATOR}${it.second}") }
    }

    override fun loadSignedPreKey(signedPreKeyId: Int): SignedPreKeyRecord =
        records.get(SIGNED_PRE_KEY, signedPreKeyId.toString())?.let(::SignedPreKeyRecord)
            ?: throw InvalidKeyIdException("No signed pre-key $signedPreKeyId")

    override fun loadSignedPreKeys(): MutableList<SignedPreKeyRecord> =
        records.keys(SIGNED_PRE_KEY).mapTo(mutableListOf()) { loadSignedPreKey(it.toInt()) }

    override fun storeSignedPreKey(signedPreKeyId: Int, record: SignedPreKeyRecord) {
        records.put(
            SignalRecordEntity(SIGNED_PRE_KEY, signedPreKeyId.toString(), record.serialize()),
        )
    }

    override fun containsSignedPreKey(signedPreKeyId: Int): Boolean =
        records.get(SIGNED_PRE_KEY, signedPreKeyId.toString()) != null

    override fun removeSignedPreKey(signedPreKeyId: Int) {
        records.delete(SIGNED_PRE_KEY, signedPreKeyId.toString())
    }

    override fun loadKyberPreKey(kyberPreKeyId: Int): KyberPreKeyRecord =
        records.get(KYBER_PRE_KEY, kyberPreKeyId.toString())?.let(::KyberPreKeyRecord)
            ?: throw InvalidKeyIdException("No Kyber pre-key $kyberPreKeyId")

    override fun loadKyberPreKeys(): MutableList<KyberPreKeyRecord> =
        records.keys(KYBER_PRE_KEY).mapTo(mutableListOf()) { loadKyberPreKey(it.toInt()) }

    override fun storeKyberPreKey(kyberPreKeyId: Int, record: KyberPreKeyRecord) {
        records.put(
            SignalRecordEntity(KYBER_PRE_KEY, kyberPreKeyId.toString(), record.serialize()),
        )
    }

    override fun containsKyberPreKey(kyberPreKeyId: Int): Boolean =
        records.get(KYBER_PRE_KEY, kyberPreKeyId.toString()) != null

    override fun markKyberPreKeyUsed(
        kyberPreKeyId: Int,
        signedPreKeyId: Int,
        baseKey: ECPublicKey,
    ) {
        val baseKeyHash = sha256(baseKey.serialize()).toHex()
        val key = "$kyberPreKeyId:$signedPreKeyId:$baseKeyHash"
        if (records.get(KYBER_USED, key) != null) throw ReusedBaseKeyException()
        records.put(SignalRecordEntity(KYBER_USED, key, byteArrayOf(1)))
    }

    override fun storeSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
        record: SenderKeyRecord,
    ) {
        val key = "${addressKey(sender)}:$distributionId"
        records.put(SignalRecordEntity(SENDER_KEY, key, record.serialize()))
    }

    override fun loadSenderKey(
        sender: SignalProtocolAddress,
        distributionId: UUID,
    ): SenderKeyRecord? = records.get(
        SENDER_KEY,
        "${addressKey(sender)}:$distributionId",
    )?.let(::SenderKeyRecord)

    fun createPublicBundle(addressName: String): PublicPreKeyBundleV1 {
        val preKeyId = nextRecordId(PRE_KEY)
        val signedPreKeyId = nextRecordId(SIGNED_PRE_KEY)
        val kyberPreKeyId = nextRecordId(KYBER_PRE_KEY)
        val preKeyPair = ECKeyPair.generate()
        val signedPreKeyPair = ECKeyPair.generate()
        val signedSignature = localIdentity.privateKey.calculateSignature(
            signedPreKeyPair.publicKey.serialize(),
        )
        val kyberPreKeyPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val kyberSignature = localIdentity.privateKey.calculateSignature(
            kyberPreKeyPair.publicKey.serialize(),
        )
        storePreKey(preKeyId, PreKeyRecord(preKeyId, preKeyPair))
        storeSignedPreKey(
            signedPreKeyId,
            SignedPreKeyRecord(
                signedPreKeyId,
                System.currentTimeMillis(),
                signedPreKeyPair,
                signedSignature,
            ),
        )
        storeKyberPreKey(
            kyberPreKeyId,
            KyberPreKeyRecord(
                kyberPreKeyId,
                System.currentTimeMillis(),
                kyberPreKeyPair,
                kyberSignature,
            ),
        )
        return PublicPreKeyBundleV1.newBuilder()
            .setRegistrationId(registrationId)
            .setDeviceId(PRIMARY_DEVICE_ID)
            .setPreKeyId(preKeyId)
            .setPreKeyPublic(ByteString.copyFrom(preKeyPair.publicKey.serialize()))
            .setSignedPreKeyId(signedPreKeyId)
            .setSignedPreKeyPublic(ByteString.copyFrom(signedPreKeyPair.publicKey.serialize()))
            .setSignedPreKeySignature(ByteString.copyFrom(signedSignature))
            .setIdentityKey(ByteString.copyFrom(localIdentity.publicKey.serialize()))
            .setKyberPreKeyId(kyberPreKeyId)
            .setKyberPreKeyPublic(ByteString.copyFrom(kyberPreKeyPair.publicKey.serialize()))
            .setKyberPreKeySignature(ByteString.copyFrom(kyberSignature))
            .setAddressName(addressName)
            .build()
    }

    private fun nextRecordId(kind: String): Int {
        val random = SecureRandom()
        repeat(100) {
            val candidate = random.nextInt(MAX_PRE_KEY_ID)
            if (records.get(kind, candidate.toString()) == null) return candidate
        }
        error("Could not allocate pre-key id")
    }

    companion object {
        private const val LOCAL_IDENTITY = "local_identity"
        private const val LOCAL_REGISTRATION = "local_registration"
        private const val REMOTE_IDENTITY = "remote_identity"
        private const val PRE_KEY = "pre_key"
        private const val SIGNED_PRE_KEY = "signed_pre_key"
        private const val KYBER_PRE_KEY = "kyber_pre_key"
        private const val KYBER_USED = "kyber_used"
        private const val SESSION = "session"
        private const val SENDER_KEY = "sender_key"
        private const val LOCAL_KEY = "self"
        private const val ADDRESS_SEPARATOR = "#"
        private const val PRIMARY_DEVICE_ID = 1
        private const val MAX_PRE_KEY_ID = 0xFFFFFF

        fun open(records: SignalRecordDao): RoomSignalProtocolStore {
            val serializedIdentity = records.get(LOCAL_IDENTITY, LOCAL_KEY)
            val registrationBytes = records.get(LOCAL_REGISTRATION, LOCAL_KEY)
            if (serializedIdentity != null && registrationBytes != null) {
                return RoomSignalProtocolStore(
                    records = records,
                    localIdentity = IdentityKeyPair(serializedIdentity),
                    registrationId = ByteBuffer.wrap(registrationBytes).int,
                )
            }
            check(serializedIdentity == null && registrationBytes == null) {
                "Incomplete local Signal identity"
            }
            val identity = IdentityKeyPair.generate()
            val registration = KeyHelper.generateRegistrationId(false)
            records.put(SignalRecordEntity(LOCAL_IDENTITY, LOCAL_KEY, identity.serialize()))
            records.put(
                SignalRecordEntity(
                    LOCAL_REGISTRATION,
                    LOCAL_KEY,
                    ByteBuffer.allocate(Int.SIZE_BYTES).putInt(registration).array(),
                ),
            )
            return RoomSignalProtocolStore(records, identity, registration)
        }

        fun publicBundle(bundle: PublicPreKeyBundleV1): PreKeyBundle = PreKeyBundle(
            bundle.registrationId,
            bundle.deviceId,
            bundle.preKeyId,
            ECPublicKey(bundle.preKeyPublic.toByteArray()),
            bundle.signedPreKeyId,
            ECPublicKey(bundle.signedPreKeyPublic.toByteArray()),
            bundle.signedPreKeySignature.toByteArray(),
            IdentityKey(bundle.identityKey.toByteArray()),
            bundle.kyberPreKeyId,
            KEMPublicKey(bundle.kyberPreKeyPublic.toByteArray()),
            bundle.kyberPreKeySignature.toByteArray(),
        )

        private fun addressKey(address: SignalProtocolAddress): String =
            "${address.name}$ADDRESS_SEPARATOR${address.deviceId}"

        private fun parseAddressKey(key: String): Pair<String, Int>? {
            val index = key.lastIndexOf(ADDRESS_SEPARATOR)
            if (index < 1) return null
            return key.substring(0, index) to key.substring(index + 1).toIntOrNull().let {
                it ?: return null
            }
        }

        private fun sha256(bytes: ByteArray): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(bytes)

        private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }
    }
}

internal data class SignalCiphertext(val type: Int, val bytes: ByteArray)

internal class SignalSession(
    private val store: RoomSignalProtocolStore,
    private val localAddressName: String,
) {
    fun establish(remoteBundle: PublicPreKeyBundleV1) {
        require(remoteBundle.addressName.isNotBlank()) { "Partner address is missing" }
        SessionBuilder(
            store,
            remoteBundle.address(),
            SignalProtocolAddress(localAddressName, 1),
        ).process(RoomSignalProtocolStore.publicBundle(remoteBundle))
    }

    fun encrypt(remoteAddressName: String, plaintext: ByteArray): SignalCiphertext {
        val message = cipher(remoteAddressName).encrypt(plaintext)
        return SignalCiphertext(type = message.type, bytes = message.serialize())
    }

    fun decrypt(remoteAddressName: String, type: Int, ciphertext: ByteArray): ByteArray {
        val cipher = cipher(remoteAddressName)
        return when (type) {
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(PreKeySignalMessage(ciphertext))
            CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(ciphertext))
            else -> error("Unsupported Signal message type $type")
        }
    }

    private fun cipher(remoteAddressName: String): SessionCipher = SessionCipher(
        store,
        SignalProtocolAddress(localAddressName, 1),
        SignalProtocolAddress(remoteAddressName, 1),
    )

    private fun PublicPreKeyBundleV1.address(): SignalProtocolAddress =
        SignalProtocolAddress(addressName, deviceId)
}
