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

    @Test
    fun scheduledPremiereVideo_isDetectedAndExcludedFromPlayableFeeds() {
        // Case 1: NewPipeExtractor upcoming Premiere with duration == -1L, textualUploadDate == null, viewCount == -1L
        val upcomingPremiere = org.schabi.newpipe.extractor.stream.StreamInfoItem(
            0,
            "https://www.youtube.com/watch?v=scheduled123",
            "Giant Beast Bone Cave | Secret Power Crystal Mystery | Ep 1032-1033",
            org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
        ).apply {
            duration = -1L
            viewCount = -1L
            textualUploadDate = null
        }
        assertTrue(com.videhub.utils.ChannelLiveStatusManager.isScheduledOrUpcomingVideo(upcomingPremiere))
        assertTrue(com.videhub.utils.ChannelLiveStatusManager.isEndedOrPastLiveStream(upcomingPremiere))
        assertFalse(com.videhub.utils.ChannelLiveStatusManager.isStreamCurrentlyLive(upcomingPremiere))

        // Case 2: Upcoming live event with LIVE_STREAM type but viewCount == -1L
        val upcomingLiveEvent = org.schabi.newpipe.extractor.stream.StreamInfoItem(
            0,
            "https://www.youtube.com/watch?v=scheduledLive456",
            "Red Blood Monster Rain | Secret Moon | Ep 1030-1031",
            org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM
        ).apply {
            duration = -1L
            viewCount = -1L
            textualUploadDate = null
        }
        assertTrue(com.videhub.utils.ChannelLiveStatusManager.isScheduledOrUpcomingVideo(upcomingLiveEvent))
        assertFalse(com.videhub.utils.ChannelLiveStatusManager.isStreamCurrentlyLive(upcomingLiveEvent))

        // Case 3: Normal published video on the same channel is playable and NOT filtered out
        val publishedVideo = org.schabi.newpipe.extractor.stream.StreamInfoItem(
            0,
            "https://www.youtube.com/watch?v=published789",
            "Ep 1028-1029 | Naman Story Book",
            org.schabi.newpipe.extractor.stream.StreamType.VIDEO_STREAM
        ).apply {
            duration = 1420L
            viewCount = 15400L
            textualUploadDate = "1 day ago"
        }
        assertFalse(com.videhub.utils.ChannelLiveStatusManager.isScheduledOrUpcomingVideo(publishedVideo))
        assertFalse(com.videhub.utils.ChannelLiveStatusManager.isEndedOrPastLiveStream(publishedVideo))

        val filtered = com.videhub.utils.ChannelLiveStatusManager.filterPlayableVideos(
            listOf(upcomingPremiere, upcomingLiveEvent, publishedVideo)
        )
        assertEquals(1, filtered.size)
        assertEquals("https://www.youtube.com/watch?v=published789", filtered.first().url)
    }

    @Test
    fun scheduledVideoException_isNotClassifiedAsNetworkError() {
        val premiereEx = RuntimeException("org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException: Premieres in 5 days")
        assertTrue(NetworkUtils.isScheduledVideoError(premiereEx))
        assertFalse(NetworkUtils.isNetworkError(premiereEx))
        assertEquals(
            "This video is scheduled and hasn't premiered yet. Please check back when it goes live.",
            NetworkUtils.getFriendlyErrorMessage(premiereEx)
        )
        assertEquals(
            "Upcoming scheduled video",
            NetworkUtils.getFriendlyErrorTitle(NetworkUtils.getFriendlyErrorMessage(premiereEx))
        )
    }
}
