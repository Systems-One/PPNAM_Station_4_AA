package com.mitas.ppnam.station4aa.domain.usecase

import com.mitas.ppnam.station4aa.data.mqtt.EmptyPayload
import com.mitas.ppnam.station4aa.data.mqtt.FailureKind
import com.mitas.ppnam.station4aa.data.mqtt.MqttOutcome
import com.mitas.ppnam.station4aa.data.mqtt.MqttTopics
import com.mitas.ppnam.station4aa.data.mqtt.RequestChannel
import com.mitas.ppnam.station4aa.data.mqtt.dto.OperatorEntryDto
import com.mitas.ppnam.station4aa.data.mqtt.dto.OperatorListResponse
import com.mitas.ppnam.station4aa.domain.login.InMemoryOperatorDirectoryStore
import com.mitas.ppnam.station4aa.domain.model.OperatorEntry
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same shape as SyncWasteCatalogueUseCaseTest's fake: canned outcome, records what was asked. */
private class FakeOperatorListChannel(
    private val outcome: () -> MqttOutcome<OperatorListResponse>,
) : RequestChannel {
    var lastDeviceId: String? = null
    var lastRequestType: String? = null
    var lastResponseClass: Class<*>? = null
    var lastPayload: Any? = null
    var lastOperatorSessionId: String? = null
    var calls = 0

    @Suppress("UNCHECKED_CAST")
    override suspend fun <T : Any> request(
        deviceId: String,
        requestType: String,
        responseClass: Class<T>,
        payload: Any,
        operatorSessionId: String,
        timeoutMs: Long,
    ): MqttOutcome<T> {
        calls++
        lastDeviceId = deviceId
        lastRequestType = requestType
        lastResponseClass = responseClass
        lastPayload = payload
        lastOperatorSessionId = operatorSessionId
        return outcome() as MqttOutcome<T>
    }
}

class OperatorDirectoryUseCaseTest {

    private val deviceId = "scanner_a1b2c3d4e5f6"

    private val cachedBefore = listOf(
        OperatorEntry(username = "cached", displayName = "Cached Operator"),
    )

    private fun fixture(
        outcome: () -> MqttOutcome<OperatorListResponse>,
        seed: List<OperatorEntry> = cachedBefore,
    ): Triple<OperatorDirectoryUseCase, FakeOperatorListChannel, InMemoryOperatorDirectoryStore> {
        val channel = FakeOperatorListChannel(outcome)
        val store = InMemoryOperatorDirectoryStore().apply { save(seed) }
        return Triple(OperatorDirectoryUseCase(channel, store, deviceId), channel, store)
    }

    @Test
    fun `cached returns whatever the store holds`() {
        val (useCase, channel, _) = fixture({ MqttOutcome.NoResponse(FailureKind.Timeout) })

        assertEquals(cachedBefore, useCase.cached())
        assertEquals(0, channel.calls)
    }

    @Test
    fun `refresh sends operator_list_requested with the bare envelope and no session`() = runTest {
        val (useCase, channel, _) = fixture({ MqttOutcome.Accepted(OperatorListResponse()) })

        useCase.refresh()

        assertEquals(deviceId, channel.lastDeviceId)
        assertEquals("operator_list_requested", channel.lastRequestType)
        assertEquals(MqttTopics.OPERATOR_LIST_REQUESTED, channel.lastRequestType)
        assertSame(OperatorListResponse::class.java, channel.lastResponseClass)
        assertSame(EmptyPayload, channel.lastPayload)
        assertEquals("", channel.lastOperatorSessionId)
    }

    @Test
    fun `an accepted list is parsed, sorted by display name, persisted and returned`() = runTest {
        val response = OperatorListResponse(
            operators = listOf(
                OperatorEntryDto(username = "zed", displayName = "Zed Zulu"),
                OperatorEntryDto(username = "mdlamini", displayName = "m. Dlamini"),
                OperatorEntryDto(username = "jsmith", displayName = "J. Smith"),
            ),
        )
        val (useCase, _, store) = fixture({ MqttOutcome.Accepted(response) })

        val result = useCase.refresh()

        val expected = listOf(
            OperatorEntry("jsmith", "J. Smith"),
            OperatorEntry("mdlamini", "m. Dlamini"),
            OperatorEntry("zed", "Zed Zulu"),
        )
        assertEquals(expected, result)
        assertEquals(expected, store.load())
        assertEquals(expected, useCase.cached())
    }

    @Test
    fun `blank and whitespace usernames are dropped and the rest are trimmed`() = runTest {
        val response = OperatorListResponse(
            operators = listOf(
                OperatorEntryDto(username = "", displayName = "Nobody"),
                OperatorEntryDto(username = "   ", displayName = "Spaces"),
                OperatorEntryDto(username = "  jsmith  ", displayName = "  J. Smith  "),
            ),
        )
        val (useCase, _, _) = fixture({ MqttOutcome.Accepted(response) })

        assertEquals(listOf(OperatorEntry("jsmith", "J. Smith")), useCase.refresh())
    }

    @Test
    fun `a missing or blank displayName falls back to the username`() = runTest {
        val response = OperatorListResponse(
            operators = listOf(
                OperatorEntryDto(username = "jsmith"),
                OperatorEntryDto(username = "mdlamini", displayName = "   "),
            ),
        )
        val (useCase, _, _) = fixture({ MqttOutcome.Accepted(response) })

        assertEquals(
            listOf(OperatorEntry("jsmith", "jsmith"), OperatorEntry("mdlamini", "mdlamini")),
            useCase.refresh(),
        )
    }

    @Test
    fun `an accepted empty list is valid and replaces the cache`() = runTest {
        val (useCase, _, store) = fixture({ MqttOutcome.Accepted(OperatorListResponse()) })

        val result = useCase.refresh()

        assertEquals(emptyList<OperatorEntry>(), result)
        assertEquals(emptyList<OperatorEntry>(), store.load())
    }

    @Test
    fun `a rejected response returns null and leaves the cache untouched`() = runTest {
        val (useCase, _, store) = fixture({
            MqttOutcome.Rejected(null, "directory_unavailable", "Directory unavailable")
        })

        assertNull(useCase.refresh())
        assertEquals(cachedBefore, store.load())
        assertEquals(cachedBefore, useCase.cached())
    }

    @Test
    fun `no response (timeout, not connected, malformed) returns null and keeps the cache`() = runTest {
        for (kind in FailureKind.values()) {
            val (useCase, _, store) = fixture({ MqttOutcome.NoResponse(kind) })

            assertNull("kind=$kind", useCase.refresh())
            assertEquals("kind=$kind", cachedBefore, store.load())
        }
    }

    @Test
    fun `a request that throws is converted to null instead of propagating`() = runTest {
        val (useCase, _, store) = fixture({ throw IllegalStateException("publish failed") })

        assertNull(useCase.refresh())
        assertEquals(cachedBefore, store.load())
    }

    @Test
    fun `a store that throws on save still returns null rather than crashing the login screen`() = runTest {
        val channel = FakeOperatorListChannel({
            MqttOutcome.Accepted(OperatorListResponse(listOf(OperatorEntryDto("jsmith", "J. Smith"))))
        })
        val store = object : InMemoryOperatorDirectoryStore() {
            override fun save(entries: List<OperatorEntry>) = throw IllegalStateException("disk full")
        }
        val useCase = OperatorDirectoryUseCase(channel, store, deviceId)

        assertNull(useCase.refresh())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun `label reads username then display name`() {
        assertEquals("jsmith — J. Smith", OperatorEntry("jsmith", "J. Smith").label)
    }
}
