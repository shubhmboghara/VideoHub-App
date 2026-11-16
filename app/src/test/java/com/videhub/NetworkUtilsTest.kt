package com.videhub

import com.videhub.utils.NetworkUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class NetworkUtilsTest {

    @Test
    fun unknownHostException_isDetectedAndSanitized() {
        val rawEx = UnknownHostException("Unable to resolve host \"www.youtube.com\": No address associated with hostname")
        val wrappedEx = RuntimeException("java.net.UnknownHostException: Unable to resolve host \"www.youtube.com\": No address associated with hostname", rawEx)

        assertTrue(NetworkUtils.isNetworkError(rawEx))
        assertTrue(NetworkUtils.isNetworkError(wrappedEx))
        assertEquals(
            "No internet connection. Please check your network and try again.",
            NetworkUtils.getFriendlyErrorMessage(rawEx)
        )
        assertEquals(
            "No internet connection. Please check your network and try again.",
            NetworkUtils.getFriendlyErrorMessage(wrappedEx)
        )
    }

    @Test
    fun rawUnknownHostStringWithoutCause_isSanitized() {
        val msg = "java.net.UnknownHostException: Unable to resolve host \"www.youtube.com\": No address associated with hostname"
        assertTrue(NetworkUtils.isNetworkErrorMessage(msg))
        assertEquals(
            "No internet connection. Please check your network and try again.",
            NetworkUtils.sanitizeErrorMessage(msg)
        )
    }

    @Test
    fun socketTimeoutException_returnsFriendlyTimeoutMessage() {
        val timeoutEx = SocketTimeoutException("connect timed out")
        assertTrue(NetworkUtils.isNetworkError(timeoutEx))
        assertTrue(NetworkUtils.isTimeoutError(timeoutEx))
        assertEquals(
            "Connection timed out. Please check your internet connection and try again.",
            NetworkUtils.getFriendlyErrorMessage(timeoutEx)
        )
    }

    @Test
    fun connectException_returnsFriendlyNoInternetMessage() {
        val connectEx = ConnectException("Failed to connect to www.youtube.com/142.250.190.46:443")
        assertTrue(NetworkUtils.isNetworkError(connectEx))
        assertEquals(
            "No internet connection. Please check your network and try again.",
            NetworkUtils.getFriendlyErrorMessage(connectEx)
        )
    }

    @Test
    fun technicalNonNetworkException_returnsCleanFallbackWithoutLeakingClassNames() {
        val parserEx = RuntimeException("org.schabi.newpipe.extractor.ParsingException: Could not parse JSON at https://www.youtube.com")
        assertFalse(NetworkUtils.isNetworkError(parserEx))
        assertEquals(
            "Could not load channel videos. Please try again.",
            NetworkUtils.getFriendlyErrorMessage(parserEx, "Could not load channel videos. Please try again.")
        )
    }
}
