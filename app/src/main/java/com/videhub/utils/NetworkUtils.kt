package com.videhub.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

object NetworkUtils {
    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
        val hasInternetCapability = activeNetwork.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        if (!hasInternetCapability) return false
        return when {
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
            activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> true
            else -> false
        }
    }

    fun getNetworkStatusFlow(context: Context): Flow<Boolean> = callbackFlow {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Emit initial value
        trySend(isNetworkAvailable(context))

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(true)
            }

            override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
                val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                trySend(hasInternet && isNetworkAvailable(context))
            }

            override fun onLost(network: Network) {
                trySend(isNetworkAvailable(context))
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        connectivityManager.registerNetworkCallback(request, networkCallback)

        awaitClose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }.distinctUntilChanged()

    private val networkKeywords = listOf(
        "unknownhostexception",
        "unable to resolve host",
        "no address associated with hostname",
        "sockettimeoutexception",
        "connectexception",
        "failed to connect",
        "network is unreachable",
        "noroutetohostexception",
        "no route to host",
        "software caused connection abort",
        "connection reset",
        "connection refused",
        "connection closed",
        "unexpected end of stream",
        "sslhandshakeexception",
        "sslexception",
        "timed out",
        "timeout",
        "offline",
        "no internet",
        "check your internet",
        "check your network",
        "enetunreach",
        "econnrefused",
        "econnreset",
        "ehostunreach"
    )

    private val technicalMarkers = listOf(
        "java.",
        "javax.",
        "kotlin.",
        "org.schabi.",
        "okhttp3.",
        "exception:",
        "exception",
        "www.youtube.com",
        "https://",
        "http://",
        "at com.",
        "at org."
    )

    fun isNetworkErrorMessage(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val lower = message.lowercase()
        return networkKeywords.any { lower.contains(it) }
    }

    fun isTimeoutError(throwable: Throwable? = null, rawMessage: String? = null): Boolean {
        if (throwable != null) {
            val causes = generateSequence(throwable) { it.cause }.take(10)
            for (cause in causes) {
                if (cause is SocketTimeoutException) return true
                val msg = cause.message?.lowercase() ?: ""
                if (msg.contains("sockettimeoutexception") || msg.contains("timed out") || msg.contains("timeout")) {
                    return true
                }
            }
        }
        if (!rawMessage.isNullOrBlank()) {
            val lower = rawMessage.lowercase()
            if (lower.contains("sockettimeoutexception") || lower.contains("timed out") || lower.contains("timeout")) {
                return true
            }
        }
        return false
    }

    fun isNetworkError(throwable: Throwable?): Boolean {
        if (throwable == null) return false
        val causes = generateSequence(throwable) { it.cause }.take(10)
        for (cause in causes) {
            if (cause is UnknownHostException ||
                cause is SocketTimeoutException ||
                cause is ConnectException ||
                cause is NoRouteToHostException ||
                cause is SocketException ||
                cause is SSLException ||
                cause is InterruptedIOException
            ) {
                return true
            }
            if (isNetworkErrorMessage(cause.message) || isNetworkErrorMessage(cause.toString())) {
                return true
            }
        }
        return false
    }

    fun getFriendlyErrorTitle(errorMessage: String?): String {
        if (errorMessage.isNullOrBlank()) return "Something went wrong"
        if (isTimeoutError(rawMessage = errorMessage)) return "Connection timed out"
        if (isNetworkErrorMessage(errorMessage)) return "You're offline"
        return "Something went wrong"
    }

    fun getFriendlyErrorMessage(
        throwable: Throwable?,
        fallback: String = "Something went wrong. Please try again."
    ): String {
        if (throwable == null) return fallback
        if (isTimeoutError(throwable)) {
            return "Connection timed out. Please check your internet connection and try again."
        }
        if (isNetworkError(throwable)) {
            return "No internet connection. Please check your network and try again."
        }
        return sanitizeErrorMessage(throwable.message, fallback)
    }

    fun sanitizeErrorMessage(
        rawMessage: String?,
        fallback: String = "Something went wrong. Please try again."
    ): String {
        if (rawMessage.isNullOrBlank()) return fallback
        if (isTimeoutError(rawMessage = rawMessage)) {
            return "Connection timed out. Please check your internet connection and try again."
        }
        if (isNetworkErrorMessage(rawMessage)) {
            return "No internet connection. Please check your network and try again."
        }
        val lower = rawMessage.lowercase()
        if (technicalMarkers.any { lower.contains(it) }) {
            return fallback
        }
        return rawMessage
    }
}
