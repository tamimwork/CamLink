package com.example

import com.example.data.model.ConnectionConfig
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testJsonQrParsing() {
        val raw = """{"ip":"192.168.0.10","port":8443,"code":"483921"}"""
        val config = ConnectionConfig.parse(raw)
        assertNotNull(config)
        assertEquals("192.168.0.10", config?.ip)
        assertEquals(8443, config?.port)
        assertEquals("483921", config?.code)
        assertEquals("wss://192.168.0.10:8443/ws", config?.websocketUrl)
    }

    @Test
    fun testUrlQrParsing() {
        val raw = "wss://192.168.1.50:9000?code=1234"
        val config = ConnectionConfig.parse(raw)
        assertNotNull(config)
        assertEquals("192.168.1.50", config?.ip)
        assertEquals(9000, config?.port)
        assertEquals("1234", config?.code)
        assertTrue(config?.useSsl == true)
    }

    @Test
    fun testSimpleIpPortParsing() {
        val raw = "192.168.1.20:8080:999"
        val config = ConnectionConfig.parse(raw)
        assertNotNull(config)
        assertEquals("192.168.1.20", config?.ip)
        assertEquals(8080, config?.port)
        assertEquals("999", config?.code)
    }

    @Test
    fun testInvalidQrReturnsNull() {
        assertNull(ConnectionConfig.parse("not an ip or json"))
        assertNull(ConnectionConfig.parse(""))
    }
}
