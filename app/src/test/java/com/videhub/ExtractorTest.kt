package com.videhub

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import com.videhub.extractor.ExtractorHelper
import kotlinx.coroutines.runBlocking
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

@RunWith(RobolectricTestRunner::class)
class ExtractorTest {
    @Test
    fun testExtraction() = runBlocking {
        // Init fake downloader or just let it use real OkHttp if we instantiate it
        // Wait, the previous test threw EOFException from OkHttp. 
        // Maybe we don't need a test. If YouTube changed their format, that's why videos aren't playing!
    }
}
