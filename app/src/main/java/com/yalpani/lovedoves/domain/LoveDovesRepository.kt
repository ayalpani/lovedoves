package com.yalpani.lovedoves.domain

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.FirebaseApp
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.google.protobuf.ByteString
import com.yalpani.lovedoves.BuildConfig
import com.yalpani.lovedoves.R
import com.yalpani.lovedoves.VaultSession
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.data.LocalProfileEntity
import com.yalpani.lovedoves.data.MediaEntity
import com.yalpani.lovedoves.data.OutboxEntity
import com.yalpani.lovedoves.data.PairStateEntity
import com.yalpani.lovedoves.data.PendingPairingEntity
import com.yalpani.lovedoves.data.ProcessedObjectEntity
import com.yalpani.lovedoves.protocol.v1.ConversationEventV1
import com.yalpani.lovedoves.protocol.v1.DeliveryReceiptV1
import com.yalpani.lovedoves.protocol.v1.DeviceRevocationV1
import com.yalpani.lovedoves.protocol.v1.EnvelopeV1
import com.yalpani.lovedoves.protocol.v1.EncryptedMediaV1
import com.yalpani.lovedoves.protocol.v1.InviteV1
import com.yalpani.lovedoves.protocol.v1.MailboxWriteV1
import com.yalpani.lovedoves.protocol.v1.PairConfirmationV1
import com.yalpani.lovedoves.protocol.v1.PairResponseV1
import com.yalpani.lovedoves.protocol.v1.PhotoMessageV1
import com.yalpani.lovedoves.protocol.v1.RecoveryBatchV1
import com.yalpani.lovedoves.protocol.v1.RecoveryManifestV1
import com.yalpani.lovedoves.protocol.v1.RecoveryRecordV1
import com.yalpani.lovedoves.protocol.v1.TextMessageV1
import com.yalpani.lovedoves.protocol.v1.VideoMessageV1
import com.yalpani.lovedoves.security.EncryptedMedia
import com.yalpani.lovedoves.security.EncryptedMediaStore
import com.yalpani.lovedoves.security.PairingPayloadCodec
import com.yalpani.lovedoves.security.RendezvousCipher
import com.yalpani.lovedoves.security.SafetyWords
import com.yalpani.lovedoves.transport.CapabilityCodec
import com.yalpani.lovedoves.transport.InboundSpool
import com.yalpani.lovedoves.transport.RelayClient
import com.yalpani.lovedoves.transport.TransportCredentialStore
import com.yalpani.lovedoves.transport.TransportCredentials
import com.yalpani.lovedoves.transport.TransportSyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

internal enum class PairingMode { IN_PERSON, REMOTE }

internal data class PairingSnapshot(
    val role: String,
    val mode: PairingMode,
    val invitation: String,
    val remoteLink: String,
    val response: String?,
    val safetyWords: String?,
    val localConfirmed: Boolean,
    val remoteConfirmed: Boolean,
    val recovery: Boolean,
)

internal data class ChatMessage(
    val id: String,
    val outgoing: Boolean,
    val text: String?,
    val mediaId: String?,
    val createdAtEpochMillis: Long,
    val deliveryState: String,
)

internal data class PreparedPhoto(
    val jpeg: ByteArray,
    val width: Int,
    val height: Int,
)

internal data class PreparedVideo(
    val mp4: ByteArray,
    val thumbnailJpeg: ByteArray,
    val width: Int,
    val height: Int,
    val durationMillis: Long,
    val thumbnailWidth: Int,
    val thumbnailHeight: Int,
) {
    fun clear() {
        mp4.fill(0)
        thumbnailJpeg.fill(0)
    }
}

internal class LoveDovesRepository(
    private val context: Context,
    private val session: VaultSession,
) {
    private val database = session.database
    private val spool = InboundSpool(context)
    private val transportCredentials = TransportCredentialStore(context)

    suspend fun profile(): LocalProfileEntity? = io { database.profileDao().get() }

    suspend fun saveProfile(name: String) = io {
        val normalized = name.trim().replace(Regex("\\s+"), " ")
        require(normalized.length in 1..40) { "Bitte gib einen Namen mit höchstens 40 Zeichen ein." }
        val existing = database.profileDao().get()
        database.profileDao().put(
            existing?.copy(displayName = normalized) ?: LocalProfileEntity(
                displayName = normalized,
                addressName = UUID.randomUUID().toString(),
                createdAtEpochMillis = System.currentTimeMillis(),
            ),
        )
    }

    suspend fun pairState(): PairStateEntity? = io { database.pairStateDao().get() }

    suspend fun pairingSnapshot(): PairingSnapshot? = io {
        database.pendingPairingDao().get()?.toSnapshot()
    }

    suspend fun createInvitation(mode: PairingMode, bootstrapToken: String): PairingSnapshot = io {
        require(database.pairStateDao().get() == null) { "Dieses Gerät ist bereits verbunden." }
        require(database.pendingPairingDao().get() == null) { "Es gibt bereits eine Einladung." }
        val profile = requireNotNull(database.profileDao().get())
        val bootstrap = CapabilityCodec.decode(bootstrapToken.trim())
        require(bootstrap.size == CAPABILITY_BYTES) { "Das Server-Starttoken ist ungültig." }
        val relay = RelayClient(BuildConfig.RELAY_URL)
        val mailbox = relay.createMailbox(bootstrap)
        val enrollment = requireNotNull(mailbox.partnerEnrollmentCapability) {
            "Der private Relay ist bereits belegt."
        }
        try {
            val signal = session.signalStore()
            val bundle = signal.createPublicBundle(profile.addressName)
            val inviteId = randomId()
            val inviteDeliveryId = randomId()
            val nonce = randomBytes(CAPABILITY_BYTES)
            val rendezvousSecret = randomBytes(CAPABILITY_BYTES)
            val rendezvousCapability = randomBytes(CAPABILITY_BYTES)
            val inviteDeliverySecret = randomBytes(CAPABILITY_BYTES)
            val inviteDeliveryCapability = randomBytes(CAPABILITY_BYTES)
            val invite = InviteV1.newBuilder()
                .setVersion(1)
                .setInviteId(inviteId)
                .setExpiresAtEpochMs(System.currentTimeMillis() + INVITE_LIFETIME_MS)
                .setNonce(ByteString.copyFrom(nonce))
                .setDisplayName(profile.displayName)
                .setPreKeyBundle(bundle)
                .setMailbox(
                    MailboxWriteV1.newBuilder()
                        .setRelayUrl(BuildConfig.RELAY_URL)
                        .setMailboxId(mailbox.mailboxId)
                        .setWriteCapability(ByteString.copyFrom(mailbox.writeCapability)),
                )
                .setRendezvousSecret(ByteString.copyFrom(rendezvousSecret))
                .setRendezvousCapability(ByteString.copyFrom(rendezvousCapability))
                .setRelayEnrollmentCapability(ByteString.copyFrom(enrollment))
                .setInviteDeliveryId(inviteDeliveryId)
                .setInviteDeliverySecret(ByteString.copyFrom(inviteDeliverySecret))
                .setInviteDeliveryCapability(ByteString.copyFrom(inviteDeliveryCapability))
                .build()
            relay.createRendezvous(inviteId, rendezvousCapability)
            relay.createRendezvous(inviteDeliveryId, inviteDeliveryCapability)
            relay.putRendezvous(
                inviteDeliveryId,
                inviteDeliveryCapability,
                RendezvousCipher.encrypt(inviteDeliverySecret, inviteDeliveryId, invite.toByteArray()),
            )
            database.pendingPairingDao().put(
                PendingPairingEntity(
                    role = ROLE_INVITER,
                    mode = mode.name,
                    invite = invite.toByteArray(),
                    response = null,
                    ownMailboxId = mailbox.mailboxId,
                    ownReadCapability = mailbox.readCapability,
                    safetyWords = null,
                    localConfirmed = false,
                    remoteConfirmed = false,
                    recovery = false,
                    recoveryOldIdentityKey = null,
                ),
            )
            storeTransportCredentials(BuildConfig.RELAY_URL, mailbox.mailboxId, mailbox.readCapability)
            database.pendingPairingDao().get()!!.toSnapshot()
        } catch (failure: Throwable) {
            runCatching { relay.deleteMailbox(mailbox.mailboxId, mailbox.readCapability) }
            throw failure
        }
    }

    suspend fun createRecoveryInvitation(mode: PairingMode): PairingSnapshot = io {
        val oldPair = requireNotNull(database.pairStateDao().get()) {
            "Dieses Gerät ist noch nicht mit einem Partner verbunden."
        }
        require(database.pendingPairingDao().get() == null) {
            "Es gibt bereits eine offene Verbindung."
        }
        val profile = requireNotNull(database.profileDao().get())
        val relay = RelayClient(oldPair.relayUrl)
        val replacement = relay.preparePartnerReplacement(
            oldPair.ownMailboxId,
            oldPair.ownReadCapability,
        )
        val signal = session.signalStore()
        val bundle = signal.createPublicBundle(profile.addressName)
        val inviteId = randomId()
        val inviteDeliveryId = randomId()
        val nonce = randomBytes(CAPABILITY_BYTES)
        val rendezvousSecret = randomBytes(CAPABILITY_BYTES)
        val rendezvousCapability = randomBytes(CAPABILITY_BYTES)
        val inviteDeliverySecret = randomBytes(CAPABILITY_BYTES)
        val inviteDeliveryCapability = randomBytes(CAPABILITY_BYTES)
        val invite = InviteV1.newBuilder()
            .setVersion(1)
            .setInviteId(inviteId)
            .setExpiresAtEpochMs(System.currentTimeMillis() + INVITE_LIFETIME_MS)
            .setNonce(ByteString.copyFrom(nonce))
            .setDisplayName(profile.displayName)
            .setPreKeyBundle(bundle)
            .setMailbox(
                MailboxWriteV1.newBuilder()
                    .setRelayUrl(oldPair.relayUrl)
                    .setMailboxId(oldPair.ownMailboxId)
                    .setWriteCapability(ByteString.copyFrom(replacement.ownWriteCapability)),
            )
            .setRendezvousSecret(ByteString.copyFrom(rendezvousSecret))
            .setRendezvousCapability(ByteString.copyFrom(rendezvousCapability))
            .setRelayEnrollmentCapability(
                ByteString.copyFrom(replacement.partnerEnrollmentCapability),
            )
            .setInviteDeliveryId(inviteDeliveryId)
            .setInviteDeliverySecret(ByteString.copyFrom(inviteDeliverySecret))
            .setInviteDeliveryCapability(ByteString.copyFrom(inviteDeliveryCapability))
            .setRecovery(true)
            .build()
        relay.createRendezvous(inviteId, rendezvousCapability)
        relay.createRendezvous(inviteDeliveryId, inviteDeliveryCapability)
        relay.putRendezvous(
            inviteDeliveryId,
            inviteDeliveryCapability,
            RendezvousCipher.encrypt(inviteDeliverySecret, inviteDeliveryId, invite.toByteArray()),
        )
        database.pendingPairingDao().put(
            PendingPairingEntity(
                role = ROLE_INVITER,
                mode = mode.name,
                invite = invite.toByteArray(),
                response = null,
                ownMailboxId = oldPair.ownMailboxId,
                ownReadCapability = oldPair.ownReadCapability,
                safetyWords = null,
                localConfirmed = false,
                remoteConfirmed = false,
                recovery = true,
                recoveryOldIdentityKey = oldPair.partnerIdentityKey,
            ),
        )
        database.pendingPairingDao().get()!!.toSnapshot()
    }

    suspend fun joinInvitation(payload: String): PairingSnapshot = io {
        require(database.pairStateDao().get() == null) { "Dieses Gerät ist bereits verbunden." }
        require(database.pendingPairingDao().get() == null) { "Es gibt bereits eine Verbindung." }
        val reference = PairingPayloadCodec.decodeReference(payload)
        require(reference.relayUrl == BuildConfig.RELAY_URL) {
            "Die Einladung verwendet nicht den erwarteten Love-Doves-Relay."
        }
        val relay = RelayClient(reference.relayUrl)
        val invite = InviteV1.parseFrom(
            RendezvousCipher.decrypt(
                reference.inviteDeliverySecret.toByteArray(),
                reference.inviteDeliveryId,
                relay.getRendezvous(
                    reference.inviteDeliveryId,
                    reference.inviteDeliveryCapability.toByteArray(),
                ),
            ),
        )
        validateRelay(invite)
        require(invite.inviteId == reference.responseId)
        require(invite.inviteDeliveryId == reference.inviteDeliveryId)
        require(invite.inviteDeliverySecret == reference.inviteDeliverySecret)
        require(invite.inviteDeliveryCapability == reference.inviteDeliveryCapability)
        require(invite.expiresAtEpochMs == reference.expiresAtEpochMs)
        val profile = requireNotNull(database.profileDao().get())
        val mailbox = relay.createMailbox(invite.relayEnrollmentCapability.toByteArray())
        try {
            val signalStore = session.signalStore()
            val ownBundle = signalStore.createPublicBundle(profile.addressName)
            session.signalSession(profile.addressName).establish(invite.preKeyBundle)
            val response = PairResponseV1.newBuilder()
                .setVersion(1)
                .setInviteId(invite.inviteId)
                .setInviteNonce(invite.nonce)
                .setDisplayName(profile.displayName)
                .setPreKeyBundle(ownBundle)
                .setMailbox(
                    MailboxWriteV1.newBuilder()
                        .setRelayUrl(invite.mailbox.relayUrl)
                        .setMailboxId(mailbox.mailboxId)
                        .setWriteCapability(ByteString.copyFrom(mailbox.writeCapability)),
                )
                .build()
            val words = safetyWords(
                invite.preKeyBundle.identityKey.toByteArray(),
                ownBundle.identityKey.toByteArray(),
                invite.nonce.toByteArray(),
            )
            val encrypted = RendezvousCipher.encrypt(
                invite.rendezvousSecret.toByteArray(),
                invite.inviteId,
                response.toByteArray(),
            )
            relay.putRendezvous(
                invite.inviteId,
                invite.rendezvousCapability.toByteArray(),
                encrypted,
            )
            database.pendingPairingDao().put(
                PendingPairingEntity(
                    role = ROLE_JOINER,
                    mode = if (PairingPayloadCodec.isRemote(payload)) {
                        PairingMode.REMOTE.name
                    } else {
                        PairingMode.IN_PERSON.name
                    },
                    invite = invite.toByteArray(),
                    response = response.toByteArray(),
                    ownMailboxId = mailbox.mailboxId,
                    ownReadCapability = mailbox.readCapability,
                    safetyWords = words,
                    localConfirmed = false,
                    remoteConfirmed = false,
                    recovery = invite.recovery,
                    recoveryOldIdentityKey = null,
                ),
            )
            storeTransportCredentials(invite.mailbox.relayUrl, mailbox.mailboxId, mailbox.readCapability)
            database.pendingPairingDao().get()!!.toSnapshot()
        } catch (failure: Throwable) {
            runCatching { relay.deleteMailbox(mailbox.mailboxId, mailbox.readCapability) }
            throw failure
        }
    }

    suspend fun acceptPairResponse(responsePayload: String): PairingSnapshot = io {
        val prefix = "lovedoves://response/v1/"
        require(responsePayload.startsWith(prefix)) { "Unbekannte Love-Doves-Antwort." }
        val pending = requireNotNull(database.pendingPairingDao().get())
        val invite = InviteV1.parseFrom(pending.invite)
        require(responsePayload.removePrefix(prefix) == invite.inviteId) {
            "Diese Antwort gehört zu einer anderen Einladung."
        }
        fetchRemoteResponse()
    }

    suspend fun fetchRemoteResponse(): PairingSnapshot = io {
        val pending = requireNotNull(database.pendingPairingDao().get())
        require(pending.role == ROLE_INVITER && pending.mode == PairingMode.REMOTE.name)
        val invite = InviteV1.parseFrom(pending.invite)
        val encrypted = RelayClient(invite.mailbox.relayUrl).getRendezvous(
            invite.inviteId,
            invite.rendezvousCapability.toByteArray(),
        )
        val response = PairResponseV1.parseFrom(
            RendezvousCipher.decrypt(
                invite.rendezvousSecret.toByteArray(),
                invite.inviteId,
                encrypted,
            ),
        )
        acceptPairResponse(response)
    }

    suspend fun confirmPairing(): PairingSnapshot? = io {
        val pending = requireNotNull(database.pendingPairingDao().get())
        require(pending.response != null && pending.safetyWords != null) {
            "Die Antwort des anderen Geräts fehlt noch."
        }
        if (!pending.localConfirmed) {
            sendPairConfirmation(pending)
            database.pendingPairingDao().put(pending.copy(localConfirmed = true))
        }
        processInboundLocked()
        finalizePairIfReady()
        database.pendingPairingDao().get()?.toSnapshot()
    }

    suspend fun cancelPairing() = io {
        val pending = database.pendingPairingDao().get() ?: return@io
        val recoveringSurvivor = pending.recovery && database.pairStateDao().get() != null
        val invite = InviteV1.parseFrom(pending.invite)
        val relay = RelayClient(invite.mailbox.relayUrl)
        if (!recoveringSurvivor) {
            runCatching { relay.deleteMailbox(pending.ownMailboxId, pending.ownReadCapability) }
        }
        if (pending.role == ROLE_INVITER) {
            runCatching {
                relay.deleteRendezvous(
                    invite.inviteId,
                    invite.rendezvousCapability.toByteArray(),
                )
            }
            runCatching {
                relay.deleteRendezvous(
                    invite.inviteDeliveryId,
                    invite.inviteDeliveryCapability.toByteArray(),
                )
            }
        }
        database.pendingPairingDao().delete()
        if (!recoveringSurvivor) {
            database.signalRecordDao().deleteAll()
            transportCredentials.clear()
            spool.clear()
            TransportSyncWorker.cancel(context)
        }
    }

    fun messages(): Flow<List<ConversationEventEntity>> = database.conversationDao().observeAll()

    suspend fun sendText(text: String) = io {
        val normalized = text.trim()
        require(normalized.isNotEmpty() && normalized.length <= MAX_TEXT_LENGTH)
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.conversationDao().insert(
            ConversationEventEntity(id, true, KIND_TEXT, normalized, null, now, DELIVERY_SENDING),
        )
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId(id)
            .setCreatedAtEpochMs(now)
            .setText(TextMessageV1.newBuilder().setText(normalized))
            .build()
        enqueueAndUpload(id, id, event)
    }

    suspend fun sendPhoto(photo: PreparedPhoto) = io {
        require(photo.jpeg.size <= MAX_PHOTO_BYTES)
        val encrypted = try {
            session.media.encrypt(photo.jpeg)
        } finally {
            photo.jpeg.fill(0)
        }
        database.mediaDao().insert(encrypted.toEntity(photo.width, photo.height))
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        database.conversationDao().insert(
            ConversationEventEntity(id, true, KIND_PHOTO, null, encrypted.id, now, DELIVERY_SENDING),
        )
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId(id)
            .setCreatedAtEpochMs(now)
            .setPhoto(
                PhotoMessageV1.newBuilder()
                    .setMediaId(encrypted.id)
                    .setMimeType("image/jpeg")
                    .setWidth(photo.width)
                    .setHeight(photo.height)
                    .setEncryptedSize(encrypted.encryptedSize)
                    .setMediaKey(ByteString.copyFrom(encrypted.key))
                    .setMediaNonce(ByteString.copyFrom(encrypted.nonce))
                    .setSha256(ByteString.copyFrom(encrypted.cipherSha256)),
            )
            .build()
        enqueueAndUpload(id, id, event)
        encrypted.key.fill(0)
    }

    suspend fun sendVideo(video: PreparedVideo) = io {
        require(video.mp4.size <= MAX_VIDEO_BYTES)
        require(video.thumbnailJpeg.size <= MAX_PHOTO_BYTES)
        var encryptedVideo: EncryptedMedia? = null
        var encryptedThumbnail: EncryptedMedia? = null
        val id = UUID.randomUUID().toString()
        var stored = false
        try {
            encryptedVideo = session.media.encrypt(video.mp4)
            encryptedThumbnail = session.media.encrypt(video.thumbnailJpeg)
            val now = System.currentTimeMillis()
            database.withTransaction {
                database.mediaDao().insert(
                    encryptedThumbnail.toEntity(
                        mimeType = "image/jpeg",
                        width = video.thumbnailWidth,
                        height = video.thumbnailHeight,
                    ),
                )
                database.mediaDao().insert(
                    encryptedVideo.toEntity(
                        mimeType = "video/mp4",
                        width = video.width,
                        height = video.height,
                        thumbnailMediaId = encryptedThumbnail.id,
                        durationMillis = video.durationMillis,
                    ),
                )
                database.conversationDao().insert(
                    ConversationEventEntity(
                        id,
                        true,
                        KIND_VIDEO,
                        null,
                        encryptedVideo.id,
                        now,
                        DELIVERY_SENDING,
                    ),
                )
            }
            stored = true
            val event = ConversationEventV1.newBuilder()
                .setVersion(1)
                .setEventId(id)
                .setCreatedAtEpochMs(now)
                .setVideo(
                    VideoMessageV1.newBuilder()
                        .setVideo(encryptedVideo.toProtocolMedia("video/mp4"))
                        .setWidth(video.width)
                        .setHeight(video.height)
                        .setDurationMs(video.durationMillis)
                        .setThumbnail(encryptedThumbnail.toProtocolMedia("image/jpeg"))
                        .setThumbnailWidth(video.thumbnailWidth)
                        .setThumbnailHeight(video.thumbnailHeight),
                )
                .build()
            enqueueAndUpload(id, id, event)
        } catch (failure: Throwable) {
            if (stored) {
                database.conversationDao().updateDelivery(id, DELIVERY_FAILED)
            } else {
                encryptedVideo?.let { session.media.delete(it.relativePath) }
                encryptedThumbnail?.let { session.media.delete(it.relativePath) }
            }
            throw failure
        } finally {
            encryptedVideo?.key?.fill(0)
            encryptedThumbnail?.key?.fill(0)
            video.clear()
        }
    }

    suspend fun retryMessage(messageId: String) = io {
        val outbox = requireNotNull(database.outboxDao().get(messageId))
        uploadOutbox(outbox)
    }

    suspend fun deleteMessages(messageIds: Set<String>) = io {
        val ids = messageIds.take(MAX_LOCAL_DELETE_BATCH).distinct()
        if (ids.isEmpty()) return@io
        val events = ids.mapNotNull(database.conversationDao()::get)
        val media = events.mapNotNull { it.mediaId }
            .flatMap { mediaId ->
                database.mediaDao().get(mediaId)?.let { item ->
                    listOfNotNull(item, item.thumbnailMediaId?.let(database.mediaDao()::get))
                }.orEmpty()
            }
            .distinctBy { it.id }
        database.withTransaction {
            database.outboxDao().deleteForEvents(ids)
            database.conversationDao().delete(ids)
            if (media.isNotEmpty()) database.mediaDao().delete(media.map { it.id })
        }
        media.forEach {
            session.media.delete(it.relativePath)
            it.key.fill(0)
            it.nonce.fill(0)
        }
    }

    suspend fun markMessagesRead(messageIds: List<String>) = io {
        val unread = messageIds.distinct().mapNotNull(database.conversationDao()::get)
            .filter { !it.outgoing && it.deliveryState != DELIVERY_READ }
        unread.chunked(READ_RECEIPT_BATCH_SIZE).forEach { batch ->
            sendReadReceipt(batch.map { it.id })
            database.withTransaction {
                batch.forEach { database.conversationDao().updateDelivery(it.id, DELIVERY_READ) }
            }
        }
    }

    suspend fun syncNow() = io {
        downloadIncoming()
        processInboundLocked()
        flushOutbox()
        finalizePairIfReady()
    }

    suspend fun mediaBytes(mediaId: String): ByteArray = io {
        val media = requireNotNull(database.mediaDao().get(mediaId))
        session.media.decrypt(media.toEncryptedMedia())
    }

    suspend fun thumbnailBytes(mediaId: String): ByteArray = io {
        val media = requireNotNull(database.mediaDao().get(mediaId))
        val thumbnail = media.thumbnailMediaId?.let(database.mediaDao()::get) ?: media
        session.media.decrypt(requireNotNull(thumbnail).toEncryptedMedia())
    }

    suspend fun deletePairAndLocalData() = io {
        val pair = database.pairStateDao().get()
        if (pair != null) {
            RelayClient(pair.relayUrl).deleteMailbox(pair.ownMailboxId, pair.ownReadCapability)
        }
        transportCredentials.clear()
        spool.clear()
        TransportSyncWorker.cancel(context)
    }

    private suspend fun acceptPairResponse(response: PairResponseV1): PairingSnapshot {
        val pending = requireNotNull(database.pendingPairingDao().get())
        require(pending.role == ROLE_INVITER)
        val invite = InviteV1.parseFrom(pending.invite)
        require(response.version == 1)
        require(response.inviteId == invite.inviteId)
        require(response.inviteNonce == invite.nonce)
        require(response.mailbox.relayUrl == invite.mailbox.relayUrl)
        require(response.mailbox.writeCapability.size() == CAPABILITY_BYTES)
        val profile = requireNotNull(database.profileDao().get())
        session.signalSession(profile.addressName).establish(response.preKeyBundle)
        val words = safetyWords(
            invite.preKeyBundle.identityKey.toByteArray(),
            response.preKeyBundle.identityKey.toByteArray(),
            invite.nonce.toByteArray(),
        )
        database.pendingPairingDao().put(
            pending.copy(response = response.toByteArray(), safetyWords = words),
        )
        return database.pendingPairingDao().get()!!.toSnapshot()
    }

    private suspend fun sendPairConfirmation(pending: PendingPairingEntity) {
        val invite = InviteV1.parseFrom(pending.invite)
        val response = PairResponseV1.parseFrom(requireNotNull(pending.response))
        val confirmation = PairConfirmationV1.newBuilder()
            .setVersion(1)
            .setInviteId(invite.inviteId)
            .setTranscriptHash(ByteString.copyFrom(transcriptHash(invite, response)))
            .setConfirmedAtEpochMs(System.currentTimeMillis())
            .build()
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId(UUID.randomUUID().toString())
            .setCreatedAtEpochMs(System.currentTimeMillis())
            .setPairConfirmation(confirmation)
            .build()
        uploadEvent(event, pending.partnerTarget())
    }

    private suspend fun enqueueAndUpload(objectId: String, eventId: String, event: ConversationEventV1) {
        val pair = requireNotNull(database.pairStateDao().get())
        val envelope = encryptEvent(event, pair.partnerAddressName)
        val outbox = OutboxEntity(objectId, eventId, envelope, 0, System.currentTimeMillis())
        database.outboxDao().put(outbox)
        runCatching { uploadOutbox(outbox) }.onFailure {
            database.conversationDao().updateDelivery(eventId, DELIVERY_FAILED)
        }
    }

    private suspend fun uploadOutbox(item: OutboxEntity) {
        val pair = requireNotNull(database.pairStateDao().get())
        val relay = RelayClient(pair.relayUrl)
        relay.putObject(pair.partnerMailboxId, item.objectId, pair.partnerWriteCapability, item.encryptedEnvelope)
        val event = database.conversationDao().get(item.eventId)
        event?.mediaId?.let { mediaId ->
            val media = requireNotNull(database.mediaDao().get(mediaId))
            uploadMedia(relay, pair, media)
            media.thumbnailMediaId?.let { thumbnailId ->
                uploadMedia(relay, pair, requireNotNull(database.mediaDao().get(thumbnailId)))
            }
        }
        database.outboxDao().delete(item.objectId)
        if (item.eventId.isNotBlank()) {
            database.conversationDao().updateDelivery(item.eventId, DELIVERY_SENT)
        }
    }

    private suspend fun uploadMedia(
        relay: RelayClient,
        pair: PairStateEntity,
        media: MediaEntity,
    ) {
        relay.putObject(
            pair.partnerMailboxId,
            media.id,
            pair.partnerWriteCapability,
            session.media.readCiphertext(media.relativePath),
        )
    }

    private suspend fun flushOutbox() {
        database.outboxDao().ready(System.currentTimeMillis()).forEach { item ->
            runCatching { uploadOutbox(item) }.onFailure {
                val attempts = item.attempts + 1
                database.outboxDao().put(
                    item.copy(
                        attempts = attempts,
                        nextAttemptAtEpochMillis = System.currentTimeMillis() +
                            minOf(60_000L, 1_000L shl attempts.coerceAtMost(6)),
                    ),
                )
            }
        }
    }

    private suspend fun downloadIncoming() {
        val credentials = transportCredentials.get() ?: return
        val relay = RelayClient(credentials.relayUrl)
        relay.listObjects(credentials.mailboxId, credentials.readCapability).forEach { item ->
            if (!spool.contains(item.objectId)) {
                spool.put(
                    item.objectId,
                    relay.getObject(credentials.mailboxId, item.objectId, credentials.readCapability),
                    item.cipherSha256Hex,
                )
            }
            relay.acknowledge(credentials.mailboxId, item.objectId, credentials.readCapability)
        }
    }

    @Suppress("DEPRECATION") // Reads the short-lived pre-release receipt field for migration.
    private suspend fun processInboundLocked() {
        val pending = database.pendingPairingDao().get()
        val pair = database.pairStateDao().get()
        // A recovery keeps the old pair until the replacement has mutually confirmed. While that
        // overlap exists, inbound pairing traffic must be authenticated as the pending device.
        val remoteAddress = pending?.partnerTarget()?.addressName ?: pair?.partnerAddressName ?: return
        val available = spool.objectIds().toMutableSet()
        available.toList().forEach { objectId ->
            if (database.processedObjectDao().contains(objectId)) {
                spool.delete(objectId)
                available.remove(objectId)
                return@forEach
            }
            val packetBytes = spool.get(objectId)
            val envelope = runCatching { EnvelopeV1.parseFrom(packetBytes) }.getOrNull()
                ?.takeIf {
                    it.version == 1 &&
                        it.messageId == objectId &&
                        it.signalMessage.size() <= MAX_SIGNAL_MESSAGE_BYTES
                }
                ?: return@forEach
            var receiptFor: String? = null
            val mediaToDelete = mutableSetOf<String>()
            val transaction = runCatching {
                database.withTransaction {
                if (database.processedObjectDao().contains(objectId)) return@withTransaction
                val plaintext = session.signalSession(requireNotNull(database.profileDao().get()).addressName)
                    .decrypt(remoteAddress, envelope.signalMessageType, envelope.signalMessage.toByteArray())
                val event = ConversationEventV1.parseFrom(plaintext)
                require(event.version == 1 && event.eventId == envelope.messageId)
                when (event.payloadCase) {
                    ConversationEventV1.PayloadCase.TEXT -> {
                        require(event.text.text.isNotBlank())
                        require(event.text.text.length <= MAX_TEXT_LENGTH)
                        database.conversationDao().insert(
                            ConversationEventEntity(
                                event.eventId,
                                false,
                                KIND_TEXT,
                                event.text.text,
                                null,
                                event.createdAtEpochMs,
                                DELIVERY_DELIVERED,
                            ),
                        )
                        receiptFor = event.eventId
                    }
                    ConversationEventV1.PayloadCase.PHOTO -> {
                        validatePhoto(event.photo)
                        if (event.photo.mediaId !in available) throw MissingMediaException
                        val mediaBytes = spool.get(event.photo.mediaId)
                        require(mediaBytes.size.toLong() == event.photo.encryptedSize)
                        val relativePath = session.media.importCiphertext(
                            event.photo.mediaId,
                            mediaBytes,
                            event.photo.sha256.toByteArray(),
                        )
                        database.mediaDao().insert(
                            MediaEntity(
                                event.photo.mediaId,
                                relativePath,
                                event.photo.mimeType,
                                event.photo.width,
                                event.photo.height,
                                event.photo.encryptedSize,
                                event.photo.mediaKey.toByteArray(),
                                event.photo.mediaNonce.toByteArray(),
                                event.photo.sha256.toByteArray(),
                            ),
                        )
                        database.conversationDao().insert(
                            ConversationEventEntity(
                                event.eventId,
                                false,
                                KIND_PHOTO,
                                null,
                                event.photo.mediaId,
                                event.createdAtEpochMs,
                                DELIVERY_DELIVERED,
                            ),
                        )
                        database.processedObjectDao().insert(
                            ProcessedObjectEntity(event.photo.mediaId, System.currentTimeMillis()),
                        )
                        mediaToDelete += event.photo.mediaId
                        receiptFor = event.eventId
                    }
                    ConversationEventV1.PayloadCase.VIDEO -> {
                        validateVideo(event.video)
                        val thumbnail = importMedia(
                            event.video.thumbnail,
                            event.video.thumbnailWidth,
                            event.video.thumbnailHeight,
                            available,
                        )
                        val video = importMedia(
                            event.video.video,
                            event.video.width,
                            event.video.height,
                            available,
                            thumbnailMediaId = thumbnail.id,
                            durationMillis = event.video.durationMs,
                        )
                        database.mediaDao().insert(thumbnail)
                        database.mediaDao().insert(video)
                        database.conversationDao().insert(
                            ConversationEventEntity(
                                event.eventId,
                                false,
                                KIND_VIDEO,
                                null,
                                video.id,
                                event.createdAtEpochMs,
                                DELIVERY_DELIVERED,
                            ),
                        )
                        listOf(thumbnail.id, video.id).forEach { mediaId ->
                            database.processedObjectDao().insert(
                                ProcessedObjectEntity(mediaId, System.currentTimeMillis()),
                            )
                            mediaToDelete += mediaId
                        }
                        receiptFor = event.eventId
                    }
                    ConversationEventV1.PayloadCase.DELIVERY_RECEIPT -> {
                        val receipt = event.deliveryReceipt
                        val messageIds = if (receipt.read) {
                            require(receipt.messageIdsCount in 1..READ_RECEIPT_BATCH_SIZE)
                            receipt.messageIdsList.distinct()
                        } else {
                            listOf(receipt.messageId)
                        }
                        messageIds.forEach { messageId ->
                            require(messageId.isNotBlank())
                            database.conversationDao().get(messageId)
                                ?.takeIf {
                                    it.outgoing &&
                                        (receipt.read || it.deliveryState != DELIVERY_READ)
                                }
                                ?.let {
                                    database.conversationDao().updateDelivery(
                                        it.id,
                                        if (receipt.read) DELIVERY_READ else DELIVERY_DELIVERED,
                                    )
                                }
                        }
                    }
                    ConversationEventV1.PayloadCase.READ_RECEIPT -> {
                        require(event.readReceipt.messageIdsCount in 1..READ_RECEIPT_BATCH_SIZE)
                        require(event.readReceipt.readAtEpochMs > 0)
                        event.readReceipt.messageIdsList.distinct().forEach { messageId ->
                            require(messageId.isNotBlank())
                            database.conversationDao().get(messageId)
                                ?.takeIf { it.outgoing }
                                ?.let {
                                    database.conversationDao().updateDelivery(
                                        it.id,
                                        DELIVERY_READ,
                                    )
                                }
                        }
                    }
                    ConversationEventV1.PayloadCase.PAIR_CONFIRMATION -> {
                        val current = requireNotNull(database.pendingPairingDao().get())
                        val invite = InviteV1.parseFrom(current.invite)
                        val response = PairResponseV1.parseFrom(requireNotNull(current.response))
                        require(event.pairConfirmation.inviteId == invite.inviteId)
                        require(
                            MessageDigest.isEqual(
                                event.pairConfirmation.transcriptHash.toByteArray(),
                                transcriptHash(invite, response),
                            ),
                        )
                        database.pendingPairingDao().put(current.copy(remoteConfirmed = true))
                    }
                    ConversationEventV1.PayloadCase.RECOVERY_MANIFEST -> {
                        require(event.recoveryManifest.recoveryId.isNotBlank())
                        require(event.recoveryManifest.eventCount <= MAX_RECOVERY_EVENTS)
                        require(event.recoveryManifest.mediaCount <= MAX_RECOVERY_EVENTS)
                    }
                    ConversationEventV1.PayloadCase.RECOVERY_BATCH -> {
                        require(event.recoveryBatch.recoveryId.isNotBlank())
                        require(event.recoveryBatch.recordsCount in 1..RECOVERY_BATCH_SIZE)
                        event.recoveryBatch.recordsList.forEach { record ->
                            val archived = record.event
                            require(archived.version == 1 && archived.eventId.isNotBlank())
                            if (database.conversationDao().get(archived.eventId) != null) {
                                return@forEach
                            }
                            when (archived.payloadCase) {
                                ConversationEventV1.PayloadCase.TEXT -> {
                                    require(archived.text.text.isNotBlank())
                                    require(archived.text.text.length <= MAX_TEXT_LENGTH)
                                    database.conversationDao().insert(
                                        ConversationEventEntity(
                                            archived.eventId,
                                            record.outgoingOnRecoveringDevice,
                                            KIND_TEXT,
                                            archived.text.text,
                                            null,
                                            archived.createdAtEpochMs,
                                            DELIVERY_DELIVERED,
                                        ),
                                    )
                                }
                                ConversationEventV1.PayloadCase.PHOTO -> {
                                    val archivedMedia = archived.photo
                                    validatePhoto(archivedMedia)
                                    if (archivedMedia.mediaId !in available) {
                                        throw MissingMediaException
                                    }
                                    val mediaBytes = spool.get(archivedMedia.mediaId)
                                    require(mediaBytes.size.toLong() == archivedMedia.encryptedSize)
                                    if (database.mediaDao().get(archivedMedia.mediaId) == null) {
                                        val relativePath = session.media.importCiphertext(
                                            archivedMedia.mediaId,
                                            mediaBytes,
                                            archivedMedia.sha256.toByteArray(),
                                        )
                                        database.mediaDao().insert(
                                            MediaEntity(
                                                archivedMedia.mediaId,
                                                relativePath,
                                                archivedMedia.mimeType,
                                                archivedMedia.width,
                                                archivedMedia.height,
                                                archivedMedia.encryptedSize,
                                                archivedMedia.mediaKey.toByteArray(),
                                                archivedMedia.mediaNonce.toByteArray(),
                                                archivedMedia.sha256.toByteArray(),
                                            ),
                                        )
                                    }
                                    database.conversationDao().insert(
                                        ConversationEventEntity(
                                            archived.eventId,
                                            record.outgoingOnRecoveringDevice,
                                            KIND_PHOTO,
                                            null,
                                            archivedMedia.mediaId,
                                            archived.createdAtEpochMs,
                                            DELIVERY_DELIVERED,
                                        ),
                                    )
                                    database.processedObjectDao().insert(
                                        ProcessedObjectEntity(
                                            archivedMedia.mediaId,
                                            System.currentTimeMillis(),
                                        ),
                                    )
                                    mediaToDelete += archivedMedia.mediaId
                                }
                                ConversationEventV1.PayloadCase.VIDEO -> {
                                    validateVideo(archived.video)
                                    val thumbnail = importMedia(
                                        archived.video.thumbnail,
                                        archived.video.thumbnailWidth,
                                        archived.video.thumbnailHeight,
                                        available,
                                    )
                                    val video = importMedia(
                                        archived.video.video,
                                        archived.video.width,
                                        archived.video.height,
                                        available,
                                        thumbnailMediaId = thumbnail.id,
                                        durationMillis = archived.video.durationMs,
                                    )
                                    if (database.mediaDao().get(thumbnail.id) == null) {
                                        database.mediaDao().insert(thumbnail)
                                    }
                                    if (database.mediaDao().get(video.id) == null) {
                                        database.mediaDao().insert(video)
                                    }
                                    database.conversationDao().insert(
                                        ConversationEventEntity(
                                            archived.eventId,
                                            record.outgoingOnRecoveringDevice,
                                            KIND_VIDEO,
                                            null,
                                            video.id,
                                            archived.createdAtEpochMs,
                                            DELIVERY_DELIVERED,
                                        ),
                                    )
                                    listOf(thumbnail.id, video.id).forEach { mediaId ->
                                        database.processedObjectDao().insert(
                                            ProcessedObjectEntity(
                                                mediaId,
                                                System.currentTimeMillis(),
                                            ),
                                        )
                                        mediaToDelete += mediaId
                                    }
                                }
                                else -> error("Unsupported recovery record")
                            }
                        }
                    }
                    ConversationEventV1.PayloadCase.DEVICE_REVOCATION -> {
                        require(
                            MessageDigest.isEqual(
                                event.deviceRevocation.replacementIdentityKey.toByteArray(),
                                session.signalStore().localIdentityKey(),
                            ),
                        )
                    }
                    else -> error("Unsupported conversation event")
                }
                database.processedObjectDao().insert(
                    ProcessedObjectEntity(objectId, System.currentTimeMillis()),
                )
                }
            }
            if (transaction.exceptionOrNull() === MissingMediaException) return@forEach
            transaction.getOrThrow()
            spool.delete(objectId)
            available.remove(objectId)
            mediaToDelete.forEach {
                spool.delete(it)
                available.remove(it)
            }
            receiptFor?.let { sendReceipt(it) }
        }
    }

    private suspend fun sendReceipt(messageId: String) {
        val pair = database.pairStateDao().get() ?: return
        val eventId = UUID.randomUUID().toString()
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId(eventId)
            .setCreatedAtEpochMs(System.currentTimeMillis())
            .setDeliveryReceipt(
                DeliveryReceiptV1.newBuilder()
                    .setMessageId(messageId)
                    .setStoredAtEpochMs(System.currentTimeMillis()),
            )
            .build()
        val outbox = OutboxEntity(
            eventId,
            "",
            encryptEvent(event, pair.partnerAddressName),
            0,
            System.currentTimeMillis(),
        )
        database.outboxDao().put(outbox)
        runCatching { uploadOutbox(outbox) }
    }

    private suspend fun sendReadReceipt(messageIds: List<String>) {
        val pair = database.pairStateDao().get() ?: return
        val eventId = UUID.randomUUID().toString()
        val event = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId(eventId)
            .setCreatedAtEpochMs(System.currentTimeMillis())
            .setDeliveryReceipt(
                DeliveryReceiptV1.newBuilder()
                    .setMessageId(messageIds.first())
                    .setStoredAtEpochMs(System.currentTimeMillis())
                    .setRead(true)
                    .addAllMessageIds(messageIds),
            )
            .build()
        val outbox = OutboxEntity(
            eventId,
            "",
            encryptEvent(event, pair.partnerAddressName),
            0,
            System.currentTimeMillis(),
        )
        database.outboxDao().put(outbox)
        runCatching { uploadOutbox(outbox) }
    }

    private suspend fun uploadEvent(event: ConversationEventV1, target: PartnerTarget) {
        RelayClient(target.relayUrl).putObject(
            target.mailboxId,
            event.eventId,
            target.writeCapability,
            encryptEvent(event, target.addressName),
        )
    }

    private suspend fun sendRecoveryHistory(
        pair: PairStateEntity,
        revokedIdentityKey: ByteArray?,
    ) {
        val target = pair.partnerTarget()
        val history = database.conversationDao().getAll()
        require(history.size.toLong() <= MAX_RECOVERY_EVENTS)
        val mediaCount = history.sumOf { item ->
            item.mediaId?.let { mediaId ->
                if (database.mediaDao().get(mediaId)?.thumbnailMediaId == null) 1 else 2
            } ?: 0
        }
        val recoveryId = UUID.randomUUID().toString()
        val archiveHash = MessageDigest.getInstance("SHA-256").run {
            history.forEach { item ->
                update(item.id.encodeToByteArray())
                update(if (item.outgoing) 1 else 0)
                update(item.createdAtEpochMillis.toString().encodeToByteArray())
                item.body?.let { update(it.encodeToByteArray()) }
                item.mediaId?.let { mediaId ->
                    update(mediaId.encodeToByteArray())
                    val media = requireNotNull(database.mediaDao().get(mediaId))
                    update(media.cipherSha256)
                    media.thumbnailMediaId?.let { thumbnailId ->
                        update(requireNotNull(database.mediaDao().get(thumbnailId)).cipherSha256)
                    }
                }
            }
            digest()
        }
        val manifestId = UUID.randomUUID().toString()
        uploadEvent(
            ConversationEventV1.newBuilder()
                .setVersion(1)
                .setEventId(manifestId)
                .setCreatedAtEpochMs(System.currentTimeMillis())
                .setRecoveryManifest(
                    RecoveryManifestV1.newBuilder()
                        .setRecoveryId(recoveryId)
                        .setEventCount(history.size.toLong())
                        .setMediaCount(mediaCount.toLong())
                        .setArchiveHash(ByteString.copyFrom(archiveHash)),
                )
                .build(),
            target,
        )
        history.forEachIndexed { index, source ->
            val archived = source.toProtocolEvent()
            source.mediaId?.let { mediaId ->
                val media = requireNotNull(database.mediaDao().get(mediaId))
                val relay = RelayClient(pair.relayUrl)
                uploadMedia(relay, pair, media)
                media.thumbnailMediaId?.let { thumbnailId ->
                    uploadMedia(relay, pair, requireNotNull(database.mediaDao().get(thumbnailId)))
                }
            }
            val batchId = UUID.randomUUID().toString()
            uploadEvent(
                ConversationEventV1.newBuilder()
                    .setVersion(1)
                    .setEventId(batchId)
                    .setCreatedAtEpochMs(System.currentTimeMillis())
                    .setRecoveryBatch(
                        RecoveryBatchV1.newBuilder()
                            .setRecoveryId(recoveryId)
                            .setBatchIndex(index)
                            .setFinalBatch(index == history.lastIndex)
                            .addRecords(
                                RecoveryRecordV1.newBuilder()
                                    .setEvent(archived)
                                    .setOutgoingOnRecoveringDevice(!source.outgoing),
                            ),
                    )
                    .build(),
                target,
            )
        }
        if (revokedIdentityKey != null) {
            val revocationId = UUID.randomUUID().toString()
            uploadEvent(
                ConversationEventV1.newBuilder()
                    .setVersion(1)
                    .setEventId(revocationId)
                    .setCreatedAtEpochMs(System.currentTimeMillis())
                    .setDeviceRevocation(
                        DeviceRevocationV1.newBuilder()
                            .setRevokedIdentityKey(ByteString.copyFrom(revokedIdentityKey))
                            .setReplacementIdentityKey(ByteString.copyFrom(pair.partnerIdentityKey))
                            .setRevokedAtEpochMs(System.currentTimeMillis()),
                    )
                    .build(),
                target,
            )
        }
    }

    private fun ConversationEventEntity.toProtocolEvent(): ConversationEventV1 {
        val builder = ConversationEventV1.newBuilder()
            .setVersion(1)
            .setEventId(id)
            .setCreatedAtEpochMs(createdAtEpochMillis)
        return when (kind) {
            KIND_TEXT -> builder.setText(TextMessageV1.newBuilder().setText(body.orEmpty())).build()
            KIND_PHOTO -> {
                val media = requireNotNull(database.mediaDao().get(requireNotNull(mediaId)))
                builder.setPhoto(
                    PhotoMessageV1.newBuilder()
                        .setMediaId(media.id)
                        .setMimeType(media.mimeType)
                        .setWidth(media.width)
                        .setHeight(media.height)
                        .setEncryptedSize(media.encryptedSize)
                        .setMediaKey(ByteString.copyFrom(media.key))
                        .setMediaNonce(ByteString.copyFrom(media.nonce))
                        .setSha256(ByteString.copyFrom(media.cipherSha256)),
                ).build()
            }
            KIND_VIDEO -> {
                val media = requireNotNull(database.mediaDao().get(requireNotNull(mediaId)))
                val thumbnail = requireNotNull(
                    media.thumbnailMediaId?.let(database.mediaDao()::get),
                )
                builder.setVideo(
                    VideoMessageV1.newBuilder()
                        .setVideo(media.toProtocolMedia())
                        .setWidth(media.width)
                        .setHeight(media.height)
                        .setDurationMs(media.durationMillis)
                        .setThumbnail(thumbnail.toProtocolMedia())
                        .setThumbnailWidth(thumbnail.width)
                        .setThumbnailHeight(thumbnail.height),
                ).build()
            }
            else -> error("Unsupported local conversation event")
        }
    }

    private fun encryptEvent(event: ConversationEventV1, remoteAddress: String): ByteArray {
        val profile = requireNotNull(database.profileDao().get())
        val encrypted = session.signalSession(profile.addressName).encrypt(
            remoteAddress,
            event.toByteArray(),
        )
        return EnvelopeV1.newBuilder()
            .setVersion(1)
            .setMessageId(event.eventId)
            .setSignalMessageType(encrypted.type)
            .setSignalMessage(ByteString.copyFrom(encrypted.bytes))
            .setCreatedAtEpochMs(System.currentTimeMillis())
            .build()
            .toByteArray()
    }

    private suspend fun finalizePairIfReady() {
        val pending = database.pendingPairingDao().get() ?: return
        if (!pending.localConfirmed || !pending.remoteConfirmed) return
        val target = pending.partnerTarget()
        val invite = InviteV1.parseFrom(pending.invite)
        val pair = PairStateEntity(
            partnerName = target.displayName,
            partnerAddressName = target.addressName,
            relayUrl = target.relayUrl,
            ownMailboxId = pending.ownMailboxId,
            ownReadCapability = pending.ownReadCapability,
            partnerMailboxId = target.mailboxId,
            partnerWriteCapability = target.writeCapability,
            safetyWords = requireNotNull(pending.safetyWords),
            pairedAtEpochMillis = System.currentTimeMillis(),
            partnerIdentityKey = target.identityKey,
        )
        database.pairStateDao().put(pair)
        database.pendingPairingDao().delete()
        if (pending.role == ROLE_INVITER) {
            runCatching {
                RelayClient(invite.mailbox.relayUrl).deleteRendezvous(
                    invite.inviteId,
                    invite.rendezvousCapability.toByteArray(),
                )
            }
            runCatching {
                RelayClient(invite.mailbox.relayUrl).deleteRendezvous(
                    invite.inviteDeliveryId,
                    invite.inviteDeliveryCapability.toByteArray(),
                )
            }
        }
        TransportSyncWorker.schedulePeriodic(context)
        requestPushToken()
        if (pending.recovery && pending.role == ROLE_INVITER) {
            sendRecoveryHistory(pair, pending.recoveryOldIdentityKey)
        }
    }

    private fun PairStateEntity.partnerTarget() = PartnerTarget(
        partnerName,
        partnerAddressName,
        partnerIdentityKey,
        relayUrl,
        partnerMailboxId,
        partnerWriteCapability,
    )

    private fun requestPushToken() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        FirebaseMessaging.getInstance().register().addOnSuccessListener {
            FirebaseInstallations.getInstance().id.addOnSuccessListener { installationId ->
                transportCredentials.putPendingPushToken(installationId)
                TransportSyncWorker.scheduleNow(context)
            }
        }
    }

    private fun PendingPairingEntity.partnerTarget(): PartnerTarget {
        val invite = InviteV1.parseFrom(invite)
        val response = response?.let(PairResponseV1::parseFrom)
        return if (role == ROLE_INVITER) {
            requireNotNull(response).let {
                PartnerTarget(
                    it.displayName,
                    it.preKeyBundle.addressName,
                    it.preKeyBundle.identityKey.toByteArray(),
                    it.mailbox.relayUrl,
                    it.mailbox.mailboxId,
                    it.mailbox.writeCapability.toByteArray(),
                )
            }
        } else {
            PartnerTarget(
                invite.displayName,
                invite.preKeyBundle.addressName,
                invite.preKeyBundle.identityKey.toByteArray(),
                invite.mailbox.relayUrl,
                invite.mailbox.mailboxId,
                invite.mailbox.writeCapability.toByteArray(),
            )
        }
    }

    private fun PendingPairingEntity.toSnapshot(): PairingSnapshot {
        val invite = InviteV1.parseFrom(invite)
        return PairingSnapshot(
            role = role,
            mode = PairingMode.valueOf(mode),
            invitation = PairingPayloadCodec.qr(invite),
            remoteLink = PairingPayloadCodec.remoteLink(invite),
            response = response?.let { PairingPayloadCodec.responseReference(invite.inviteId) },
            safetyWords = safetyWords,
            localConfirmed = localConfirmed,
            remoteConfirmed = remoteConfirmed,
            recovery = recovery,
        )
    }

    private fun safetyWords(first: ByteArray, second: ByteArray, nonce: ByteArray): String =
        SafetyWords.render(
            SafetyWords.indices(first, second, nonce),
            context.resources.openRawResource(R.raw.bip39_english).bufferedReader().useLines {
                it.toList()
            },
        )

    private fun transcriptHash(invite: InviteV1, response: PairResponseV1): ByteArray =
        MessageDigest.getInstance("SHA-256").run {
            update("LoveDovesPairingTranscriptV1".encodeToByteArray())
            update(invite.toByteArray())
            digest(response.toByteArray())
        }

    private fun validateRelay(invite: InviteV1) {
        val now = System.currentTimeMillis()
        require(invite.version == 1)
        require(invite.expiresAtEpochMs in now..(now + INVITE_LIFETIME_MS)) {
            "Die Einladung ist abgelaufen."
        }
        require(invite.nonce.size() == CAPABILITY_BYTES)
        require(invite.mailbox.relayUrl == BuildConfig.RELAY_URL) {
            "Die Einladung verwendet nicht den erwarteten Love-Doves-Relay."
        }
        require(invite.mailbox.writeCapability.size() == CAPABILITY_BYTES)
        require(invite.rendezvousSecret.size() == CAPABILITY_BYTES)
        require(invite.rendezvousCapability.size() == CAPABILITY_BYTES)
        require(invite.relayEnrollmentCapability.size() == CAPABILITY_BYTES)
        require(invite.preKeyBundle.addressName.isNotBlank())
        require(invite.preKeyBundle.identityKey.size() > CAPABILITY_BYTES)
    }

    private fun validatePhoto(photo: PhotoMessageV1) {
        require(photo.mediaId.matches(UUID_PATTERN))
        require(photo.mimeType == "image/jpeg")
        require(photo.width in 1..MAX_PHOTO_EDGE && photo.height in 1..MAX_PHOTO_EDGE)
        require(photo.encryptedSize in MIN_ENCRYPTED_PHOTO_BYTES..EncryptedMediaStore.MAX_CIPHERTEXT_BYTES.toLong())
        require(photo.mediaKey.size() == PHOTO_KEY_BYTES)
        require(photo.mediaNonce.size() == PHOTO_NONCE_BYTES)
        require(photo.sha256.size() == SHA256_BYTES)
    }

    private fun validateVideo(video: VideoMessageV1) {
        validateMedia(video.video, "video/mp4")
        validateMedia(video.thumbnail, "image/jpeg")
        require(video.video.mediaId != video.thumbnail.mediaId)
        require(video.width in 1..MAX_VIDEO_EDGE && video.height in 1..MAX_VIDEO_EDGE)
        require(video.thumbnailWidth in 1..MAX_PHOTO_EDGE)
        require(video.thumbnailHeight in 1..MAX_PHOTO_EDGE)
        require(video.durationMs in 1..MAX_VIDEO_DURATION_MILLIS)
    }

    private fun validateMedia(media: EncryptedMediaV1, mimeType: String) {
        require(media.mediaId.matches(UUID_PATTERN))
        require(media.mimeType == mimeType)
        require(
            media.encryptedSize in
                MIN_ENCRYPTED_PHOTO_BYTES..EncryptedMediaStore.MAX_CIPHERTEXT_BYTES.toLong(),
        )
        require(media.mediaKey.size() == PHOTO_KEY_BYTES)
        require(media.mediaNonce.size() == PHOTO_NONCE_BYTES)
        require(media.sha256.size() == SHA256_BYTES)
    }

    private fun importMedia(
        media: EncryptedMediaV1,
        width: Int,
        height: Int,
        available: Set<String>,
        thumbnailMediaId: String? = null,
        durationMillis: Long = 0L,
    ): MediaEntity {
        if (media.mediaId !in available) throw MissingMediaException
        val mediaBytes = spool.get(media.mediaId)
        require(mediaBytes.size.toLong() == media.encryptedSize)
        val relativePath = session.media.importCiphertext(
            media.mediaId,
            mediaBytes,
            media.sha256.toByteArray(),
        )
        return MediaEntity(
            id = media.mediaId,
            relativePath = relativePath,
            mimeType = media.mimeType,
            width = width,
            height = height,
            encryptedSize = media.encryptedSize,
            key = media.mediaKey.toByteArray(),
            nonce = media.mediaNonce.toByteArray(),
            cipherSha256 = media.sha256.toByteArray(),
            thumbnailMediaId = thumbnailMediaId,
            durationMillis = durationMillis,
        )
    }

    private fun storeTransportCredentials(relayUrl: String, mailboxId: String, read: ByteArray) {
        transportCredentials.put(TransportCredentials(relayUrl, mailboxId, read))
        TransportSyncWorker.schedulePeriodic(context)
    }

    private fun EncryptedMedia.toEntity(
        width: Int,
        height: Int,
        mimeType: String = "image/jpeg",
        thumbnailMediaId: String? = null,
        durationMillis: Long = 0L,
    ): MediaEntity = MediaEntity(
        id = id,
        relativePath = relativePath,
        mimeType = mimeType,
        width = width,
        height = height,
        encryptedSize = encryptedSize,
        key = key.copyOf(),
        nonce = nonce,
        cipherSha256 = cipherSha256,
        thumbnailMediaId = thumbnailMediaId,
        durationMillis = durationMillis,
    )

    private fun EncryptedMedia.toProtocolMedia(mimeType: String): EncryptedMediaV1 =
        EncryptedMediaV1.newBuilder()
            .setMediaId(id)
            .setMimeType(mimeType)
            .setEncryptedSize(encryptedSize)
            .setMediaKey(ByteString.copyFrom(key))
            .setMediaNonce(ByteString.copyFrom(nonce))
            .setSha256(ByteString.copyFrom(cipherSha256))
            .build()

    private fun MediaEntity.toProtocolMedia(): EncryptedMediaV1 =
        EncryptedMediaV1.newBuilder()
            .setMediaId(id)
            .setMimeType(mimeType)
            .setEncryptedSize(encryptedSize)
            .setMediaKey(ByteString.copyFrom(key))
            .setMediaNonce(ByteString.copyFrom(nonce))
            .setSha256(ByteString.copyFrom(cipherSha256))
            .build()

    private fun MediaEntity.toEncryptedMedia(): EncryptedMedia = EncryptedMedia(
        id,
        relativePath,
        key,
        nonce,
        encryptedSize,
        cipherSha256,
    )

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }

    private data class PartnerTarget(
        val displayName: String,
        val addressName: String,
        val identityKey: ByteArray,
        val relayUrl: String,
        val mailboxId: String,
        val writeCapability: ByteArray,
    )

    companion object {
        private object MissingMediaException : RuntimeException()
        const val ROLE_INVITER = "INVITER"
        const val ROLE_JOINER = "JOINER"
        const val KIND_TEXT = "TEXT"
        const val KIND_PHOTO = "PHOTO"
        const val KIND_VIDEO = "VIDEO"
        const val DELIVERY_SENDING = "SENDING"
        const val DELIVERY_SENT = "SENT"
        const val DELIVERY_DELIVERED = "DELIVERED"
        const val DELIVERY_READ = "READ"
        const val DELIVERY_FAILED = "FAILED"
        const val MAX_TEXT_LENGTH = 4_000
        const val MAX_PHOTO_BYTES = EncryptedMediaStore.MAX_PLAINTEXT_BYTES
        const val MAX_VIDEO_BYTES = EncryptedMediaStore.MAX_PLAINTEXT_BYTES
        private const val CAPABILITY_BYTES = 32
        private const val MAX_SIGNAL_MESSAGE_BYTES = 512 * 1024
        private const val MAX_PHOTO_EDGE = 4_096
        private const val MAX_VIDEO_EDGE = 8_192
        private const val MAX_VIDEO_DURATION_MILLIS = 24L * 60 * 60 * 1_000
        private const val PHOTO_KEY_BYTES = 32
        private const val PHOTO_NONCE_BYTES = 12
        private const val SHA256_BYTES = 32
        private const val MIN_ENCRYPTED_PHOTO_BYTES = 4L + PHOTO_NONCE_BYTES + 16L
        private const val INVITE_LIFETIME_MS = 10 * 60 * 1_000L
        private const val RECOVERY_BATCH_SIZE = 1
        private const val READ_RECEIPT_BATCH_SIZE = 256
        private const val MAX_LOCAL_DELETE_BATCH = 1_000
        private const val MAX_RECOVERY_EVENTS = 1_000_000L
        private val UUID_PATTERN = Regex("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

        private fun randomBytes(size: Int): ByteArray =
            ByteArray(size).also(SecureRandom()::nextBytes)

        private fun randomId(): String = CapabilityCodec.encode(randomBytes(18))

    }
}
