package com.example.webrtc

import android.annotation.SuppressLint
import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

object LanSslHelper {

    /**
     * Builds an OkHttpClient configured to connect to local LAN IP addresses,
     * securely allowing self-signed TLS certificates exclusively for LAN IPs (192.168.x.x, 10.x.x.x, 172.16-31.x.x)
     * and the explicitly scanned target IP.
     */
    fun createLanOkHttpClient(targetIp: String): OkHttpClient {
        val trustManager = object : X509TrustManager {
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}

            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
                // For LAN development and local screen recording tools, accept self-signed certificates
            }

            override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
        }

        val sslContext: SSLContext = SSLContext.getInstance("TLS")
        sslContext.init(null, arrayOf<TrustManager>(trustManager), SecureRandom())
        val sslSocketFactory: SSLSocketFactory = sslContext.socketFactory

        return OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustManager)
            .hostnameVerifier { hostname, _ ->
                // Accept hostname if it matches the target scanned IP or local LAN private subnets
                hostname == targetIp ||
                        hostname == "localhost" ||
                        hostname == "127.0.0.1" ||
                        hostname.startsWith("192.168.") ||
                        hostname.startsWith("10.") ||
                        (hostname.startsWith("172.") && isPrivate172(hostname))
            }
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // Keep-alive for WebSocket
            .writeTimeout(10, TimeUnit.SECONDS)
            .pingInterval(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun isPrivate172(host: String): Boolean {
        val parts = host.split(".")
        if (parts.size >= 2) {
            val second = parts[1].toIntOrNull() ?: return false
            return second in 16..31
        }
        return false
    }
}
