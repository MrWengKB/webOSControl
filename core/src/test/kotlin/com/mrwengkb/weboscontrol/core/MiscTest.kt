package com.mrwengkb.weboscontrol.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MiscTest {
    @Test
    fun magicPacketLayout() {
        val p = WakeOnLan.magicPacket("A8:23:FE:01:02:0f")
        assertEquals(102, p.size)
        assertEquals(0xFF.toByte(), p[5])
        assertEquals(0xA8.toByte(), p[6])
        assertEquals(0x0F.toByte(), p[101])
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBadMac() {
        WakeOnLan.parseMac("not-a-mac")
    }

    @Test
    fun parsesOldVolumeShape() {
        val p = Json.parseToJsonElement("""{"volume":9,"muted":true}""").jsonObject
        assertEquals(VolumeStatus(9, true), parseVolume(p))
    }

    @Test
    fun parsesAppsAndInputs() {
        val apps = parseApps(Json.parseToJsonElement(
            """{"launchPoints":[{"id":"netflix","title":"Netflix","icon":"http://x/n.png","removable":true},{"id":"youtube.leanback.v4","title":"YouTube"}]}"""
        ).jsonObject)
        assertEquals(listOf("Netflix", "YouTube"), apps.map { it.title })
        assertEquals(listOf(true, false), apps.map { it.removable })
        val inputs = parseInputs(Json.parseToJsonElement(
            """{"devices":[{"id":"HDMI_1","label":"PlayStation","connected":true},{"id":"HDMI_2","label":"HDMI 2","connected":false}]}"""
        ).jsonObject)
        assertEquals(listOf("HDMI_1", "HDMI_2"), inputs.map { it.id })
        assertEquals(false, inputs[1].connected)
    }

    @Test
    fun picksConnectedMac() {
        val conn = Json.parseToJsonElement(
            """{"wiredInfo":{"state":"disconnected","macAddress":"aa:aa:aa:aa:aa:aa"},"wifiInfo":{"state":"connected","macAddress":"BB:BB:BB:BB:BB:BB"}}"""
        ).jsonObject
        assertEquals("bb:bb:bb:bb:bb:bb", parseMac(conn, null))
        assertNull(parseMac(null, null))
    }

    @Test
    fun parsesKeyboardFocus() {
        assertNull(parseKeyboard(Json.parseToJsonElement("""{"returnValue":true,"subscribed":true}""").jsonObject))
        val focused = parseKeyboard(Json.parseToJsonElement(
            """{"currentWidget":{"focus":true,"contentType":"text","hiddenText":false,"cursorPosition":0}}"""
        ).jsonObject)
        assertEquals(KeyboardState(focused = true, password = false, contentType = "text"), focused)
    }

    @Test
    fun computesTextEdits() {
        assertEquals(0 to "a", textEdit("", "a"))
        assertEquals(0 to "c", textEdit("ab", "abc"))
        assertEquals(1 to "", textEdit("abc", "ab"))
        assertEquals(3 to "xyz", textEdit("abc", "xyz"))
        assertEquals(1 to "Xd", textEdit("abc", "abXd"))
        assertEquals(0 to "", textEdit("same", "same"))
    }
}

class YouTubeSearchTest {
    @org.junit.Test fun usesTheAppsSearchTemplate() {
        val template = kotlinx.serialization.json.Json.parseToJsonElement("""{"contentTarget":"q=${'$'}SEARCH_KEYWORD"}""")
        org.junit.Assert.assertEquals("""{"contentTarget":"q=lo%20fi%26beats"}""", youtubeSearchParams("lo fi&beats", template).toString())
        val asString = kotlinx.serialization.json.JsonPrimitive("""{"contentTarget":"https://www.youtube.com/tv?q=${'$'}SEARCH_KEYWORD"}""")
        org.junit.Assert.assertEquals("""{"contentTarget":"https://www.youtube.com/tv?q=cats"}""", youtubeSearchParams("cats", asString).toString())
    }

    @org.junit.Test fun fallsBackWithoutATemplate() {
        org.junit.Assert.assertEquals("""{"contentTarget":"q=cats"}""", youtubeSearchParams(" cats ", null).toString())
    }
}
