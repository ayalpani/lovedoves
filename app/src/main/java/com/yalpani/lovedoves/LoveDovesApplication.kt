package com.yalpani.lovedoves

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.yalpani.lovedoves.data.VaultDatabase
import com.yalpani.lovedoves.security.EncryptedMediaStore
import com.yalpani.lovedoves.security.RoomSignalProtocolStore
import com.yalpani.lovedoves.security.SignalSession
import com.yalpani.lovedoves.security.VaultKey
import com.yalpani.lovedoves.security.VaultKeyStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions

class LoveDovesApplication : Application(), DefaultLifecycleObserver {
    internal lateinit var vaults: VaultSessionManager
        private set

    override fun onCreate() {
        super<Application>.onCreate()
        initializeFirebase()
        vaults = VaultSessionManager(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        vaults.lock()
    }

    private fun initializeFirebase() {
        if (
            BuildConfig.FIREBASE_APP_ID.isBlank() ||
            BuildConfig.FIREBASE_API_KEY.isBlank() ||
            BuildConfig.FIREBASE_PROJECT_ID.isBlank() ||
            BuildConfig.FIREBASE_SENDER_ID.isBlank()
        ) return
        FirebaseApp.initializeApp(
            this,
            FirebaseOptions.Builder()
                .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                .setApiKey(BuildConfig.FIREBASE_API_KEY)
                .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                .setGcmSenderId(BuildConfig.FIREBASE_SENDER_ID)
                .build(),
        )
    }
}

internal sealed interface VaultState {
    data class Locked(val hasVault: Boolean) : VaultState
    data object Opening : VaultState
    data class Open(val session: VaultSession) : VaultState
    data class Failed(val message: String) : VaultState
}

internal class VaultSession(
    val database: VaultDatabase,
    val media: EncryptedMediaStore,
    private val vaultKey: VaultKey,
) : AutoCloseable {
    fun signalSession(localAddressName: String): SignalSession = SignalSession(
        RoomSignalProtocolStore.open(database.signalRecordDao()),
        localAddressName,
    )

    fun signalStore(): RoomSignalProtocolStore =
        RoomSignalProtocolStore.open(database.signalRecordDao())

    override fun close() {
        database.close()
        vaultKey.close()
    }
}

internal class VaultSessionManager(private val application: Application) {
    val keyStore = VaultKeyStore(application)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow<VaultState>(VaultState.Locked(keyStore.hasVault))
    val state: StateFlow<VaultState> = mutableState.asStateFlow()

    suspend fun unlock(vaultKey: VaultKey) {
        mutex.withLock {
            val previous = (mutableState.value as? VaultState.Open)?.session
            mutableState.value = VaultState.Opening
            previous?.close()
            runCatching {
                val passphrase = vaultKey.copyBytes()
                val database = try {
                    VaultDatabase.open(application, passphrase).also {
                        it.openHelper.writableDatabase
                    }
                } finally {
                    passphrase.fill(0)
                }
                VaultSession(
                    database = database,
                    media = EncryptedMediaStore(application),
                    vaultKey = vaultKey,
                )
            }.onSuccess { session ->
                mutableState.value = VaultState.Open(session)
            }.onFailure { failure ->
                vaultKey.close()
                mutableState.value = VaultState.Failed(
                    failure.message ?: "Der private Speicher konnte nicht geöffnet werden.",
                )
            }
        }
    }

    fun lock() {
        val session = (mutableState.value as? VaultState.Open)?.session ?: run {
            if (mutableState.value !is VaultState.Opening) {
                mutableState.value = VaultState.Locked(keyStore.hasVault)
            }
            return
        }
        mutableState.value = VaultState.Locked(keyStore.hasVault)
        scope.launch {
            mutex.withLock { session.close() }
        }
    }

    suspend fun deleteAll() {
        val session = (mutableState.value as? VaultState.Open)?.session
        mutableState.value = VaultState.Locked(hasVault = false)
        mutex.withLock {
            session?.media?.deleteAll()
            session?.close()
            application.getDatabasePath("love-doves-vault.db").let { database ->
                database.delete()
                java.io.File(database.path + "-wal").delete()
                java.io.File(database.path + "-shm").delete()
            }
            keyStore.deleteVaultKey()
        }
    }
}
