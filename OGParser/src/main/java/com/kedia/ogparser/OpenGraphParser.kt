package com.kedia.ogparser

import android.content.Context
import kotlinx.coroutines.*
import org.jsoup.Jsoup
import kotlin.coroutines.CoroutineContext


class OpenGraphParser(
    private val listener: OpenGraphCallback,
    private var showNullOnEmpty: Boolean = false,
    private val context: Context? = null
) {

    private val sharedPrefs: SharedPrefs? = context?.let { SharedPrefs(it) }

    private var url: String = ""

    private val AGENT = "Mozilla"
    private val REFERRER = "http://www.google.com"
    private val TIMEOUT = 10000
    private val DOC_SELECT_QUERY = "meta[property^=og:]"
    private val OPEN_GRAPH_KEY = "content"
    private val PROPERTY = "property"
    private val OG_IMAGE = "og:image"
    private val OG_DESCRIPTION = "og:description"
    private val OG_URL = "og:url"
    private val OG_TITLE = "og:title"
    private val OG_SITE_NAME = "og:site_name"
    private val OG_TYPE = "og:type"

    private var openGraphResult: OpenGraphResult? = null

    fun parse(url: String) {
        this.url = url
        parseLink().parse()
    }

    inner class parseLink : CoroutineScope {

        private val job: Job = Job()
        override val coroutineContext: CoroutineContext
            get() = Dispatchers.Main + job


        fun parse() = launch {
            val result = fetchContent()
            result?.let {
                listener.onPostResponse(it)
            }
        }
    }

    private suspend fun fetchContent() = withContext(Dispatchers.IO) {
        if (!url.contains("http")) {
            url = "http://$url"
        }
        if (sharedPrefs?.urlExists(url) == true) {
            return@withContext sharedPrefs?.getOpenGraphResult(url)
        }
        openGraphResult = OpenGraphResult()
        try {
            val response = Jsoup.connect(url)
                .ignoreContentType(true)
                .userAgent(AGENT)
                .referrer(REFERRER)
                .timeout(TIMEOUT)
                .followRedirects(true)
                .execute()

            val doc = response.parse()

            val ogTags = doc.select(DOC_SELECT_QUERY)
            when {
                ogTags.size > 0 ->
                    ogTags.forEachIndexed { index, _ ->
                        val tag = ogTags[index]
                        val text = tag.attr(PROPERTY)

                        when (text) {
                            OG_IMAGE -> {
                                openGraphResult!!.image = (tag.attr(OPEN_GRAPH_KEY))
                            }
                            OG_DESCRIPTION -> {
                                openGraphResult!!.description = (tag.attr(OPEN_GRAPH_KEY))
                            }
                            OG_URL -> {
                                openGraphResult!!.url = (tag.attr(OPEN_GRAPH_KEY))
                            }
                            OG_TITLE -> {
                                openGraphResult!!.title = (tag.attr(OPEN_GRAPH_KEY))
                            }
                            OG_SITE_NAME -> {
                                openGraphResult!!.siteName = (tag.attr(OPEN_GRAPH_KEY))
                            }
                            OG_TYPE -> {
                                openGraphResult!!.type = (tag.attr(OPEN_GRAPH_KEY))
                            }
                        }
                    }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            launch(Dispatchers.Main) {
                listener.onError(e.localizedMessage)
            }
            return@withContext null
        }

        if ((openGraphResult!!.title?.isEmpty() == true || openGraphResult!!.title.equals("null")) && (openGraphResult!!.description?.isEmpty() == true || openGraphResult!!.description.equals("null")) && showNullOnEmpty) {
            launch(Dispatchers.Main) {
                listener.onError("Null or empty response from the server")
            }
            return@withContext null
        }
        openGraphResult?.let { sharedPrefs?.setOpenGraphResult(it, url) }
        return@withContext openGraphResult
    }
}