package com.example.ava.utils

import android.annotation.SuppressLint
import timber.log.Timber
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.random.Random

@OptIn(ExperimentalStdlibApi::class)
fun getRandomMacAddressString(): String {
    val random = Random.Default
    val bytes = random.nextBytes(6)
    return bytes.toHexString(HexFormat {
        upperCase = true
        bytes { byteSeparator = ":" }
    })
}

/**
 * A TrustManager that trusts all certificates.
 */
@SuppressLint("CustomX509TrustManager")
private object TrustAllTrustManager : X509TrustManager {
    @SuppressLint("TrustAllX509TrustManager")
    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    @SuppressLint("TrustAllX509TrustManager")
    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
    }

    override fun getAcceptedIssuers(): Array<out X509Certificate?> = arrayOf()
}

/**
 * Sets whether SSL connections should trust all certificates, or use the default trust manager.
 * Can be used as an unsafe workaround when using self-signed certificates.
 */
fun setTrustAllSSLCertificates(trustAll: Boolean) {
    // If disabling, use a custom TrustManager that trusts all certificates,
    // else set to null to use the default trust manager to [re]enable verification.
    val trustManager = if (trustAll) arrayOf<TrustManager>(TrustAllTrustManager) else null

    runCatching {
        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustManager, SecureRandom())
        HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
    }.onFailure { t ->
        Timber.e(t, "Failed to ${if (trustAll) "disable" else "enable"} SSL verification")
    }
}