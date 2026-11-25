package com.vanderck.xtreamtv.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed class XtreamException(message: String) : Exception(message) {
    class NotAuthenticated : XtreamException("The server rejected that username and password.")
    class AccountInactive(status: String) : XtreamException("This account is ${status.lowercase()}.")
    class HttpStatus(code: Int) : XtreamException("The server returned HTTP $code.")
    class Transport(detail: String) : XtreamException("Could not reach the server: $detail")
    class UnexpectedResponse(action: String, detail: String) :
        XtreamException("The server sent an unexpected reply to $action: $detail")
}

/**
 * A client for the Xtream Codes `player_api.php` interface.
 *
 * The API is a single endpoint keyed by `action`, authenticated with the
 * username and password in the query string. Stream URLs are built locally
 * from the same credentials, so playback needs no extra round trip.
 */
class XtreamClient(
    val credentials: XtreamCredentials,
    private val http: OkHttpClient = defaultHttp,
) {
    companion object {
        const val USER_AGENT = "XtreamTV/1.0 (Android)"

        val defaultHttp: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .followRedirects(true)
                .followSslRedirects(true)
                .build()
        }
    }

    // MARK: Endpoints

    /** Logs in and returns the account, throwing when the login is refused. */
    suspend fun authenticate(): XtreamAccount {
        val body = fetch(null)
        val account = XtreamAccount.from(parseObject(body, "login"))
        if (!account.isAuthenticated) throw XtreamException.NotAuthenticated()
        val status = account.status.lowercase()
        if (status.isNotEmpty() && status != "active") throw XtreamException.AccountInactive(account.status)
        return account
    }

    suspend fun categories(section: Section): List<XtreamCategory> {
        val body = fetch(section.categoriesAction)
        if (isEmptyJson(body)) return emptyList()
        val a = parseArray(body, section.categoriesAction)
        return (0 until a.length()).mapNotNull { (a.opt(it) as? JSONObject)?.let(XtreamCategory::from) }
    }

    suspend fun streams(section: Section, categoryId: String? = null): List<XtreamStream> {
        val extra = categoryId?.let { mapOf("category_id" to it) } ?: emptyMap()
        val body = fetch(section.streamsAction, extra)
        if (isEmptyJson(body)) return emptyList()
        return withContext(Dispatchers.Default) {
            XtreamStream.list(parseArray(body, section.streamsAction), section)
        }
    }

    suspend fun movieDetails(id: Int): XtreamMovieDetails =
        XtreamMovieDetails.from(parseObject(fetch("get_vod_info", mapOf("vod_id" to "$id")), "get_vod_info"))

    suspend fun seriesDetails(id: Int): XtreamSeriesDetails =
        XtreamSeriesDetails.from(parseObject(fetch("get_series_info", mapOf("series_id" to "$id")), "get_series_info"))

    /** The next few programmes on a live channel. */
    suspend fun shortEpg(streamId: Int, limit: Int = 4): List<XtreamProgramme> {
        val body = fetch("get_short_epg", mapOf("stream_id" to "$streamId", "limit" to "$limit"))
        return XtreamProgramme.list(parseObject(body, "get_short_epg"))
    }

    // MARK: URLs

    fun apiUrl(action: String?, extra: Map<String, String> = emptyMap()): HttpUrl {
        val b = "${credentials.server}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", credentials.username)
            .addQueryParameter("password", credentials.password)
        if (action != null) b.addQueryParameter("action", action)
        for ((k, v) in extra) b.addQueryParameter(k, v)
        return b.build()
    }

    /**
     * Playback URL for a live channel or movie. For live streams `format` is
     * `ts` or `m3u8`; for movies it defaults to the stream's own container.
     */
    fun streamUrl(stream: XtreamStream, format: String? = null): String {
        val ext = when (stream.section) {
            Section.LIVE -> format ?: "m3u8"
            else -> format ?: stream.containerExtension ?: "mp4"
        }
        return mediaUrl(stream.section, stream.streamId.toString(), ext)
    }

    fun episodeUrl(episode: XtreamEpisode): String =
        mediaUrl(Section.SERIES, episode.id, episode.containerExtension ?: "mp4")

    private fun mediaUrl(section: Section, id: String, ext: String): String =
        "${credentials.server}/${section.path}/${enc(credentials.username)}/${enc(credentials.password)}/$id.$ext"

    private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    // MARK: Transport

    private suspend fun fetch(action: String?, extra: Map<String, String> = emptyMap()): String =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(apiUrl(action, extra))
                .header("User-Agent", USER_AGENT)
                .build()
            try {
                http.newCall(request).execute().use { response ->
                    // Some panels answer a bad login with 401/403 rather than auth: 0.
                    if (response.code == 401 || response.code == 403) throw XtreamException.NotAuthenticated()
                    if (!response.isSuccessful) throw XtreamException.HttpStatus(response.code)
                    response.body?.string() ?: ""
                }
            } catch (e: XtreamException) {
                throw e
            } catch (e: IOException) {
                throw XtreamException.Transport(e.message ?: e.javaClass.simpleName)
            }
        }

    private fun parseObject(body: String, action: String): JSONObject = try {
        JSONObject(body)
    } catch (e: JSONException) {
        throw XtreamException.UnexpectedResponse(action, describe(body, e))
    }

    private fun parseArray(body: String, action: String): JSONArray = try {
        JSONArray(body)
    } catch (e: JSONException) {
        // A few panels wrap catalogs in an object keyed by index.
        val obj = runCatching { JSONObject(body) }.getOrNull()
            ?: throw XtreamException.UnexpectedResponse(action, describe(body, e))
        JSONArray().apply { obj.keys().forEach { k -> obj.opt(k)?.let(::put) } }
    }

    private fun describe(body: String, e: Exception): String {
        val head = body.take(64).trim()
        return when {
            head.startsWith("<") -> "an HTML page instead of JSON"
            head.isEmpty() -> "an empty body"
            else -> e.message ?: "invalid JSON"
        }
    }
}
