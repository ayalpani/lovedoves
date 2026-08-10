package com.yalpani.lovedoves.domain

import android.app.Application
import com.yalpani.lovedoves.BuildConfig
import com.yalpani.lovedoves.VaultSession
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.data.PairStateEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal sealed interface AppContentState {
    data object Loading : AppContentState
    data object ProfileSetup : AppContentState
    data object PairingHome : AppContentState
    data class Pairing(val snapshot: PairingSnapshot) : AppContentState
    data class Conversation(
        val pair: PairStateEntity,
        val messages: List<ConversationEventEntity>,
    ) : AppContentState
}

internal class LoveDovesController(
    application: Application,
    session: VaultSession,
) : AutoCloseable {
    private val repository = LoveDovesRepository(application, session)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val repositoryMutex = Mutex()
    private var messagesJob: Job? = null
    private var foregroundSyncJob: Job? = null
    private val mutableContent = MutableStateFlow<AppContentState>(AppContentState.Loading)
    private val mutableBusy = MutableStateFlow(false)
    private val mutableError = MutableStateFlow<String?>(null)
    val content: StateFlow<AppContentState> = mutableContent.asStateFlow()
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()
    val error: StateFlow<String?> = mutableError.asStateFlow()

    init {
        refresh(sync = true)
    }

    fun saveProfile(name: String) = action {
        repository.saveProfile(name)
        refreshNow(sync = false)
    }

    fun createInvitation(mode: PairingMode, bootstrapToken: String) = action {
        mutableContent.value = AppContentState.Pairing(
            repository.createInvitation(mode, bootstrapToken),
        )
    }

    fun createRecoveryInvitation(mode: PairingMode) = action {
        mutableContent.value = AppContentState.Pairing(
            repository.createRecoveryInvitation(mode),
        )
    }

    fun acceptPayload(payload: String) = action {
        val snapshot = if (payload.startsWith("lovedoves://response/v1/")) {
            repository.acceptPairResponse(payload)
        } else {
            repository.joinInvitation(payload)
        }
        mutableContent.value = AppContentState.Pairing(snapshot)
    }

    fun fetchRemoteResponse() = action {
        mutableContent.value = AppContentState.Pairing(repository.fetchRemoteResponse())
    }

    fun confirmPairing() = action {
        repository.confirmPairing()
        refreshNow(sync = true)
    }

    fun cancelPairing() = action {
        repository.cancelPairing()
        refreshNow(sync = false)
    }

    fun sendText(text: String) = action {
        repository.sendText(text)
    }

    fun sendPhoto(photo: PreparedPhoto) = action {
        repository.sendPhoto(photo)
    }

    fun retryMessage(id: String) = action {
        repository.retryMessage(id)
    }

    fun sync() = action {
        repository.syncNow()
        refreshNow(sync = false)
    }

    suspend fun photoBytes(mediaId: String): ByteArray = repository.photoBytes(mediaId)

    fun prepareDelete(onPrepared: suspend () -> Unit) = action {
        repository.deletePairAndLocalData()
        onPrepared()
    }

    fun clearError() {
        mutableError.value = null
    }

    private fun refresh(sync: Boolean) {
        scope.launch { repositoryMutex.withLock { refreshNow(sync) } }
    }

    private suspend fun refreshNow(sync: Boolean) {
        messagesJob?.cancel()
        foregroundSyncJob?.cancel()
        val profile = repository.profile()
        if (profile == null) {
            mutableContent.value = AppContentState.ProfileSetup
            return
        }
        if (sync) runCatching { repository.syncNow() }
        val pending = repository.pairingSnapshot()
        if (pending != null) {
            mutableContent.value = AppContentState.Pairing(pending)
            return
        }
        val pair = repository.pairState()
        if (pair == null) {
            mutableContent.value = AppContentState.PairingHome
            return
        }
        messagesJob = scope.launch {
            repository.messages().collect { messages ->
                mutableContent.value = AppContentState.Conversation(pair, messages)
            }
        }
        foregroundSyncJob = scope.launch {
            while (isActive) {
                delay(FOREGROUND_SYNC_INTERVAL_MILLIS)
                runCatching {
                    repositoryMutex.withLock { repository.syncNow() }
                }
            }
        }
    }

    private fun action(block: suspend () -> Unit) {
        if (mutableBusy.value) return
        mutableBusy.value = true
        mutableError.value = null
        scope.launch {
            runCatching { repositoryMutex.withLock { block() } }
                .onFailure {
                    mutableError.value = if (BuildConfig.DEBUG) {
                        "${it.javaClass.simpleName}: ${it.message ?: "ohne Detail"}"
                    } else {
                        it.message ?: "Etwas ist schiefgegangen."
                    }
                }
            mutableBusy.value = false
        }
    }

    override fun close() {
        scope.cancel()
    }

    private companion object {
        const val FOREGROUND_SYNC_INTERVAL_MILLIS = 2_000L
    }
}
