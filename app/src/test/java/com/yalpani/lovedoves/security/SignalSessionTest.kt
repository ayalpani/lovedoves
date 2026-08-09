package com.yalpani.lovedoves.security

import com.yalpani.lovedoves.data.SignalRecordDao
import com.yalpani.lovedoves.data.SignalRecordEntity
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class SignalSessionTest {
    @Test
    fun preKeyMessageCreatesDurableBidirectionalSession() {
        val aliceDao = MemorySignalRecordDao()
        val bobDao = MemorySignalRecordDao()
        val aliceStore = RoomSignalProtocolStore.open(aliceDao)
        val bobStore = RoomSignalProtocolStore.open(bobDao)
        val aliceAddress = "alice-test-address"
        val bobAddress = "bob-test-address"
        val bobBundle = bobStore.createPublicBundle(bobAddress)
        val alice = SignalSession(aliceStore, aliceAddress)
        val bob = SignalSession(bobStore, bobAddress)

        alice.establish(bobBundle)
        val hello = "only us".encodeToByteArray()
        val firstMessage = alice.encrypt(bobAddress, hello)
        val received = bob.decrypt(aliceAddress, firstMessage.type, firstMessage.bytes)
        val reply = bob.encrypt(aliceAddress, "always".encodeToByteArray())
        val receivedReply = alice.decrypt(bobAddress, reply.type, reply.bytes)

        assertArrayEquals(hello, received)
        assertArrayEquals("always".encodeToByteArray(), receivedReply)
        assertEquals(3, firstMessage.type)
        assertEquals(2, reply.type)
    }
}

private class MemorySignalRecordDao : SignalRecordDao {
    private val records = mutableMapOf<Pair<String, String>, ByteArray>()

    override fun get(kind: String, recordKey: String): ByteArray? =
        records[kind to recordKey]?.copyOf()

    override fun keys(kind: String): List<String> = records.keys
        .filter { it.first == kind }
        .map { it.second }

    override fun put(record: SignalRecordEntity) {
        records[record.kind to record.recordKey] = record.payload.copyOf()
    }

    override fun delete(kind: String, recordKey: String) {
        records.remove(kind to recordKey)
    }

    override fun deleteWithPrefix(kind: String, prefix: String) {
        records.keys.filter { it.first == kind && it.second.startsWith(prefix) }
            .forEach(records::remove)
    }

    override fun deleteAll() {
        records.clear()
    }
}
