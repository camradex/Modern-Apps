package com.vayunmathur.web.util

import android.net.Uri
import com.vayunmathur.web.data.SearchResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object SearchEngine {
    private val SEARCH_PATTERNS = listOf(
        Regex("""^https?://(www\.)?duckduckgo\.com/""") to "q",
        Regex("""^https?://(www\.)?google\.[a-z.]+/search""") to "q",
        Regex("""^https?://(www\.)?bing\.com/search""") to "q",
        Regex("""^https?://search\.yahoo\.com/search""") to "p",
        Regex("""^https?://(www\.)?ecosia\.org/search""") to "q",
        Regex("""^https?://(www\.)?startpage\.com/(do/search|sp/search)""") to "query",
        Regex("""^https?://html\.duckduckgo\.com/html/""") to "q",
    )

    private val SEARCH_ENGINE_HOMES = listOf(
        Regex("""^https?://(www\.)?duckduckgo\.com(/.*)?$"""),
        Regex("""^https?://html\.duckduckgo\.com(/.*)?$"""),
        Regex("""^https?://(www\.)?google\.[a-z.]+(/.*)?$"""),
        Regex("""^https?://(www\.)?bing\.com(/.*)?$"""),
        Regex("""^https?://(search\.)?yahoo\.com(/.*)?$"""),
        Regex("""^https?://(www\.)?ecosia\.org(/.*)?$"""),
        Regex("""^https?://(www\.)?startpage\.com(/.*)?$"""),
        Regex("""^https?://(www\.)?baidu\.com(/.*)?$"""),
        Regex("""^https?://(www\.)?yandex\.(com|ru)(/.*)?$"""),
    )

    fun isSearchEngineUrl(url: String): Boolean {
        return SEARCH_ENGINE_HOMES.any { it.containsMatchIn(url) }
    }

    fun extractSearchQuery(url: String): String? {
        for ((pattern, param) in SEARCH_PATTERNS) {
            if (pattern.containsMatchIn(url)) {
                return Uri.parse(url).getQueryParameter(param)?.takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    suspend fun fetchSearchResults(query: String): List<SearchResult> = withContext(Dispatchers.IO) {
        val doc = Jsoup.connect("https://html.duckduckgo.com/html/?q=${Uri.encode(query)}")
            .userAgent("Mozilla/5.0")
            .get()

        doc.select(".result__body").mapNotNull { element ->
            val linkEl = element.selectFirst(".result__a") ?: return@mapNotNull null
            val title = linkEl.text().takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val rawHref = linkEl.attr("href")
            val url = unwrapDdgUrl(rawHref)
            val snippet = element.selectFirst(".result__snippet")?.text().orEmpty()
            SearchResult(title = title, url = url, snippet = snippet)
        }
    }

    private fun unwrapDdgUrl(href: String): String {
        val parsed = Uri.parse(href)
        return parsed.getQueryParameter("uddg") ?: href
    }
}
