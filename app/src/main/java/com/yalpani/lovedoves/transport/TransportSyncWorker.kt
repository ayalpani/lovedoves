package com.yalpani.lovedoves.transport

import android.annotation.SuppressLint
import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.yalpani.lovedoves.R
import com.yalpani.lovedoves.MainActivity
import java.util.concurrent.TimeUnit

internal class TransportSyncWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val credentialStore = TransportCredentialStore(applicationContext)
        val credentials = runCatching(credentialStore::get).getOrNull() ?: return Result.success()
        val relay = runCatching { RelayClient(credentials.relayUrl) }.getOrElse {
            return Result.failure()
        }
        return runCatching {
            credentialStore.pendingPushToken()?.let { token ->
                relay.updatePushToken(credentials.mailboxId, credentials.readCapability, token)
                credentialStore.clearPendingPushToken()
            }
            val spool = InboundSpool(applicationContext)
            var received = 0
            relay.listObjects(credentials.mailboxId, credentials.readCapability).forEach { item ->
                if (!spool.contains(item.objectId)) {
                    val bytes = relay.getObject(
                        credentials.mailboxId,
                        item.objectId,
                        credentials.readCapability,
                    )
                    spool.put(item.objectId, bytes, item.cipherSha256Hex)
                    received++
                }
                relay.acknowledge(credentials.mailboxId, item.objectId, credentials.readCapability)
            }
            if (received > 0) notifyGenericActivity()
            Result.success()
        }.getOrElse { Result.retry() }
    }

    private fun notifyGenericActivity() {
        if (ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            return
        }
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL,
                "Nachrichten",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Inhaltslose Hinweise auf neue verschlüsselte Nachrichten"
                setShowBadge(true)
            },
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL)
            .setSmallIcon(R.drawable.ic_notification_heart)
            .setContentTitle("Love Doves")
            .setContentText("Neue Nachricht")
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    0,
                    Intent(applicationContext, MainActivity::class.java).addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP,
                    ),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val ONE_TIME_WORK = "love-doves-incoming-sync"
        private const val PERIODIC_WORK = "love-doves-periodic-sync"
        private const val NOTIFICATION_CHANNEL = "love-doves-activity"
        private const val NOTIFICATION_ID = 1001

        fun scheduleNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<TransportSyncWorker>()
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME_WORK,
                ExistingWorkPolicy.KEEP,
                request,
            )
        }

        fun schedulePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<TransportSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints(requiredNetworkType = NetworkType.CONNECTED))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(ONE_TIME_WORK)
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC_WORK)
        }
    }
}

@SuppressLint("MissingFirebaseInstanceTokenRefresh") // FCM 25 uses onRegistered with the installation ID.
class LoveDovesMessagingService : FirebaseMessagingService() {
    override fun onMessageReceived(message: RemoteMessage) {
        if (message.data["wake"] == "1") {
            TransportSyncWorker.scheduleNow(this)
        }
    }

    override fun onRegistered(installationId: String) {
        TransportCredentialStore(this).putPendingPushToken(installationId)
        TransportSyncWorker.scheduleNow(this)
    }
}
