package com.yalpani.lovedoves.transport

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64

internal data class RelayMailboxCredentials(
    val mailboxId: String,
    val readCapability: ByteArray,
    val writeCapability: ByteArray,
    val partnerEnrollmentCapability: ByteArray?,
)

internal data class RelayObjectMetadata(
    val objectId: String,
    val size: Long,
    val cipherSha256Hex: String,
    val expiresAtEpochMillis: Long,
)

internal data class PartnerReplacementCredentials(
    val partnerEnrollmentCapability: ByteArray,
    val ownWriteCapability: ByteArray,
)

internal class RelayException(val status: Int, val code: String) :
    Exception("Relay request failed with $status ($code)")

internal class RelayClient(baseUrl: String) {
    private val baseUrl = baseUrl.trimEnd('/')

    init {
        require(baseUrl.startsWith("https://") || baseUrl == "http://127.0.0.1:8787") {
            "Relay URL must use HTTPS"
        }
    }

    suspend fun createMailbox(enrollmentCapability: ByteArray): RelayMailboxCredentials =
        withContext(Dispatchers.IO) {
            val response = execute(
                method = "POST",
                path = "/v1/mailboxes",
                capability = enrollmentCapability,
            )
            val json = JSONObject(response.body.decodeToString())
            RelayMailboxCredentials(
                mailboxId = json.getString("mailbox_id"),
                readCapability = CapabilityCodec.decode(json.getString("read_capability")),
                writeCapability = CapabilityCodec.decode(json.getString("write_capability")),
                partnerEnrollmentCapability = json.optString("partner_enrollment_token")
                    .takeIf(String::isNotBlank)
                    ?.let(CapabilityCodec::decode),
            )
        }

    suspend fun preparePartnerReplacement(
        ownMailboxId: String,
        ownReadCapability: ByteArray,
    ): PartnerReplacementCredentials = withContext(Dispatchers.IO) {
        val response = execute(
            method = "POST",
            path = "/v1/mailboxes/${safeId(ownMailboxId)}/partner-replacement",
            capability = ownReadCapability,
        )
        val json = JSONObject(response.body.decodeToString())
        PartnerReplacementCredentials(
            partnerEnrollmentCapability = CapabilityCodec.decode(
                json.getString("partner_enrollment_token"),
            ),
            ownWriteCapability = CapabilityCodec.decode(json.getString("own_write_capability")),
        )
    }

    suspend fun createRendezvous(id: String, capability: ByteArray) =
        withContext(Dispatchers.IO) {
            execute(
                method = "PUT",
                path = "/v1/rendezvous/${safeId(id)}",
                capability = capability,
                headers = mapOf("X-Love-Doves-Rendezvous-Create" to "1"),
            )
            Unit
        }

    suspend fun putRendezvous(id: String, capability: ByteArray, encryptedResponse: ByteArray) =
        withContext(Dispatchers.IO) {
            execute(
                method = "PUT",
                path = "/v1/rendezvous/${safeId(id)}",
                capability = capability,
                body = encryptedResponse,
            )
            Unit
        }

    suspend fun getRendezvous(id: String, capability: ByteArray): ByteArray =
        withContext(Dispatchers.IO) {
            execute(
                method = "GET",
                path = "/v1/rendezvous/${safeId(id)}",
                capability = capability,
                maxResponseBytes = MAX_RENDEZVOUS_BYTES,
            ).body
        }

    suspend fun deleteRendezvous(id: String, capability: ByteArray) =
        withContext(Dispatchers.IO) {
            execute(
                method = "DELETE",
                path = "/v1/rendezvous/${safeId(id)}",
                capability = capability,
            )
            Unit
        }

    suspend fun putObject(
        mailboxId: String,
        objectId: String,
        writeCapability: ByteArray,
        bytes: ByteArray,
    ) = withContext(Dispatchers.IO) {
        require(bytes.size <= MAX_OBJECT_BYTES)
        execute(
            method = "PUT",
            path = "/v1/mailboxes/${safeId(mailboxId)}/objects/${safeId(objectId)}",
            capability = writeCapability,
            body = bytes,
        )
        Unit
    }

    suspend fun listObjects(mailboxId: String, readCapability: ByteArray): List<RelayObjectMetadata> =
        withContext(Dispatchers.IO) {
            val response = execute(
                method = "GET",
                path = "/v1/mailboxes/${safeId(mailboxId)}/objects",
                capability = readCapability,
                maxResponseBytes = MAX_LIST_BYTES,
            )
            val objects = JSONObject(response.body.decodeToString()).getJSONArray("objects")
            objects.mapObjects { json ->
                RelayObjectMetadata(
                    objectId = json.getString("object_id"),
                    size = json.getLong("size"),
                    cipherSha256Hex = json.getString("cipher_sha256"),
                    expiresAtEpochMillis = json.getLong("expires_at_epoch_ms"),
                )
            }
        }

    suspend fun getObject(
        mailboxId: String,
        objectId: String,
        readCapability: ByteArray,
    ): ByteArray = withContext(Dispatchers.IO) {
        execute(
            method = "GET",
            path = "/v1/mailboxes/${safeId(mailboxId)}/objects/${safeId(objectId)}",
            capability = readCapability,
            maxResponseBytes = MAX_OBJECT_BYTES + 64,
        ).body
    }

    suspend fun acknowledge(mailboxId: String, objectId: String, readCapability: ByteArray) =
        withContext(Dispatchers.IO) {
            execute(
                method = "POST",
                path = "/v1/mailboxes/${safeId(mailboxId)}/acks/${safeId(objectId)}",
                capability = readCapability,
            )
            Unit
        }

    suspend fun updatePushToken(mailboxId: String, readCapability: ByteArray, token: String) =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("token", token).toString().encodeToByteArray()
            execute(
                method = "PUT",
                path = "/v1/mailboxes/${safeId(mailboxId)}/push-token",
                capability = readCapability,
                body = body,
                contentType = "application/json",
            )
            Unit
        }

    suspend fun deleteMailbox(mailboxId: String, readCapability: ByteArray) =
        withContext(Dispatchers.IO) {
            execute(
                method = "DELETE",
                path = "/v1/mailboxes/${safeId(mailboxId)}",
                capability = readCapability,
            )
            Unit
        }

    private fun execute(
        method: String,
        path: String,
        capability: ByteArray,
        body: ByteArray? = null,
        contentType: String = "application/octet-stream",
        headers: Map<String, String> = emptyMap(),
        maxResponseBytes: Int = MAX_SMALL_RESPONSE_BYTES,
    ): RelayResponse {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = false
            connection.setRequestProperty("Authorization", "Bearer ${CapabilityCodec.encode(capability)}")
            connection.setRequestProperty("Accept", "application/json, application/octet-stream")
            headers.forEach(connection::setRequestProperty)
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", contentType)
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val status = connection.responseCode
            val input = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseBytes = input?.use { readBounded(it, maxResponseBytes) } ?: byteArrayOf()
            if (status !in 200..299) {
                val code = runCatching {
                    JSONObject(responseBytes.decodeToString()).getString("error")
                }.getOrDefault("http_error")
                throw RelayException(status, code)
            }
            return RelayResponse(status, responseBytes)
        } finally {
            connection.disconnect()
        }
    }

    private fun readBounded(input: java.io.InputStream, limit: Int): ByteArray {
        val output = ByteArrayOutputStream(minOf(limit, 64 * 1024))
        val buffer = ByteArray(16 * 1024)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            require(total <= limit) { "Relay response exceeds limit" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun safeId(id: String): String {
        require(id.matches(Regex("[A-Za-z0-9_-]{16,128}"))) { "Invalid relay id" }
        return id
    }

    private data class RelayResponse(val status: Int, val body: ByteArray)

    companion object {
        private const val CONNECT_TIMEOUT_MS = 15_000
        private const val READ_TIMEOUT_MS = 45_000
        private const val MAX_OBJECT_BYTES = 20 * 1024 * 1024
        private const val MAX_RENDEZVOUS_BYTES = 256 * 1024
        private const val MAX_LIST_BYTES = 256 * 1024
        private const val MAX_SMALL_RESPONSE_BYTES = 64 * 1024
    }
}

internal object CapabilityCodec {
    fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}

private inline fun <T> JSONArray.mapObjects(transform: (JSONObject) -> T): List<T> =
    List(length()) { index -> transform(getJSONObject(index)) }
