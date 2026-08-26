package com.videhub.extractor

import android.content.Context
import android.content.SharedPreferences
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Route
import okhttp3.Request
import okhttp3.Response
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

data class ProxyConfig(
    val host: String,
    val port: Int,
    val type: Proxy.Type = Proxy.Type.HTTP,
    val username: String = "",
    val password: String = ""
)

object ProxyManager {

    private const val PREFS_NAME = "boghara_proxy_prefs"
    private const val KEY_ENABLED = "proxy_enabled"
    private const val KEY_HOST = "proxy_host"
    private const val KEY_PORT = "proxy_port"
    private const val KEY_TYPE = "proxy_type"
    private const val KEY_USERNAME = "proxy_username"
    private const val KEY_PASSWORD = "proxy_password"

    // Free public rotating proxies as fallback (updated regularly)
    // In production, replace these with your own proxy service
    private val FREE_PROXIES = listOf(
        ProxyConfig("8.219.97.248", 80),
        ProxyConfig("103.149.162.195", 80),
        ProxyConfig("47.74.152.29", 8888),
        ProxyConfig("20.111.54.16", 80),
        ProxyConfig("103.167.135.159", 8080),
    )

    private val currentProxyIndex = java.util.concurrent.atomic.AtomicInteger(0)
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    // Save proxy settings
    fun saveProxy(config: ProxyConfig) {
        prefs?.edit()
            ?.putBoolean(KEY_ENABLED, true)
            ?.putString(KEY_HOST, config.host)
            ?.putInt(KEY_PORT, config.port)
            ?.putString(KEY_TYPE, config.type.name)
            ?.putString(KEY_USERNAME, config.username)
            ?.putString(KEY_PASSWORD, config.password)
            ?.apply()
    }

    fun disableProxy() {
        prefs?.edit()?.putBoolean(KEY_ENABLED, false)?.apply()
    }

    fun isProxyEnabled(): Boolean = prefs?.getBoolean(KEY_ENABLED, false) ?: false

    fun getSavedProxy(): ProxyConfig? {
        if (!isProxyEnabled()) return null
        val p = prefs ?: return null
        val host = p.getString(KEY_HOST, "") ?: return null
        val port = p.getInt(KEY_PORT, 0)
        if (host.isEmpty() || port == 0) return null
        return ProxyConfig(
            host = host,
            port = port,
            type = Proxy.Type.valueOf(p.getString(KEY_TYPE, "HTTP") ?: "HTTP"),
            username = p.getString(KEY_USERNAME, "") ?: "",
            password = p.getString(KEY_PASSWORD, "") ?: ""
        )
    }

    // Rotate to next free proxy
    fun getNextFreeProxy(): ProxyConfig {
        val index = currentProxyIndex.getAndIncrement()
        val proxy = FREE_PROXIES[index % FREE_PROXIES.size]
        return proxy
    }

    // Build OkHttpClient with proxy support
    fun buildClient(config: ProxyConfig? = getSavedProxy()): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)

        if (config != null) {
            val proxy = Proxy(config.type, InetSocketAddress(config.host, config.port))
            builder.proxy(proxy)

            // Add auth if proxy requires username/password
            if (config.username.isNotEmpty() && config.password.isNotEmpty()) {
                builder.proxyAuthenticator(object : Authenticator {
                    override fun authenticate(route: Route?, response: Response): Request? {
                        val credential = Credentials.basic(config.username, config.password)
                        return response.request.newBuilder()
                            .header("Proxy-Authorization", credential)
                            .build()
                    }
                })
            }
        }

        return builder.build()
    }

    // Auto-rotate: if current proxy fails, try next one
    fun buildAutoRotatingClient(): OkHttpClient {
        return buildClient(getNextFreeProxy())
    }
}