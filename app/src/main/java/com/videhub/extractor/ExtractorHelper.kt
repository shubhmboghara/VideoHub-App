package com.videhub.extractor

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request as DownloaderRequest
import org.schabi.newpipe.extractor.downloader.Response as DownloaderResponse
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.stream.StreamInfo
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import org.json.JSONArray
import java.net.URLEncoder

data class ChannelAboutInfo(
    val description: String? = null,
    val country: String? = null,
    val joinDate: String? = null,
    val totalViews: Long = -1L,
    val subscriberCount: Long = -1L,
    val socialLinks: List<String> = emptyList()
)

data class SponsorSegment(val start: Float, val end: Float, val category: String)

object ExtractorHelper {
    private val client = OkHttpClient()

    suspend fun getSponsorSegments(videoId: String): List<SponsorSegment> = withContext(Dispatchers.IO) {
        try {
            val url = "https://sponsor.ajay.app/api/skipSegments?videoID=${URLEncoder.encode(videoId, "UTF-8")}"
            val request = Request.Builder().url(url).build()
            val response = client.newCall(request).execute()
            if (response.isSuccessful) {
                val jsonStr = response.body?.string() ?: return@withContext emptyList()
                val jsonArray = JSONArray(jsonStr)
                val segments = mutableListOf<SponsorSegment>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val segment = obj.getJSONArray("segment")
                    val start = segment.getDouble(0).toFloat()
                    val end = segment.getDouble(1).toFloat()
                    val category = obj.getString("category")
                    if (category == "sponsor") {
                        segments.add(SponsorSegment(start, end, category))
                    }
                }
                return@withContext segments
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext emptyList()
    }

    @Volatile
    private var initialized = false
    private lateinit var appContext: Context

    private const val ANDROID_USER_AGENT =
        "com.google.android.youtube/19.09.37 (Linux; U; Android 11) gzip"

    private const val WEB_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private const val COOKIES =
        "SOCS=CAESNQgDEitib3FfaWRlbnRpdHlmcm9udGVuZHVpc2VydmVyXzIwMjQwNDAyLjA3X3AwGgJlbiABGgYIgMvFsAY; GPS=1"

    // Build fresh OkHttpClient — uses proxy if enabled, direct if not
    fun buildClient(): OkHttpClient {
        return if (ProxyManager.isProxyEnabled()) {
            ProxyManager.buildClient()
        } else {
            ProxyManager.buildClient(null) // direct connection
        }
    }

    private var currentLanguage = "en"
    private var currentCountry = "US"

    @Synchronized
    fun init(context: Context) {
        appContext = context.applicationContext
        ProxyManager.init(appContext)
        
        // Initial load of settings (blocking for simplicity on first init)
        try {
            val prefs = android.preference.PreferenceManager.getDefaultSharedPreferences(appContext)
            // Note: we might want to use DataStore but for init we can fallback or use runBlocking
            // For now let's just use defaults and update when changed
        } catch (e: Exception) {}
        
        initNewPipe()
    }

    @Synchronized
    fun updateLocalization(language: String, country: String) {
        if (currentLanguage == language && currentCountry == country) return
        currentLanguage = language
        currentCountry = country
        initialized = false
        initNewPipe()
    }

    // Call this again after changing proxy settings
    @Synchronized
    fun reinit() {
        initialized = false
        initNewPipe()
    }

    @Synchronized
    private fun initNewPipe() {
        if (initialized) return

        val client = buildClient()

        NewPipe.init(
            object : Downloader() {
                override fun execute(request: DownloaderRequest): DownloaderResponse {
                    val url = request.url()

                    val isPlayerRequest = url.contains("youtubei/v1/player") ||
                            url.contains("get_video_info") ||
                            url.contains("videoplayback")

                    val userAgent = if (isPlayerRequest) ANDROID_USER_AGENT else WEB_USER_AGENT

                    val okRequest = Request.Builder()
                        .url(url)
                        .apply {
                            // Let NewPipe set its own headers, but we can provide defaults if missing
                            var hasUserAgent = false
                            var hasAcceptLanguage = false
                            var contentType = "application/json; charset=utf-8"
                            
                            request.headers()?.forEach { (key, values) ->
                                if (key.equals("User-Agent", ignoreCase = true)) hasUserAgent = true
                                if (key.equals("Accept-Language", ignoreCase = true)) hasAcceptLanguage = true
                                if (key.equals("Content-Type", ignoreCase = true)) contentType = values.firstOrNull() ?: contentType
                                
                                values.forEach { value -> addHeader(key, value) }
                            }
                            
                            if (!hasUserAgent) {
                                addHeader("User-Agent", userAgent)
                            }
                            if (!hasAcceptLanguage) {
                                addHeader("Accept-Language", "$currentLanguage-$currentCountry,$currentLanguage;q=0.9")
                            }
                            
                            val method = request.httpMethod()
                            val data = request.dataToSend()
                            if (data != null) {
                                val mediaType = contentType.toMediaType()
                                method(method, data.toRequestBody(mediaType))
                            } else if (method.equals("POST", ignoreCase = true)) {
                                method(method, ByteArray(0).toRequestBody(null))
                            } else {
                                method(method, null)
                            }
                        }
                        .build()

                    try {
                        val response = client.newCall(okRequest).execute()
                        val body = response.body?.string() ?: ""

                        // If YouTube still blocks, auto-rotate proxy and retry once
                        if (response.code == 429 || body.contains("LOGIN_REQUIRED") || body.contains("Sign in to confirm")) {
                            return retryWithNewProxy(request)
                        }

                        return DownloaderResponse(
                            response.code,
                            response.message,
                            response.headers.toMultimap(),
                            body,
                            response.request.url.toString()
                        )
                    } catch (e: Exception) {
                        throw IOException(e)
                    }
                }

                // Auto-rotate to next free proxy on block
                private fun retryWithNewProxy(request: DownloaderRequest): DownloaderResponse {
                    val rotatingClient = ProxyManager.buildAutoRotatingClient()
                    val url = request.url()
                    val retryRequest = Request.Builder()
                        .url(url)
                        .apply {
                            var contentType = "application/json; charset=utf-8"
                            request.headers()?.forEach { (key, values) ->
                                if (key.equals("Content-Type", ignoreCase = true)) contentType = values.firstOrNull() ?: contentType
                                values.forEach { value -> addHeader(key, value) }
                            }
                            
                            val method = request.httpMethod()
                            val data = request.dataToSend()
                            if (data != null) {
                                val mediaType = contentType.toMediaType()
                                method(method, data.toRequestBody(mediaType))
                            } else if (method.equals("POST", ignoreCase = true)) {
                                method(method, ByteArray(0).toRequestBody(null))
                            } else {
                                method(method, null)
                            }
                        }
                        .build()

                    val response = rotatingClient.newCall(retryRequest).execute()
                    val body = response.body?.string() ?: ""
                    return DownloaderResponse(
                        response.code,
                        response.message,
                        response.headers.toMultimap(),
                        body,
                        response.request.url.toString()
                    )
                }
            },
            org.schabi.newpipe.extractor.localization.Localization(currentLanguage, currentCountry)
        )
        initialized = true
    }

    suspend fun searchYouTube(query: String, pages: Int = 3): SearchInfo =
        withContext(Dispatchers.IO) {
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
            val firstPage = SearchInfo.getInfo(ServiceList.YouTube, handler)
            var nextPage = firstPage.nextPage
            var pagesLoaded = 1
            // Keep loading pages until no more or limit reached
            while (nextPage != null && pagesLoaded < pages) {
                try {
                    val more = SearchInfo.getMoreItems(
                        ServiceList.YouTube, handler, nextPage
                    )
                    firstPage.relatedItems.addAll(more.items ?: emptyList())
                    nextPage = more.nextPage
                    pagesLoaded++
                } catch (_: Exception) { break }
            }
            firstPage
        }

    suspend fun searchWithChannels(query: String): Pair<SearchInfo, List<org.schabi.newpipe.extractor.channel.ChannelInfoItem>> =
        withContext(Dispatchers.IO) {
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
            val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
            // Try to get more items
            if (info.nextPage != null) {
                try {
                    val more = SearchInfo.getMoreItems(ServiceList.YouTube, handler, info.nextPage)
                    info.relatedItems.addAll(more.items ?: emptyList())
                } catch (_: Exception) {}
            }
            val channels = info.relatedItems
                .filterIsInstance<org.schabi.newpipe.extractor.channel.ChannelInfoItem>()
            Pair(info, channels)
        }

    fun getSearchPagingSource(query: String): ListExtractorPagingSource {
        val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
        val extractor = ServiceList.YouTube.getSearchExtractor(handler)
        return ListExtractorPagingSource(extractor)
    }

    suspend fun getSearchSuggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            // 1. Try NewPipe's official suggestion extractor
            try {
                val suggestionExtractor = ServiceList.YouTube.suggestionExtractor
                if (suggestionExtractor != null) {
                    val suggestions = suggestionExtractor.suggestionList(query)
                    if (!suggestions.isNullOrEmpty()) {
                        return@withContext suggestions.take(10)
                    }
                }
            } catch (_: Exception) {}

            // 2. High-speed YouTube Suggestion API fallback
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url = "https://suggestqueries.google.com/complete/search?client=youtube&ds=yt&client=firefox&q=$encoded"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", WEB_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            val response = buildClient().newCall(request).execute()
            if (response.isSuccessful) {
                val body = response.body?.string() ?: ""
                if (body.startsWith("[")) {
                    val array = JSONArray(body)
                    if (array.length() > 1) {
                        val suggestionsArray = array.getJSONArray(1)
                        val results = mutableListOf<String>()
                        for (i in 0 until suggestionsArray.length()) {
                            results.add(suggestionsArray.getString(i))
                        }
                        return@withContext results.take(10)
                    }
                }
            }
        } catch (_: Exception) {}
        emptyList()
    }

    fun getChannelPagingSource(info: ChannelInfo): ListExtractorPagingSource? {
        val tab = info.tabs.firstOrNull() as? org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
        return if (tab != null) {
            val extractor = ServiceList.YouTube.getChannelTabExtractor(tab)
            ListExtractorPagingSource(extractor)
        } else {
            null
        }
    }

    suspend fun getMoreSearchItems(query: String): List<org.schabi.newpipe.extractor.InfoItem> = withContext(Dispatchers.IO) {
        try {
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
            val extractor = ServiceList.YouTube.getSearchExtractor(handler)
            extractor.fetchPage()
            val items = mutableListOf<org.schabi.newpipe.extractor.InfoItem>()
            items.addAll(extractor.initialPage.items ?: emptyList())
            
            try {
                var currentPage = extractor.initialPage
                var pagesFetched = 0
                while (currentPage.hasNextPage() && pagesFetched < 5) {
                    val nextPage = extractor.getPage(currentPage.nextPage)
                    items.addAll(nextPage.items ?: emptyList())
                    currentPage = nextPage
                    pagesFetched++
                }
            } catch (e: Exception) {
                // Ignore pagination errors
            }
            
            items
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getShortsFeed(
        category: String = "All",
        maxPages: Int = 3
    ): List<org.schabi.newpipe.extractor.stream.StreamInfoItem> = withContext(Dispatchers.IO) {
        try {
            val query = when (category.lowercase()) {
                "all" -> listOf("#shorts", "trending shorts", "viral shorts", "youtube shorts", "#shortsvideo").random()
                "trending" -> listOf("trending shorts", "viral shorts 2024", "most viewed shorts").random()
                "gaming" -> listOf("gaming shorts", "gameplay shorts #shorts", "funny gaming shorts").random()
                "music" -> listOf("music shorts", "song shorts #shorts", "trending songs shorts").random()
                "comedy" -> listOf("comedy shorts", "funny shorts #shorts", "humor viral shorts").random()
                "tech" -> listOf("tech shorts", "gadgets shorts #shorts", "technology review shorts").random()
                "viral" -> listOf("viral shorts", "best viral shorts #shorts", "trending now shorts").random()
                "anime" -> listOf("anime shorts", "anime edit shorts #shorts", "manga shorts").random()
                else -> "$category #shorts"
            }
            val handler = ServiceList.YouTube.searchQHFactory.fromQuery(query)
            val extractor = ServiceList.YouTube.getSearchExtractor(handler)
            extractor.fetchPage()
            val rawItems = mutableListOf<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
            val initial = extractor.initialPage.items?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>() ?: emptyList()
            rawItems.addAll(initial)

            var currentPage = extractor.initialPage
            var pagesFetched = 1
            while (currentPage.hasNextPage() && pagesFetched < maxPages) {
                try {
                    val nextPage = extractor.getPage(currentPage.nextPage)
                    val nextItems = nextPage.items?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>() ?: emptyList()
                    rawItems.addAll(nextItems)
                    currentPage = nextPage
                    pagesFetched++
                } catch (e: Exception) {
                    break
                }
            }

            // Filter for shorts (duration <= 90s or #short in title or shorts in URL, or fallback)
            val shortsFiltered = rawItems.filter { item ->
                val dur = item.duration
                val name = item.name ?: ""
                val url = item.url ?: ""
                val isShortDuration = dur in 1..90
                val isShortTagged = name.contains("#short", ignoreCase = true) || 
                                    name.contains("shorts", ignoreCase = true) || 
                                    url.contains("shorts", ignoreCase = true)
                isShortDuration || isShortTagged || dur <= 0
            }.distinctBy { it.url ?: it.name ?: "" }

            if (shortsFiltered.isNotEmpty()) shortsFiltered else rawItems.distinctBy { it.url ?: "" }
        } catch (e: Exception) {
            android.util.Log.e("ExtractorHelper", "Error getting shorts feed", e)
            emptyList()
        }
    }

    suspend fun getChannelShorts(
        channelUrl: String,
        maxPages: Int = 3
    ): List<org.schabi.newpipe.extractor.stream.StreamInfoItem> = withContext(Dispatchers.IO) {
        try {
            val allVideos = getChannelVideosSorted(channelUrl, "latest", maxPages)
            val shorts = allVideos.filter { item ->
                val dur = item.duration
                val name = item.name ?: ""
                val url = item.url ?: ""
                val isShortDuration = dur in 1..90
                val isShortTagged = name.contains("#short", ignoreCase = true) || 
                                    name.contains("shorts", ignoreCase = true) || 
                                    url.contains("shorts", ignoreCase = true)
                isShortDuration || isShortTagged
            }.distinctBy { it.url ?: it.name ?: "" }
            
            if (shorts.isNotEmpty()) {
                shorts
            } else {
                // If no direct shorts found in channel latest, search for channel name + #shorts
                val channelInfo = getChannelInfo(channelUrl)
                val channelName = channelInfo.name ?: ""
                if (channelName.isNotBlank()) {
                    getShortsFeed(category = "$channelName #shorts", maxPages = 2)
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExtractorHelper", "Error getting channel shorts", e)
            emptyList()
        }
    }

    private val streamCache = java.util.concurrent.ConcurrentHashMap<String, Pair<Long, StreamInfo>>()

    suspend fun prefetchStreamInfo(url: String) = withContext(Dispatchers.IO) {
        try {
            getStreamInfo(url)
        } catch (e: Exception) {
            // ignore
        }
    }

    suspend fun getStreamInfo(url: String, useCache: Boolean = true): StreamInfo = withContext(Dispatchers.IO) {
        if (useCache) {
            val cached = streamCache[url]
            if (cached != null && System.currentTimeMillis() - cached.first < 30 * 60 * 1000) {
                return@withContext cached.second
            }
        }

        val info = StreamInfo.getInfo(ServiceList.YouTube, url)
        if (useCache) {
            streamCache[url] = System.currentTimeMillis() to info
        }
        
        android.util.Log.d("StreamInfo", "videoStreams: ${info.videoStreams?.size ?: 0}")
        android.util.Log.d("StreamInfo", "videoOnlyStreams: ${info.videoOnlyStreams?.size ?: 0}")
        android.util.Log.d("StreamInfo", "audioStreams: ${info.audioStreams?.size ?: 0}")
        android.util.Log.d("StreamInfo", "hlsUrl: ${info.hlsUrl ?: ""}")
        android.util.Log.d("StreamInfo", "dashUrl: ${info.dashMpdUrl ?: ""}")

        if (!info.videoStreams.isNullOrEmpty()) {
            android.util.Log.d("StreamInfo", "First video stream: ${info.videoStreams[0].url?.take(100) ?: ""}")
            android.util.Log.d("StreamInfo", "First video mime: ${info.videoStreams[0].format?.mimeType ?: ""}")
        }
        
        info
    }

    fun getPlaylistInfo(url: String): org.schabi.newpipe.extractor.playlist.PlaylistInfo {
        initNewPipe()
        return org.schabi.newpipe.extractor.playlist.PlaylistInfo.getInfo(url)
    }

    suspend fun getChannelAboutInfo(channelUrl: String): ChannelAboutInfo = withContext(Dispatchers.IO) {
        var result = ChannelAboutInfo()
        try {
            val client = buildClient()
            val url = if (channelUrl.endsWith("/about")) channelUrl else "${channelUrl.removeSuffix("/")}/about"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", WEB_USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .header("Cookie", COOKIES)
                .build()
            
            val response = client.newCall(request).execute()
            val html = response.body?.string() ?: ""
            
            val jsonStartStr = "var ytInitialData = "
            val jsonStartIndex = html.indexOf(jsonStartStr)
            if (jsonStartIndex != -1) {
                val start = jsonStartIndex + jsonStartStr.length
                val jsonEndIndex = html.indexOf(";</script>", start)
                if (jsonEndIndex != -1) {
                    val json = html.substring(start, jsonEndIndex)
                    
                    var joinDate: String? = null
                    var country: String? = null
                    var totalViews: Long = -1L
                    val socialLinks = mutableListOf<String>()
                    
                    val joinDateRegex = """"joinedDateText":\{"content":"([^"]+)"""".toRegex()
                    joinDateRegex.find(json)?.let { joinDate = it.groupValues[1] }
                    
                    val countryRegex = """"country":"([^"]+)"""".toRegex()
                    countryRegex.find(json)?.let { country = it.groupValues[1] }
                    
                    val viewsRegex = """"viewCountText":\{"simpleText":"([\d,]+)[^"]*"""".toRegex()
                    viewsRegex.find(json)?.let { 
                        totalViews = it.groupValues[1].replace(",", "").toLongOrNull() ?: -1L 
                    }
                    
                    val linksRegex = """"urlEndpoint":\{"url":"(http[^"]+)"""".toRegex()
                    linksRegex.findAll(json).forEach {
                        var link = it.groupValues[1].replace("\\u0026", "&")
                        if (link.contains("youtube.com/redirect")) {
                            val qMatch = Regex("""[?&]q=([^&]+)""").find(link)
                            if (qMatch != null) {
                                link = try { java.net.URLDecoder.decode(qMatch.groupValues[1], "UTF-8") } catch(e: Exception) { qMatch.groupValues[1] }
                            }
                        }
                        socialLinks.add(link)
                    }
                    
                    result = ChannelAboutInfo(
                        country = country,
                        joinDate = joinDate,
                        totalViews = totalViews,
                        socialLinks = socialLinks.distinct()
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        
        android.util.Log.d("ChannelAboutInfo", "Result: $result"); return@withContext result
    }

    suspend fun getChannelInfo(url: String): ChannelInfo = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(ServiceList.YouTube, url)
        try {
            info.tabs?.forEachIndexed { index, tab ->
                android.util.Log.d("TabsDebug", "Tab $index class: ${tab.javaClass.name}")
                if (tab is org.schabi.newpipe.extractor.linkhandler.ListLinkHandler) {
                    android.util.Log.d("TabsDebug", "Tab $index URL: ${tab.url}")
                }
            }
        } catch(e: Exception) {}
        info
    }


    suspend fun getChannelVideosSorted(
        channelUrl: String,
        sortFilter: String = "latest",
        maxPages: Int = 3
    ): List<org.schabi.newpipe.extractor.stream.StreamInfoItem> = withContext(Dispatchers.IO) {
        val svc = ServiceList.YouTube
        val items = mutableListOf<org.schabi.newpipe.extractor.stream.StreamInfoItem>()
        try {
            val info = ChannelInfo.getInfo(svc, channelUrl)
            
            // Determine how many pages to fetch based on filter
            val fetchPages = if (sortFilter.equals("latest", ignoreCase = true)) 1 else maxPages
            
            // Try to find if extractor supports passing sort (often not exposed directly for channel tabs in this version)
            // Fall back to fetching multiple pages
            val tab = info.tabs?.firstOrNull() as? org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
            if (tab != null) {
                val extractor = svc.getChannelTabExtractor(tab)
                extractor.fetchPage()
                
                items.addAll(extractor.initialPage.items?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>() ?: emptyList())
                
                try {
                    var currentPage = extractor.initialPage
                    var pagesFetched = 1
                    while (currentPage.hasNextPage() && pagesFetched < fetchPages) {
                        val nextPage = extractor.getPage(currentPage.nextPage)
                        items.addAll(nextPage.items?.filterIsInstance<org.schabi.newpipe.extractor.stream.StreamInfoItem>() ?: emptyList())
                        currentPage = nextPage
                        pagesFetched++
                    }
                } catch (e: Exception) {
                    // Graceful handling of extractor errors mid-fetch, return what we have
                    e.printStackTrace()
                }
            }
            
            when (sortFilter.lowercase()) {
                "popular" -> items.sortByDescending { 
                    if (it.viewCount < 0) Long.MIN_VALUE else it.viewCount 
                }
                "oldest" -> items.sortBy { video ->
                    video.uploadDate?.offsetDateTime()?.toEpochSecond() ?: Long.MAX_VALUE
                }
                // "latest" or others -> keep API order
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext items
    }

    suspend fun getChannelVideos(info: ChannelInfo, maxPages: Int = 10): List<org.schabi.newpipe.extractor.InfoItem> = withContext(Dispatchers.IO) {
        val svc = ServiceList.YouTube
        val items = mutableListOf<org.schabi.newpipe.extractor.InfoItem>()
        // Just take the first tab (usually "Videos") and extract items
        try {
            val tab = info.tabs?.firstOrNull() as? org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
            if (tab != null) {
                val extractor = svc.getChannelTabExtractor(tab)
                extractor.fetchPage()
                items.addAll(extractor.initialPage.items ?: emptyList())
                
                try {
                    var currentPage = extractor.initialPage
                    // Fetch up to maxPages more pages
                    var pagesFetched = 0
                    while (currentPage.hasNextPage() && pagesFetched < maxPages) {
                        val nextPage = extractor.getPage(currentPage.nextPage)
                        items.addAll(nextPage.items ?: emptyList())
                        currentPage = nextPage
                        pagesFetched++
                    }
                } catch (e: Exception) {}
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
        }
        items
    }

    suspend fun getChannelPlaylists(
        channelUrl: String,
        maxPages: Int = 3
    ): List<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem> = withContext(Dispatchers.IO) {
        val svc = ServiceList.YouTube
        val items = mutableListOf<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
        try {
            val info = ChannelInfo.getInfo(svc, channelUrl)
            val playlistTab = info.tabs?.firstOrNull { tab ->
                val linkHandler = tab as? org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
                linkHandler?.url?.contains("playlists", ignoreCase = true) == true ||
                linkHandler?.id?.contains("playlists", ignoreCase = true) == true
            } as? org.schabi.newpipe.extractor.linkhandler.ListLinkHandler

            val tabHandler = playlistTab ?: try {
                val cleanUrl = if (channelUrl.endsWith("/")) "${channelUrl}playlists" else "$channelUrl/playlists"
                svc.channelTabLHFactory.fromUrl(cleanUrl)
            } catch (_: Exception) { null }

            if (tabHandler != null) {
                val extractor = svc.getChannelTabExtractor(tabHandler)
                extractor.fetchPage()
                val initialItems = extractor.initialPage.items?.filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                    ?: emptyList()
                items.addAll(initialItems)

                var currentPage = extractor.initialPage
                var pagesFetched = 1
                while (currentPage.hasNextPage() && pagesFetched < maxPages) {
                    val nextPage = extractor.getPage(currentPage.nextPage)
                    val nextItems = nextPage.items?.filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
                        ?: emptyList()
                    items.addAll(nextItems)
                    currentPage = nextPage
                    pagesFetched++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext items.distinctBy { it.url ?: it.name ?: "" }
    }

    suspend fun searchPlaylists(query: String): List<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem> = withContext(Dispatchers.IO) {
        try {
            val handler = try {
                ServiceList.YouTube.searchQHFactory.fromQuery(query, listOf("playlists"), "")
            } catch (_: Exception) {
                ServiceList.YouTube.searchQHFactory.fromQuery(query)
            }
            val info = SearchInfo.getInfo(ServiceList.YouTube, handler)
            val items = mutableListOf<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>()
            items.addAll(info.relatedItems.filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>())
            if (info.nextPage != null && items.size < 10) {
                try {
                    val more = SearchInfo.getMoreItems(ServiceList.YouTube, handler, info.nextPage)
                    items.addAll(more.items.filterIsInstance<org.schabi.newpipe.extractor.playlist.PlaylistInfoItem>())
                } catch (_: Exception) {}
            }
            items.distinctBy { it.url ?: it.name ?: "" }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrending(): List<org.schabi.newpipe.extractor.InfoItem> = withContext(Dispatchers.IO) {
        try {
            val svc = ServiceList.YouTube
            val kioskId = svc.kioskList.defaultKioskId
            val info = KioskInfo.getInfo(svc, kioskId)
            val items = (info.relatedItems ?: emptyList()).toMutableList<org.schabi.newpipe.extractor.InfoItem>()
            if (items.isEmpty()) throw Exception("Kiosk empty")
            // Also fetch extra videos to pad the home feed
            try {
                val extra = searchYouTube("trending videos today", pages = 1)
                items.addAll(extra.relatedItems ?: emptyList())
            } catch (e: Exception) {
                // Ignore extra failure
            }
            items.distinctBy { it.url ?: "" }
        } catch (e: Exception) {
            try {
                val fallback = searchYouTube("trending videos", pages = 2)
                fallback.relatedItems ?: emptyList()
            } catch (inner: Exception) {
                emptyList()
            }
        }
    }
}

class ListExtractorPagingSource(
    private val listExtractor: org.schabi.newpipe.extractor.ListExtractor<*>
) {
    private var currentPage: org.schabi.newpipe.extractor.Page? = null
    var hasMore = true
        private set
    
    suspend fun loadInitial(): List<org.schabi.newpipe.extractor.InfoItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        listExtractor.fetchPage()
        currentPage = listExtractor.initialPage?.nextPage
        hasMore = listExtractor.initialPage?.hasNextPage() ?: false
        listExtractor.initialPage?.items ?: emptyList()
    }
    
    suspend fun loadNextPage(): List<org.schabi.newpipe.extractor.InfoItem> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val current = currentPage ?: return@withContext emptyList()
        try {
            val page = listExtractor.getPage(current)
            currentPage = page?.nextPage
            hasMore = page?.hasNextPage() ?: false
            page?.items ?: emptyList()
        } catch (e: Exception) {
            hasMore = false
            emptyList()
        }
    }
}