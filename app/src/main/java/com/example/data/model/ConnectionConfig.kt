package com.example.data.model

import org.json.JSONObject
import java.net.URI

data class ConnectionConfig(
    val ip: String,
    val port: Int = 8443,
    val code: String = "",
    val useSsl: Boolean = true
) {
    val websocketUrl: String
        get() {
            val scheme = if (useSsl) "wss" else "ws"
            return "$scheme://$ip:$port/ws"
        }

    val displayAddress: String
        get() = "$ip:$port"

    companion object {
        /**
         * Parses JSON QR payload like: {"ip":"192.168.0.10","port":8443,"code":"483921"}
         * or fallback formats like "wss://192.168.0.10:8443/ws" or "192.168.0.10:8443"
         */
        fun parse(raw: String): ConnectionConfig? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null

            // 1. Try standard JSON
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                try {
                    val json = JSONObject(trimmed)
                    val ip = json.optString("ip", "").ifEmpty {
                        json.optString("host", "")
                    }
                    if (ip.isNotEmpty()) {
                        val port = json.optInt("port", 8443)
                        val code = json.optString("code", "")
                        val ssl = json.optBoolean("ssl", port != 80 && port != 8080)
                        return ConnectionConfig(
                            ip = ip,
                            port = if (port > 0) port else 8443,
                            code = code,
                            useSsl = ssl
                        )
                    }
                } catch (_: Throwable) {
                    // fall through to regex fallback
                }

                // Fallback regex extraction for unit tests / JVM stubs
                try {
                    val ipMatch = Regex(""""(?:ip|host)"\s*:\s*"([^"]+)"""").find(trimmed)
                    if (ipMatch != null) {
                        val ip = ipMatch.groupValues[1]
                        val portMatch = Regex(""""port"\s*:\s*([0-9]+)""").find(trimmed)
                        val port = portMatch?.groupValues?.get(1)?.toIntOrNull() ?: 8443
                        val codeMatch = Regex(""""code"\s*:\s*"([^"]*)"""").find(trimmed)
                            ?: Regex(""""code"\s*:\s*([0-9]+)""").find(trimmed)
                        val code = codeMatch?.groupValues?.get(1) ?: ""
                        val sslMatch = Regex(""""ssl"\s*:\s*(true|false)""").find(trimmed)
                        val ssl = sslMatch?.groupValues?.get(1)?.toBoolean() ?: (port != 80 && port != 8080)
                        return ConnectionConfig(ip = ip, port = port, code = code, useSsl = ssl)
                    }
                } catch (_: Exception) {
                    // fall through
                }
            }

            // 2. Try URI format (wss:// or ws://)
            if (trimmed.startsWith("wss://") || trimmed.startsWith("ws://") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
                try {
                    val uri = URI(trimmed)
                    val host = uri.host ?: return null
                    val port = if (uri.port > 0) uri.port else if (trimmed.startsWith("wss://") || trimmed.startsWith("https://")) 8443 else 8080
                    val useSsl = trimmed.startsWith("wss://") || trimmed.startsWith("https://")
                    var code = ""
                    uri.query?.split("&")?.forEach { param ->
                        val parts = param.split("=")
                        if (parts.size == 2 && parts[0].equals("code", ignoreCase = true)) {
                            code = parts[1]
                        }
                    }
                    return ConnectionConfig(ip = host, port = port, code = code, useSsl = useSsl)
                } catch (_: Exception) {
                    // fall through
                }
            }

            // 3. Simple IP[:port][:code] e.g. "192.168.1.10:8443:123456"
            val parts = trimmed.split(":")
            if (parts.isNotEmpty()) {
                val ip = parts[0]
                if (isValidIp(ip)) {
                    val port = if (parts.size >= 2) parts[1].toIntOrNull() ?: 8443 else 8443
                    val code = if (parts.size >= 3) parts[2] else ""
                    return ConnectionConfig(ip = ip, port = port, code = code, useSsl = true)
                }
            }

            return null
        }

        private fun isValidIp(ip: String): Boolean {
            val parts = ip.split(".")
            if (parts.size != 4) return false
            return parts.all { part ->
                val num = part.toIntOrNull() ?: return false
                num in 0..255
            }
        }
    }
}
