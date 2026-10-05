package com.mitas.ppnam.station4aa.data.settings

import com.mitas.ppnam.station4aa.domain.model.OperatorEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OperatorDirectoryCodecTest {

    private val entries = listOf(
        OperatorEntry("jsmith", "J. Smith"),
        OperatorEntry("mdlamini", "M. Dlamini"),
    )

    @Test
    fun `round-trips a list through a JSON array`() {
        val text = OperatorDirectoryCodec.encode(entries)

        assertTrue(text, text.trimStart().startsWith("["))
        assertEquals(entries, OperatorDirectoryCodec.decode(text))
    }

    @Test
    fun `encodes exactly username and displayName per entry`() {
        val text = OperatorDirectoryCodec.encode(listOf(OperatorEntry("jsmith", "J. Smith")))

        assertEquals("""[{"username":"jsmith","displayName":"J. Smith"}]""", text)
    }

    @Test
    fun `null, blank and garbage decode to an empty list`() {
        assertEquals(emptyList<OperatorEntry>(), OperatorDirectoryCodec.decode(null))
        assertEquals(emptyList<OperatorEntry>(), OperatorDirectoryCodec.decode(""))
        assertEquals(emptyList<OperatorEntry>(), OperatorDirectoryCodec.decode("   "))
        assertEquals(emptyList<OperatorEntry>(), OperatorDirectoryCodec.decode("not json"))
        assertEquals(emptyList<OperatorEntry>(), OperatorDirectoryCodec.decode("{\"username\":\"x\"}"))
        assertEquals(emptyList<OperatorEntry>(), OperatorDirectoryCodec.decode("[1, \"two\", null]"))
    }

    @Test
    fun `decode drops blank usernames and falls back displayName to username`() {
        val text = """[
            {"username": "", "displayName": "Nobody"},
            {"username": "jsmith"},
            {"username": "mdlamini", "displayName": null},
            {"displayName": "Orphan"},
            {"username": " zed ", "displayName": " Zed "}
        ]"""

        assertEquals(
            listOf(
                OperatorEntry("jsmith", "jsmith"),
                OperatorEntry("mdlamini", "mdlamini"),
                OperatorEntry("zed", "Zed"),
            ),
            OperatorDirectoryCodec.decode(text),
        )
    }
}
