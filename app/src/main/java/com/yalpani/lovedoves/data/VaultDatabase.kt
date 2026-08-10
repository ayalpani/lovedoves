package com.yalpani.lovedoves.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Entity(tableName = "local_profile")
internal data class LocalProfileEntity(
    @PrimaryKey val id: Int = 1,
    val displayName: String,
    val addressName: String,
    val createdAtEpochMillis: Long,
)

@Entity(tableName = "pair_state")
internal data class PairStateEntity(
    @PrimaryKey val id: Int = 1,
    val partnerName: String,
    val partnerAddressName: String,
    val relayUrl: String,
    val ownMailboxId: String,
    val ownReadCapability: ByteArray,
    val partnerMailboxId: String,
    val partnerWriteCapability: ByteArray,
    val safetyWords: String,
    val pairedAtEpochMillis: Long,
    val partnerIdentityKey: ByteArray,
)

@Entity(tableName = "pending_pairing")
internal data class PendingPairingEntity(
    @PrimaryKey val id: Int = 1,
    val role: String,
    val mode: String,
    val invite: ByteArray,
    val response: ByteArray?,
    val ownMailboxId: String,
    val ownReadCapability: ByteArray,
    val safetyWords: String?,
    val localConfirmed: Boolean,
    val remoteConfirmed: Boolean,
    val recovery: Boolean,
    val recoveryOldIdentityKey: ByteArray?,
)

@Entity(tableName = "conversation_events")
internal data class ConversationEventEntity(
    @PrimaryKey val id: String,
    val outgoing: Boolean,
    val kind: String,
    val body: String?,
    val mediaId: String?,
    val createdAtEpochMillis: Long,
    val deliveryState: String,
)

@Entity(tableName = "media")
internal data class MediaEntity(
    @PrimaryKey val id: String,
    val relativePath: String,
    val mimeType: String,
    val width: Int,
    val height: Int,
    val encryptedSize: Long,
    val key: ByteArray,
    val nonce: ByteArray,
    val cipherSha256: ByteArray,
    val thumbnailMediaId: String? = null,
    val durationMillis: Long = 0L,
)

@Entity(tableName = "outbox")
internal data class OutboxEntity(
    @PrimaryKey val objectId: String,
    val eventId: String,
    val encryptedEnvelope: ByteArray,
    val attempts: Int,
    val nextAttemptAtEpochMillis: Long,
)

@Entity(tableName = "signal_records", primaryKeys = ["kind", "recordKey"])
internal data class SignalRecordEntity(
    val kind: String,
    val recordKey: String,
    val payload: ByteArray,
)

@Entity(tableName = "processed_objects")
internal data class ProcessedObjectEntity(
    @PrimaryKey val objectId: String,
    val processedAtEpochMillis: Long,
)

@Dao
internal interface ProfileDao {
    @Query("SELECT * FROM local_profile WHERE id = 1")
    fun get(): LocalProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(profile: LocalProfileEntity)
}

@Dao
internal interface PairStateDao {
    @Query("SELECT * FROM pair_state WHERE id = 1")
    fun get(): PairStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(pairState: PairStateEntity)

    @Query("DELETE FROM pair_state")
    fun delete()
}

@Dao
internal interface PendingPairingDao {
    @Query("SELECT * FROM pending_pairing WHERE id = 1")
    fun get(): PendingPairingEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(pending: PendingPairingEntity)

    @Query("DELETE FROM pending_pairing")
    fun delete()
}

@Dao
internal interface ConversationDao {
    @Query("SELECT * FROM conversation_events ORDER BY createdAtEpochMillis, id")
    fun observeAll(): Flow<List<ConversationEventEntity>>

    @Query("SELECT * FROM conversation_events ORDER BY createdAtEpochMillis, id")
    fun getAll(): List<ConversationEventEntity>

    @Query("SELECT * FROM conversation_events WHERE id = :id")
    fun get(id: String): ConversationEventEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(event: ConversationEventEntity): Long

    @Query("UPDATE conversation_events SET deliveryState = :state WHERE id = :id")
    fun updateDelivery(id: String, state: String)

    @Query("DELETE FROM conversation_events")
    fun deleteAll()
}

@Dao
internal interface MediaDao {
    @Query("SELECT * FROM media WHERE id = :id")
    fun get(id: String): MediaEntity?

    @Query("SELECT * FROM media ORDER BY id")
    fun getAll(): List<MediaEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insert(media: MediaEntity)

    @Query("DELETE FROM media")
    fun deleteAll()
}

@Dao
internal interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE objectId = :objectId")
    fun get(objectId: String): OutboxEntity?

    @Query("SELECT * FROM outbox WHERE nextAttemptAtEpochMillis <= :now ORDER BY nextAttemptAtEpochMillis")
    fun ready(now: Long): List<OutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(item: OutboxEntity)

    @Query("DELETE FROM outbox WHERE objectId = :objectId")
    fun delete(objectId: String)

    @Query("DELETE FROM outbox")
    fun deleteAll()
}

@Dao
internal interface SignalRecordDao {
    @Query("SELECT payload FROM signal_records WHERE kind = :kind AND recordKey = :recordKey")
    fun get(kind: String, recordKey: String): ByteArray?

    @Query("SELECT recordKey FROM signal_records WHERE kind = :kind")
    fun keys(kind: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun put(record: SignalRecordEntity)

    @Query("DELETE FROM signal_records WHERE kind = :kind AND recordKey = :recordKey")
    fun delete(kind: String, recordKey: String)

    @Query("DELETE FROM signal_records WHERE kind = :kind AND recordKey LIKE :prefix || '%'")
    fun deleteWithPrefix(kind: String, prefix: String)

    @Query("DELETE FROM signal_records")
    fun deleteAll()
}

@Dao
internal interface ProcessedObjectDao {
    @Query("SELECT EXISTS(SELECT 1 FROM processed_objects WHERE objectId = :objectId)")
    fun contains(objectId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insert(processed: ProcessedObjectEntity): Long

    @Query("DELETE FROM processed_objects")
    fun deleteAll()
}

@Database(
    entities = [
        LocalProfileEntity::class,
        PairStateEntity::class,
        PendingPairingEntity::class,
        ConversationEventEntity::class,
        MediaEntity::class,
        OutboxEntity::class,
        SignalRecordEntity::class,
        ProcessedObjectEntity::class,
    ],
    version = 5,
    exportSchema = true,
)
internal abstract class VaultDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao
    abstract fun pairStateDao(): PairStateDao
    abstract fun pendingPairingDao(): PendingPairingDao
    abstract fun conversationDao(): ConversationDao
    abstract fun mediaDao(): MediaDao
    abstract fun outboxDao(): OutboxDao
    abstract fun signalRecordDao(): SignalRecordDao
    abstract fun processedObjectDao(): ProcessedObjectDao

    companion object {
        fun open(context: Context, passphrase: ByteArray): VaultDatabase {
            System.loadLibrary("sqlcipher")
            val factory = SupportOpenHelperFactory(passphrase.copyOf())
            return Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                "love-doves-vault.db",
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pair_state ADD COLUMN partnerIdentityKey BLOB NOT NULL DEFAULT X''",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS pending_pairing (
                        id INTEGER NOT NULL,
                        role TEXT NOT NULL,
                        mode TEXT NOT NULL,
                        invite BLOB NOT NULL,
                        response BLOB,
                        ownMailboxId TEXT NOT NULL,
                        ownReadCapability BLOB NOT NULL,
                        safetyWords TEXT,
                        localConfirmed INTEGER NOT NULL,
                        remoteConfirmed INTEGER NOT NULL,
                        PRIMARY KEY(id)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS processed_objects (
                        objectId TEXT NOT NULL,
                        processedAtEpochMillis INTEGER NOT NULL,
                        PRIMARY KEY(objectId)
                    )
                    """.trimIndent(),
                )
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE pending_pairing ADD COLUMN recovery INTEGER NOT NULL DEFAULT 0",
                )
                db.execSQL(
                    "ALTER TABLE pending_pairing ADD COLUMN recoveryOldIdentityKey BLOB",
                )
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE media ADD COLUMN thumbnailMediaId TEXT")
                db.execSQL(
                    "ALTER TABLE media ADD COLUMN durationMillis INTEGER NOT NULL DEFAULT 0",
                )
            }
        }
    }
}
