package com.videhub

import org.junit.Test
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response

class SubtitleTest {
    @Test
    fun testSubtitles() {
        NewPipe.init(object : Downloader() {
            override fun execute(request: Request): Response {
                val okRequest = okhttp3.Request.Builder()
                    .url(request.url())
                    .apply {
                        request.headers().forEach { (k, v) ->
                            v.forEach { headerValue -> addHeader(k, headerValue) }
                        }
                    }
                    .build()
                val client = okhttp3.OkHttpClient()
                val response = client.newCall(okRequest).execute()
                val body = response.body?.string() ?: ""
                return Response(response.code, response.message, response.headers.toMultimap(), body, request.url())
            }
        })
        
        val service = ServiceList.YouTube
        val streamInfo = service.getStreamExtractor("https://www.youtube.com/watch?v=s5eA0mFhG64") // Chaand Baaliyan
        streamInfo.fetchPage()
        
        val subs = streamInfo.subtitlesDefault
        println("Default Subs: ${subs.size}")
        subs.forEach { 
            println("URL: ${it.url!!}")
            var req = okhttp3.Request.Builder().url(it.url!!).build()
            var res = okhttp3.OkHttpClient().newCall(req).execute().body?.string() ?: ""
            println("Response starts with: ${res.take(150)}")
            
            req = okhttp3.Request.Builder().url(it.url!! + "&fmt=vtt").build()
            res = okhttp3.OkHttpClient().newCall(req).execute().body?.string() ?: ""
            println("VTT Response starts with: ${res.take(150)}")
            
            req = okhttp3.Request.Builder().url(it.url!! + "&fmt=json3").build()
            res = okhttp3.OkHttpClient().newCall(req).execute().body?.string() ?: ""
            println("JSON3 Response starts with: ${res.take(150)}")
            
            req = okhttp3.Request.Builder().url(it.url!! + "&fmt=srv3").build()
            res = okhttp3.OkHttpClient().newCall(req).execute().body?.string() ?: ""
            println("SRV3 Response starts with: ${res.take(150)}")
        }
    }
}
