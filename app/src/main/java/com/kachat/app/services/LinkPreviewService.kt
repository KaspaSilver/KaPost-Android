package com.kachat.app.services

import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.Buffer
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

/**
 * A preview image fetched with a byte limit instead of holding whatever a server sends in memory:
 * the URLs here come from strangers' posts (iOS ee5ec98 `BoundedDownload`, through
 * `LinkPreviewService.imageData`). Coil then decodes it to the size it is shown at (iOS
 * `ImageThumbnail`), so a few megabytes of crafted image can't ask for gigabytes of bitmap.
 * Set per request with `fetcherFactory(BoundedImageFetcher.Factory(referer, maxBytes))`.
 */
class BoundedImageFetcher private constructor(
    private val url: String,
    private val referer: String?,
    private val maxBytes: Long,
    private val options: Options,
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        val bytes = LinkPreviewService.imageData(url, referer, maxBytes)
            ?: throw IOException("no image within the size limit")
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(
        private val referer: String?,
        private val maxBytes: Long = LinkPreviewService.MAX_IMAGE_BYTES,
    ) : Fetcher.Factory<android.net.Uri> {
        override fun create(data: android.net.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            val scheme = data.scheme?.lowercase()
            if (scheme != "http" && scheme != "https") return null
            return BoundedImageFetcher(data.toString(), referer, maxBytes, options)
        }
    }
}

/**
 * Which web links in a KaPost may draw a preview card (owner's call, 2026-11-18, iOS b78fdb0
 * `KaPostLinkPreviewPolicy`). Only a poster with a `.kachat` name gets previews at all, and then only
 * for popular, mainstream sites on this list, so a preview never fetches from an unknown server or
 * shows adult content. Anything else stays a plain link. A host matches itself and its subdomains
 * (www., m., mobile.). The same list holds for every hop: a redirect is followed only to an allowed
 * https page ([allowsRedirect]), and a preview's image loads only from the site or a known image
 * server of it ([allowsImage]).
 *
 * Every endpoint a preview fetches is on [allowedDomains] itself, so there is no list of extra
 * endpoints: YouTube's oEmbed (`https://www.youtube.com/oembed`) is youtube.com; Instagram and
 * Facebook are scraped at the link itself, only with Meta's crawler User-Agent; X has no special
 * endpoint. A new special endpoint may only be the platform's own domain, listed here explicitly.
 */
object KaPostLinkPreviewPolicy {
    val allowedDomains: Set<String> = setOf(
        // social and video
        "x.com", "twitter.com", "youtube.com", "youtu.be", "instagram.com", "facebook.com",
        "linkedin.com", "tiktok.com", "threads.net",
        // code, reference and writing
        "github.com", "wikipedia.org", "medium.com", "substack.com", "apple.com",
        // news
        "bbc.com", "bbc.co.uk", "reuters.com", "bloomberg.com", "cnbc.com", "cnn.com",
        "nytimes.com", "theguardian.com", "forbes.com", "apnews.com",
        // crypto news and data
        "coindesk.com", "cointelegraph.com", "decrypt.co", "theblock.co",
        "coingecko.com", "coinmarketcap.com",
        // Kaspa and KaChat
        "kaspa.org", "kas.fyi", "kaspa.stream", "kachat.app",
    )

    /**
     * The image servers of the allowed sites, for a preview's `og:image` (and the YouTube
     * thumbnail). Each matches itself and its subdomains. Subdomains of an allowed site need no
     * entry (miro.medium.com, media.cnn.com, imageio.forbes.com, dims.apnews.com,
     * images.cointelegraph.com, img.decrypt.co, coin-images.coingecko.com, s2.coinmarketcap.com).
     * Left out on purpose: shared hosting that serves anyone's files (S3 buckets, sanity.io,
     * arcpublishing.com, raw.githubusercontent.com, googleusercontent.com beyond YouTube's).
     */
    val imageCDNDomains: Set<String> = setOf(
        // X, YouTube, Meta (Instagram, Facebook, Threads), LinkedIn, TikTok
        "twimg.com", "ytimg.com", "ggpht.com", "yt3.googleusercontent.com",
        "cdninstagram.com", "fbcdn.net", "licdn.com", "tiktokcdn.com", "tiktokcdn-us.com",
        // GitHub, Substack, Wikipedia, Apple
        "githubassets.com", "avatars.githubusercontent.com", "repository-images.githubusercontent.com",
        "substackcdn.com", "wikimedia.org", "mzstatic.com",
        // BBC, New York Times, Guardian, CNBC, Bloomberg, The Block
        "bbci.co.uk", "nyt.com", "guim.co.uk", "cnbcfm.com", "bwbx.io", "tbstat.com",
    )

    /** Words that keep a link on an allowed site from previewing anyway (a path or query on a
     *  mainstream host can still point at adult content). Matched anywhere in the decoded,
     *  lowercased URL: none of them sits inside an ordinary word. */
    private val blockedSubstrings = listOf("porn", "xxx", "nsfw", "nude", "hentai", "onlyfans")
    /** Matched only as a whole word (between non-letters), so Sussex, Essex and unisex pass. */
    private val blockedTokens = setOf("sex", "sexy")

    /** [matchedText] is the exact substring matched, which the post leaves out of its text: the
     *  card shows it. [url] is what to fetch and open (a `www.` link gets `http://`, as iOS's link
     *  detector gives it). */
    data class Match(val url: String, val matchedText: String)

    private class MatchBox(val value: Match?)

    /** Same caching as iOS's `matchCache`: this runs from post rows. */
    private val matchCache = android.util.LruCache<String, MatchBox>(2_048)

    /** A link written as a link: http://, https:// or www., not inside a longer word, address or
     *  path. Stops at whitespace, quotes and angle brackets. */
    private val EXPLICIT_LINK = Regex(
        """(?<![\p{L}\p{N}@._/-])(?:https?://|www\.)[^\s<>"']+""",
        RegexOption.IGNORE_CASE,
    )
    /** Trailing prose punctuation is not part of the link ("... see https://x.com/a."). */
    private const val TRAILING_TRIM = ".,;:!?'\"*)]}>"

    /**
     * The first http(s) link in [text] that is written as a link ([isExplicitLink]), with the text
     * it was written as. A bare domain in prose ("I left facebook.com") is skipped, so it stays a
     * word in the text and draws no card.
     */
    fun firstLink(text: String): Match? {
        matchCache.get(text)?.let { return it.value }
        var found: Match? = null
        for (candidate in EXPLICIT_LINK.findAll(text)) {
            val written = trimTrailing(candidate.value)
            if (!isExplicitLink(written)) continue
            val url = if (written.startsWith("www.", ignoreCase = true)) "http://$written" else written
            // No host at all ("https://" and punctuation) isn't a link; anything else is the
            // first link, previewable or not, as iOS's detector.
            if (url.substringAfter("://").isEmpty()) continue
            found = Match(url, written)
            break
        }
        matchCache.put(text, MatchBox(found))
        return found
    }

    /**
     * The first explicit link in [text] if it may draw a card: a KaChat site link
     * ([com.kachat.app.ui.screens.KaChatSiteLink]) or a link [allows] takes; else null. As iOS, only
     * the first explicit link is looked at (posts' `textParts`, rooms' `firstExplicitHTTPLink` +
     * `previews`). Callers decide first whether the poster or sender has a `.kachat` name.
     */
    fun firstPreviewableLink(text: String): String? {
        val url = firstLink(text)?.url ?: return null
        return url.takeIf { com.kachat.app.ui.screens.KaChatSiteLink.isSiteLink(it) || allows(it) }
    }

    /** True when a match was written as a link: it starts with http://, https:// or www. (any
     *  case). Bare domains ("x.com") never preview. */
    fun isExplicitLink(matchedText: String): Boolean {
        val lowered = matchedText.trim().lowercase()
        return lowered.startsWith("http://") || lowered.startsWith("https://") || lowered.startsWith("www.")
    }

    fun allows(url: String): Boolean {
        val parsed = parse(url) ?: return false
        if (!matches(parsed.host, allowedDomains)) return false
        return !hasBlockedWords(url)
    }

    /** A page a preview may fetch: an allowed site or KaChat's own. */
    fun allowsPage(url: String): Boolean =
        allows(url) || com.kachat.app.ui.screens.KaChatSiteLink.isSiteLink(url)

    /** A redirect a page fetch may follow: https, to a page [allowsPage] takes. */
    fun allowsRedirect(url: String): Boolean = isHttps(url) && allowsPage(url)

    /**
     * An image a preview of [page] may load: https only (no http:, file: or data:), no credentials
     * or port, no adult words, from an allowed site, KaChat's own or one of their image servers
     * ([imageCDNDomains]), for a page that is itself allowed. Anything else draws the card without
     * its image. Redirects of an image fetch are held to the same rule.
     */
    fun allowsImage(url: String, page: String): Boolean {
        if (!isHttps(url)) return false
        val parsed = parse(url) ?: return false
        if (!allowsPage(page) || hasBlockedWords(url)) return false
        return matches(parsed.host, allowedDomains) || matches(parsed.host, imageCDNDomains)
    }

    private fun isHttps(url: String): Boolean =
        url.substringBefore("://", missingDelimiterValue = "").equals("https", ignoreCase = true)

    /**
     * The URL as OkHttp will fetch it, or null unless it is http(s) with a host and no user,
     * password or port (iOS `url.user == nil, url.password == nil, url.port == nil`). The authority
     * is also read from the raw text, so an explicit default port (`:443`) or a `user@` that a
     * parser would drop is refused too.
     */
    private fun parse(url: String): okhttp3.HttpUrl? {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        val authority = url.substring(scheme.length + 3).takeWhile { it !in "/?#\\" }
        if ('@' in authority) return null
        val hostAndPort = if (authority.startsWith("[")) authority.substringAfter("]", "") else authority
        if (':' in hostAndPort) return null
        val parsed = url.toHttpUrlOrNull() ?: return null
        if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) return null
        if (parsed.port != okhttp3.HttpUrl.defaultPort(parsed.scheme)) return null
        if (parsed.host.isEmpty()) return null
        return parsed
    }

    private fun matches(host: String, domains: Set<String>): Boolean {
        val lowered = host.lowercase()
        return domains.any { lowered == it || lowered.endsWith(".$it") }
    }

    /** The URL decoded (twice-encoded too, "%2570orn"), lowercased, then checked: the
     *  unambiguous words anywhere, the rest only as whole words. */
    private fun hasBlockedWords(url: String): Boolean {
        var text = url
        for (pass in 0 until 3) {
            val decoded = removingPercentEncoding(text)
            if (decoded == null || decoded == text) break
            text = decoded
        }
        text = text.lowercase()
        if (blockedSubstrings.any { text.contains(it) }) return true
        val tokens = text.split(Regex("""[^\p{L}\p{N}]+"""))
        return tokens.any { it in blockedTokens }
    }

    /** Foundation's `removingPercentEncoding`: every `%XX` decoded as UTF-8, `+` left alone; null
     *  for a malformed escape or bytes that aren't UTF-8. */
    private fun removingPercentEncoding(text: String): String? {
        if ('%' !in text) return text
        val out = java.io.ByteArrayOutputStream(text.length)
        var index = 0
        while (index < text.length) {
            val ch = text[index]
            if (ch == '%') {
                if (index + 2 >= text.length) return null
                val hi = Character.digit(text[index + 1], 16)
                val lo = Character.digit(text[index + 2], 16)
                if (hi < 0 || lo < 0) return null
                out.write(hi * 16 + lo)
                index += 3
            } else {
                out.write(ch.toString().toByteArray(Charsets.UTF_8))
                index++
            }
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                .decode(java.nio.ByteBuffer.wrap(out.toByteArray()))
                .toString()
        } catch (e: java.nio.charset.CharacterCodingException) {
            null
        }
    }

    private fun trimTrailing(raw: String): String {
        var end = raw.length
        while (end > 0) {
            val last = raw[end - 1]
            if (last !in TRAILING_TRIM) break
            // A closing bracket the link itself opened stays ("wiki/Foo_(bar)"), as iOS's detector.
            if (last == ')' && raw.substring(0, end - 1).count { it == '(' } > raw.substring(0, end - 1).count { it == ')' }) break
            end--
        }
        return raw.substring(0, end)
    }
}

/** Open Graph metadata scraped from a message link, for rendering a rich preview card. */
data class LinkPreviewData(
    val url: String,
    val title: String?,
    val description: String?,
    val imageUrl: String?,
    val siteName: String?,
    /** Set only for Nextcloud public-share links: "image"/"video"/"audio"/"pdf"/"file" (generic
     *  fallback — a valid share ALWAYS gets a kind, never null). Tells the card this link is a
     *  directly shareable file and which kind, so tapping opens the in-app viewer / system player
     *  instead of the browser. Mirrors iOS's `LinkPreviewData.nextcloudMedia`. */
    val nextcloudMedia: String? = null,
    /** The share's raw-file `/download` URL (streams via range requests) — the viewer's source. */
    val mediaDownloadUrl: String? = null,
    /** The shared file's Content-Length from the HEAD probe — the attachment card's size caption. */
    val mediaByteSize: Long? = null,
    /** The Nextcloud server answered that this public share no longer exists (404/410), so the
     *  sender revoked it or it expired. The card renders a neutral "no longer shared" tile and,
     *  crucially, stops showing the share URL - a link nobody can open is just the sender's
     *  server address sitting in the transcript. Only ever set on a definite answer FROM the
     *  server; a network failure leaves this false and the card simply renders nothing.
     *  Mirrors iOS's `LinkPreviewData.nextcloudShareRevoked`. */
    val nextcloudShareRevoked: Boolean = false
)

/**
 * Fetches Open Graph preview metadata for links in posts and Public Chats rooms. A card is only
 * made for a poster or sender with a `.kachat` name and a link to KaChat's site or an allowed site
 * ([KaPostLinkPreviewPolicy]), and then fetches by itself on render; [fetchPreview] refuses any
 * other page, every redirect is held to the same list, and images to
 * [KaPostLinkPreviewPolicy.allowsImage], so an unknown server is never contacted (iOS b78fdb0).
 * Each reader's own device does this fetch when the post renders, rather than the sender
 * embedding preview data in the encrypted message payload, so link previews never bloat the
 * on-chain/indexer payload.
 *
 * Plain object, not Hilt-injected - [com.kachat.app.ui.screens.MessageBubble] and
 * `GroupMessageBubble` are presentational composables with no ViewModel threaded through, so this
 * mirrors the existing no-DI utility pattern [com.kachat.app.util.TextLinkify] already uses,
 * rather than changing those composables' signatures. Owns its own short-timeout client, separate
 * from [com.kachat.app.di.AppModule]'s REST-API client - arbitrary user-supplied URLs need
 * tighter timeouts than trusted API hosts.
 */
object LinkPreviewService {
    private const val FETCH_TIMEOUT_SECONDS = 8L
    private const val MAX_BODY_BYTES = 1_000_000L
    private const val CACHE_LIMIT = 2_048
    private const val CACHE_TTL_MS = 24L * 60 * 60 * 1000
    /** YouTube's oEmbed answer (iOS `maxOEmbedBytes`). */
    private const val MAX_OEMBED_BYTES = 100_000L
    /** OkHttp's own follow-up limit; redirects are followed by hand so each hop is checked. */
    private const val MAX_REDIRECTS = 20

    /** The most a preview card's image may be (iOS `maxImageBytes`). */
    const val MAX_IMAGE_BYTES = 5_000_000L
    /** Nextcloud's full photo and PDF viewer: above the card cap, since someone tapped to see
     *  this; the server is still a stranger's (iOS ee5ec98 `fullImageMaxBytes`, was 50 MB). */
    const val FULL_IMAGE_MAX_BYTES = 15_000_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .callTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Redirects are followed by hand (`executeHeld`), each hop held to the allowed list.
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    // `null` value = "fetched, but no preview data found" - still worth caching so a bad/plain
    // link isn't refetched on every scroll. Bounded FIFO eviction, not LRU - simplicity over
    // optimality for a cosmetic, cheap-to-refetch-on-relaunch cache.
    private val cache = LinkedHashMap<String, LinkPreviewData?>()
    /** When each entry was stored: past [CACHE_TTL_MS] it is dropped and refetched - a link whose
     *  page changed (or whose preview image URL was a short-lived signed one) would otherwise
     *  stay stale for the whole process lifetime (iOS cacheTTL). */
    private val cacheStoredAt = HashMap<String, Long>()
    private val cacheLock = Any()

    private val titleTagRegex = Regex("<title[^>]*>([^<]*)</title>", RegexOption.IGNORE_CASE)

    private val youTubeHosts = setOf("youtube.com", "www.youtube.com", "m.youtube.com", "youtu.be")

    /** A real desktop-browser User-Agent. A self-identifying bot UA (the old value) makes sites
     *  like Instagram serve a login/consent wall whose HTML has a title but no post-specific
     *  `og:image`; a browser UA gets the real page. Shared with the image loader (see
     *  [com.kachat.app.ui.screens.LinkPreviewCard]) so image CDNs (cdninstagram/fbcdn) that reject
     *  non-browser requests don't 403. Mirrors iOS's `LinkPreviewService.browserUserAgent`. */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.4 Safari/605.1.15"

    /** Meta's own link-scraper User-Agent. Instagram/Facebook serve full Open Graph tags -
     *  including `og:image` - to this crawler, whereas a browser UA increasingly gets a login wall
     *  with no post image. Used for the scrape on Meta hosts. Mirrors iOS's
     *  `facebookExternalHitUserAgent`. */
    const val FACEBOOK_EXTERNAL_HIT_UA =
        "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    /** Hosts that gate `og:image` behind Meta's crawler UA (a plain/bot UA gets a login wall). */
    private val metaScrapeHosts = setOf(
        "instagram.com", "www.instagram.com", "m.instagram.com",
        "facebook.com", "www.facebook.com", "m.facebook.com", "fb.watch"
    )

    /** `https://host/s/TOKEN` (or `/index.php/s/TOKEN`, token 10+ url-safe chars). */
    private val nextcloudSharePathRegex = Regex("""^(/index\.php)?/s/([A-Za-z0-9_-]{10,})/?$""")

    /** What a Nextcloud server answers for a public share that was deleted, expired, or had its
     *  link removed. */
    private val SHARE_GONE_STATUSES = setOf(404, 410)

    data class NextcloudShareEndpoints(val downloadUrl: String, val previewUrl: String)

    /** A Nextcloud public share's raw-file `/download` and thumbnail `/preview` endpoints
     *  (query/fragment stripped); null for any other URL. */
    fun nextcloudShareEndpoints(url: String): NextcloudShareEndpoints? {
        val httpUrl = url.toHttpUrlOrNull() ?: return null
        val match = nextcloudSharePathRegex.find(httpUrl.encodedPath) ?: return null
        val prefix = if (match.groupValues[1].isNotEmpty()) "/index.php" else ""
        val base = httpUrl.newBuilder()
            .encodedPath("$prefix/s/${match.groupValues[2]}")
            .query(null)
            .fragment(null)
            .build()
            .toString()
        return NextcloudShareEndpoints(downloadUrl = "$base/download", previewUrl = "$base/preview")
    }

    suspend fun fetchPreview(url: String): LinkPreviewData? {
        synchronized(cacheLock) {
            if (cache.containsKey(url)) {
                val storedAt = cacheStoredAt[url] ?: 0L
                if (System.currentTimeMillis() - storedAt < CACHE_TTL_MS) return cache[url]
                // Expired: drop it and fall through to a fresh fetch, which re-stores it at the
                // back of the FIFO like any new entry.
                cache.remove(url)
                cacheStoredAt.remove(url)
            }
        }

        val result = withContext(Dispatchers.IO) { fetchAndParse(url) }

        synchronized(cacheLock) {
            if (!cache.containsKey(url) && cache.size >= CACHE_LIMIT) {
                val oldestKey = cache.keys.firstOrNull()
                if (oldestKey != null) {
                    cache.remove(oldestKey)
                    cacheStoredAt.remove(oldestKey)
                }
            }
            cache[url] = result
            cacheStoredAt[url] = System.currentTimeMillis()
        }
        return result
    }

    private fun fetchAndParse(url: String): LinkPreviewData? {
        val scheme = url.substringBefore("://", missingDelimiterValue = "").lowercase()
        if (scheme != "http" && scheme != "https") return null
        // The callers only ask for allowed links; this holds the rule here too, so no path can
        // make a preview contact a server off the list (iOS b78fdb0).
        if (!KaPostLinkPreviewPolicy.allowsPage(url)) return null

        // Nextcloud public shares are media files, not pages — no Open Graph to scrape. Type
        // comes from a HEAD on the raw-file endpoint instead (see fetchNextcloudPreview).
        nextcloudShareEndpoints(url)?.let { return fetchNextcloudPreview(url, it) }

        // YouTube serves a cookie-consent-wall page (no Open Graph tags at all) to plain scraper
        // requests instead of the real video page, so the generic scrape below never finds
        // anything for a youtube.com/youtu.be link. YouTube's own oEmbed endpoint is built
        // exactly for this (no consent wall, no API key needed).
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull()
        if (host != null && host in youTubeHosts) {
            fetchYouTubeOEmbed(url)?.let { return it }
            // Fall through to the generic scrape only if oEmbed itself failed (e.g. a
            // private/deleted video) - unlikely to succeed either, but no harm trying.
        }

        // Instagram/Facebook serve a login wall (title/description but no post `og:image`) to a
        // plain/bot UA; Meta's crawler UA gets the full Open Graph tags. Everything else gets a
        // real browser UA (some CDNs 403 a self-identifying bot).
        val scrapeUserAgent = if (host != null && host in metaScrapeHosts) FACEBOOK_EXTERNAL_HIT_UA else BROWSER_USER_AGENT
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", scrapeUserAgent)
                .build()

            // Stops at a redirect off the allowed list: the 3xx itself shows no preview.
            executeHeld(client, request, KaPostLinkPreviewPolicy::allowsRedirect).use { response ->
                if (!response.isSuccessful) return if (host != null && host.contains("instagram")) instagramFallback(url, null) else null
                val source = response.body?.source() ?: return null

                val buffer = Buffer()
                while (buffer.size < MAX_BODY_BYTES) {
                    val read = source.read(buffer, MAX_BODY_BYTES - buffer.size)
                    if (read == -1L) break
                }
                val html = buffer.readString(Charsets.UTF_8)
                val parsed = parseHtml(html, url)
                if (host != null && host.contains("instagram")) instagramFallback(url, parsed) else parsed
            }
        } catch (e: Exception) {
            if (host != null && host.contains("instagram")) instagramFallback(url, null) else null
        }
    }

    /**
     * Instagram stopped serving Open Graph data - title, description and above all `og:image` -
     * to anyone not logged in, whatever the User-Agent (iOS checked 2026-09-22 with a browser UA,
     * Meta's own crawler UA and Twitterbot: every post, reel and profile comes back as a bare
     * "Instagram" shell, and the oEmbed endpoint now needs a Meta API token). So an Instagram
     * link cannot show the post's picture. Rather than an empty image box, the card names what
     * the link is from the URL itself: the account, and whether it is a post, a reel, a story or
     * a profile. A scrape that did find an image is used as it is. Mirrors iOS 2010124.
     */
    private fun instagramFallback(url: String, scraped: LinkPreviewData?): LinkPreviewData {
        if (scraped?.imageUrl != null) return scraped
        val parts = runCatching { java.net.URI(url).path.orEmpty() }.getOrDefault("")
            .split('/').filter { it.isNotEmpty() }
        val first = parts.firstOrNull()?.lowercase()
        val (title, description) = when {
            first == "p" -> "Instagram post" to "Open in Instagram to see the post."
            first == "reel" || first == "reels" -> "Instagram reel" to "Open in Instagram to watch the reel."
            first == "stories" -> "Instagram story" to "Open in Instagram to see the story."
            first != null && first !in setOf("explore", "accounts", "direct") ->
                "@${parts.first()} on Instagram" to "Open in Instagram to see the profile."
            else -> "Instagram" to "Open in Instagram."
        }
        return LinkPreviewData(
            url = url,
            title = scraped?.title?.takeIf { it != "Instagram" } ?: title,
            description = scraped?.description ?: description,
            imageUrl = null,
            siteName = "Instagram",
        )
    }

    private fun fetchYouTubeOEmbed(url: String): LinkPreviewData? {
        val oEmbedUrl = "https://www.youtube.com/oembed".toHttpUrlOrNull()
            ?.newBuilder()
            ?.addQueryParameter("url", url)
            ?.addQueryParameter("format", "json")
            ?.build() ?: return null

        return try {
            val request = Request.Builder()
                .url(oEmbedUrl)
                .header("User-Agent", "Mozilla/5.0 (compatible; KaChatLinkPreview/1.0)")
                .build()

            // youtube.com itself (on the allowed list), bounded and held to the page redirect rule
            // like the scrape (iOS b78fdb0).
            executeHeld(client, request, KaPostLinkPreviewPolicy::allowsRedirect).use { response ->
                val bytes = boundedBytes(response, MAX_OEMBED_BYTES) ?: return null
                val json = JSONObject(String(bytes, Charsets.UTF_8))
                val title = json.optString("title").takeIf { it.isNotEmpty() }
                val thumbnailUrl = json.optString("thumbnail_url").takeIf { it.isNotEmpty() }
                if (title == null && thumbnailUrl == null) return null

                LinkPreviewData(
                    url = url,
                    title = title,
                    description = null,
                    imageUrl = thumbnailUrl,
                    siteName = "YouTube"
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * The share link itself carries no file type, so HEAD the `/download` URL and branch on
     * `Content-Type`. Non-media shares return null, which renders as a plain link. The card's
     * poster is the share's `/preview` thumbnail; the full-quality fetch/stream only happens
     * when the user taps the card.
     */
    private fun fetchNextcloudPreview(url: String, endpoints: NextcloudShareEndpoints): LinkPreviewData? {
        return try {
            val request = Request.Builder().url(endpoints.downloadUrl).head().build()
            // A share probe's redirect goes only to an allowed https page (iOS's
            // `LinkPreviewRedirectGuard`).
            executeHeld(client, request, KaPostLinkPreviewPolicy::allowsRedirect).use { response ->
                if (!response.isSuccessful) {
                    // A status the SERVER returned saying the share is gone, as opposed to a
                    // request that never got an answer - only the former justifies hiding the
                    // link. Anything else (403 password prompt, 5xx, a proxy error) leaves the
                    // link intact and the card simply does not render.
                    if (response.code in SHARE_GONE_STATUSES) {
                        return LinkPreviewData(
                            url = url,
                            title = null,
                            description = null,
                            imageUrl = null,
                            siteName = null,
                            nextcloudShareRevoked = true
                        )
                    }
                    return null
                }
                val contentType = (response.header("Content-Type") ?: "").lowercase()
                // Every valid share gets a kind — "file" is the generic fallback for docs,
                // archives, and anything else, so a share never renders as a bare link.
                val kind = when {
                    contentType.startsWith("image/") -> "image"
                    contentType.startsWith("video/") -> "video"
                    contentType.startsWith("audio/") -> "audio"
                    contentType.contains("pdf") -> "pdf"
                    else -> "file"
                }
                val filename = filenameFromContentDisposition(response.header("Content-Disposition"))
                val fallbackTitle = when (kind) {
                    "image" -> "Photo"
                    "video" -> "Video"
                    "audio" -> "Audio"
                    "pdf" -> "PDF"
                    else -> "File"
                }
                val host = runCatching { java.net.URI(url).host }.getOrNull()
                LinkPreviewData(
                    url = url,
                    title = filename ?: fallbackTitle,
                    description = null,
                    imageUrl = endpoints.previewUrl,
                    siteName = if (host != null) "Nextcloud · $host" else "Nextcloud",
                    nextcloudMedia = kind,
                    mediaDownloadUrl = endpoints.downloadUrl,
                    mediaByteSize = response.header("Content-Length")?.toLongOrNull()
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Downloads a (public) share file to app-private cache for local rendering — the in-app PDF
     * viewer's source. Its own client without [FETCH_TIMEOUT_SECONDS]'s overall call cap: a
     * multi-MB document on a home server's uplink legitimately takes longer than a page scrape.
     * Null on any failure or if the file exceeds [maxBytes]: the download stops there, whatever
     * the server sends and whatever Content-Length said (iOS ee5ec98). iOS fetches these through
     * `imageData`, so the same rule holds: nothing is fetched unless
     * [KaPostLinkPreviewPolicy.allowsImage] takes it (redirects too), and the Referer goes only to
     * the share's own host (iOS b78fdb0).
     */
    suspend fun downloadToCacheFile(
        context: android.content.Context,
        url: String,
        maxBytes: Long = FULL_IMAGE_MAX_BYTES,
        /** The share link: Nextcloud's public download wants it (iOS sends it through `imageData`). */
        referer: String? = null,
    ): java.io.File? = withContext(Dispatchers.IO) {
        var temp: java.io.File? = null
        if (referer == null || !KaPostLinkPreviewPolicy.allowsImage(url, referer)) return@withContext null
        try {
            // iOS's headers: its session's browser User-Agent and the share link as Referer, on
            // the share's own host only.
            val request = Request.Builder().url(url)
                .header("User-Agent", BROWSER_USER_AGENT)
                .apply { if (sameHost(url, referer)) header("Referer", referer) }
                .build()
            executeHeld(downloadClient, request, { KaPostLinkPreviewPolicy.allowsImage(it, referer) }, refererHost = hostOf(referer)).use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                if (body.contentLength() > maxBytes) return@withContext null
                val dir = java.io.File(context.cacheDir, "nextcloud_previews").apply { mkdirs() }
                val file = java.io.File(dir, "share-${url.hashCode()}.bin")
                // Each download its own temp file, renamed when whole: two of one URL at once
                // can't write into, or read, the other's half-written file.
                val partial = java.io.File.createTempFile("share-${url.hashCode()}-", ".part", dir)
                temp = partial
                var total = 0L
                var tooLarge = false
                partial.outputStream().use { out ->
                    val input = body.byteStream()
                    val chunk = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(chunk)
                        if (read < 0) break
                        total += read
                        if (total > maxBytes) {
                            tooLarge = true
                            break
                        }
                        out.write(chunk, 0, read)
                    }
                }
                if (tooLarge || !partial.renameTo(file)) {
                    partial.delete()
                    null
                } else {
                    file
                }
            }
        } catch (e: Exception) {
            temp?.delete()
            null
        }
    }

    /**
     * A preview image's bytes, at most [maxBytes], with the same browser User-Agent as the scrape
     * plus a `Referer` (the page the image belongs to) when the image is on the page's own host:
     * image CDNs reject bare requests. If that is refused, once more with Meta's crawler User-Agent
     * and no Referer. Null on any failure, or as soon as Content-Length or the body passes the
     * limit (iOS ee5ec98 `imageData`, `BoundedDownload.Overflow.reject`), and no request at all
     * for an image [KaPostLinkPreviewPolicy.allowsImage] refuses: only https, from the allowed
     * sites and their image servers, redirects included (iOS b78fdb0).
     */
    suspend fun imageData(url: String, referer: String?, maxBytes: Long = MAX_IMAGE_BYTES): ByteArray? = withContext(Dispatchers.IO) {
        if (referer == null || !KaPostLinkPreviewPolicy.allowsImage(url, referer)) return@withContext null
        fetchImageBytes(url, referer, null, sendsReferer = true, maxBytes = maxBytes)
            ?: fetchImageBytes(url, referer, FACEBOOK_EXTERNAL_HIT_UA, sendsReferer = false, maxBytes = maxBytes)
    }

    /** The Referer goes only to the page's own host: another host (a CDN) isn't told which post
     *  it was shown in. A redirect is followed only to an image `allowsImage` takes, and loses the
     *  Referer if it leaves the page's host. */
    private suspend fun fetchImageBytes(url: String, page: String, userAgent: String?, sendsReferer: Boolean, maxBytes: Long): ByteArray? {
        val first = try {
            Request.Builder().url(url)
                .header("User-Agent", userAgent ?: BROWSER_USER_AGENT)
                .header("Accept", "image/avif,image/webp,image/png,image/*,*/*;q=0.8")
                .apply { if (sendsReferer && sameHost(url, page)) header("Referer", page) }
                .build()
        } catch (e: IllegalArgumentException) {
            return null
        }
        val pageHost = hostOf(page)
        // The body is read in the callback, so a cancel (the card gone, iOS's task cancelled with
        // its view) stops the download wherever it is, not just between reads.
        return suspendCancellableCoroutine<ByteArray?> { continuation ->
            val current = java.util.concurrent.atomic.AtomicReference<Call?>(null)
            continuation.invokeOnCancellation { current.get()?.cancel() }

            fun start(request: Request, hops: Int) {
                val call = imageClient.newCall(request)
                current.set(call)
                if (continuation.isCancelled) {
                    call.cancel()
                    return
                }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (continuation.isActive) continuation.resume(null)
                    }

                    override fun onResponse(call: Call, response: Response) {
                        if (response.isRedirect) {
                            val target = response.header("Location")?.let { response.request.url.resolve(it) }
                            response.close()
                            if (target == null || hops >= MAX_REDIRECTS ||
                                !KaPostLinkPreviewPolicy.allowsImage(target.toString(), page)
                            ) {
                                if (continuation.isActive) continuation.resume(null)
                                return
                            }
                            val next = request.newBuilder().url(target)
                                .apply { if (target.host != pageHost) removeHeader("Referer") }
                                .build()
                            start(next, hops + 1)
                            return
                        }
                        val bytes = try {
                            response.use { boundedBytes(it, maxBytes) }
                        } catch (e: Exception) {
                            null
                        }
                        if (continuation.isActive) continuation.resume(bytes)
                    }
                })
            }
            start(first, 0)
        }
    }

    /**
     * Runs [first] and follows its redirects by hand, each only when [allowHop] takes the target
     * (iOS `BoundedDownload.RedirectPolicy`). A refused redirect hands the 3xx itself back, so a
     * caller that wants a 2xx shows nothing. With [refererHost], a hop off that host loses the
     * Referer. The caller closes the response.
     */
    private fun executeHeld(
        client: OkHttpClient,
        first: Request,
        allowHop: (String) -> Boolean,
        refererHost: String? = null,
    ): Response {
        var request = first
        var hops = 0
        while (true) {
            val response = client.newCall(request).execute()
            if (!response.isRedirect || hops >= MAX_REDIRECTS) return response
            val target = response.header("Location")?.let { response.request.url.resolve(it) } ?: return response
            if (!allowHop(target.toString())) return response
            response.close()
            val builder = request.newBuilder().url(target)
            if (refererHost != null && target.host != refererHost) builder.removeHeader("Referer")
            // 303 turns anything but a HEAD into a GET, as OkHttp's own follow-up does.
            if (response.code == 303 && request.method != "HEAD") builder.get()
            request = builder.build()
            hops++
        }
    }

    private fun hostOf(url: String): String? = url.toHttpUrlOrNull()?.host

    private fun sameHost(a: String, b: String): Boolean {
        val hostA = hostOf(a) ?: return false
        return hostA == hostOf(b)
    }

    private fun boundedBytes(response: Response, maxBytes: Long): ByteArray? {
        if (!response.isSuccessful) return null
        val body = response.body ?: return null
        if (body.contentLength() > maxBytes) return null
        val source = body.source()
        val buffer = Buffer()
        while (true) {
            val read = source.read(buffer, minOf(64L * 1024, maxBytes + 1 - buffer.size))
            if (read == -1L) break
            if (buffer.size > maxBytes) return null
        }
        return buffer.readByteArray()
    }

    private val downloadClient = OkHttpClient.Builder()
        .connectTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        // Followed by hand, each hop held to `allowsImage` (iOS b78fdb0).
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    /** Preview images: the whole download in [FETCH_TIMEOUT_SECONDS], as iOS's session
     *  (`timeoutIntervalForResource` 8 s). */
    private val imageClient = downloadClient.newBuilder()
        .callTimeout(FETCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    /** Pulls the shared file's name out of `Content-Disposition: attachment; filename="x.jpg"`
     *  (or the RFC 5987 `filename*=UTF-8''x.jpg` form) for the card title. */
    private fun filenameFromContentDisposition(header: String?): String? {
        if (header == null) return null
        Regex("""filename\*=UTF-8''([^;]+)""").find(header)?.let { match ->
            val raw = match.groupValues[1].trim()
            return runCatching { android.net.Uri.decode(raw) }.getOrDefault(raw).takeIf { it.isNotEmpty() }
        }
        Regex("""filename="([^"]+)"""").find(header)?.let { match ->
            return match.groupValues[1].takeIf { it.isNotEmpty() }
        }
        return null
    }

    private fun parseHtml(html: String, url: String): LinkPreviewData? {
        val title = metaContent("og:title", html, useProperty = true) ?: titleTag(html)
        val description = metaContent("og:description", html, useProperty = true) ?: metaContent("description", html, useProperty = false)
        // Entities decoded BEFORE the URL is used: an HTML attribute writes `&` as `&amp;`, and a
        // signed CDN link (Instagram's cdninstagram `...&oh=...&oe=...`) left as `&amp;oh=` loses
        // its signature - the CDN answers 403 and the card falls back to text only (iOS c97e78f:
        // raw 403, decoded 200).
        val imageUrl = metaContent("og:image", html, useProperty = true)?.let { resolveUrl(it.decodeHtmlEntities(), url) }
        val siteName = metaContent("og:site_name", html, useProperty = true) ?: runCatching { java.net.URI(url).host }.getOrNull()

        if (title == null && description == null && imageUrl == null) return null

        return LinkPreviewData(
            url = url,
            title = title?.decodeHtmlEntities(),
            description = description?.decodeHtmlEntities(),
            imageUrl = imageUrl,
            siteName = siteName
        )
    }

    /** Matches `<meta property="og:title" content="...">` in either attribute order, single or
     *  double quotes - real-world OG tags aren't consistent about ordering/quoting. */
    private fun metaContent(tagValue: String, html: String, useProperty: Boolean): String? {
        val attribute = if (useProperty) "property" else "name"
        val escaped = Regex.escape(tagValue)
        val patterns = listOf(
            """<meta[^>]+$attribute=["']$escaped["'][^>]+content=["']([^"']*)["']""",
            """<meta[^>]+content=["']([^"']*)["'][^>]+$attribute=["']$escaped["']"""
        )
        for (pattern in patterns) {
            val match = Regex(pattern, RegexOption.IGNORE_CASE).find(html)
            val raw = match?.groupValues?.getOrNull(1)?.trim()
            if (!raw.isNullOrEmpty()) return raw
        }
        return null
    }

    private fun titleTag(html: String): String? {
        val raw = titleTagRegex.find(html)?.groupValues?.getOrNull(1)?.trim()
        return raw?.takeIf { it.isNotEmpty() }
    }

    private fun resolveUrl(raw: String, base: String): String {
        return try {
            java.net.URI(base).resolve(raw).toString()
        } catch (e: Exception) {
            raw
        }
    }

    private fun String.decodeHtmlEntities(): String {
        return this
            .replace("&amp;", "&")
            .replace("&#38;", "&")
            .replace("&#x26;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }
}
