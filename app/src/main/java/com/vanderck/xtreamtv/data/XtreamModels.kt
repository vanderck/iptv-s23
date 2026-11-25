package com.vanderck.xtreamtv.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Login details for an Xtream Codes server. */
data class XtreamCredentials(
    /** Server root without a trailing slash, e.g. `http://provider.example:8080`. */
    val server: String,
    val username: String,
    val password: String,
) {
    companion object {
        private val endpoints = listOf("/get.php", "/player_api.php", "/xmltv.php")

        /**
         * Accepts what users actually paste: a bare host, a host with port, a URL
         * with a trailing slash, or a full `get.php?username=…&password=…` link.
         */
        fun parse(rawServer: String, username: String = "", password: String = ""): XtreamCredentials? {
            var text = rawServer.trim()
            if (text.isEmpty()) return null
            if (!text.contains("://")) text = "http://$text"
            val uri = try { URI(text) } catch (_: Exception) { return null }
            if (uri.host.isNullOrEmpty()) return null

            var user = username.trim()
            var pass = password
            val query = uri.rawQuery.orEmpty().split('&').mapNotNull {
                val parts = it.split('=', limit = 2)
                if (parts.size == 2) parts[0] to java.net.URLDecoder.decode(parts[1], "UTF-8") else null
            }.toMap()
            if (user.isEmpty()) query["username"]?.let { user = it }
            if (pass.isEmpty()) query["password"]?.let { pass = it }

            var path = uri.rawPath.orEmpty()
            for (endpoint in endpoints) if (path.endsWith(endpoint)) path = path.dropLast(endpoint.length)
            path = path.trimEnd('/')

            if (user.isEmpty()) return null
            val port = if (uri.port != -1) ":${uri.port}" else ""
            return XtreamCredentials("${uri.scheme}://${uri.host}$port$path", user, pass)
        }
    }
}

data class XtreamAccount(
    val username: String,
    val status: String,
    val isAuthenticated: Boolean,
    val expiresAt: Long?,
    val isTrial: Boolean,
    val activeConnections: Int?,
    val maxConnections: Int?,
    val allowedOutputFormats: List<String>,
    val timezone: String?,
) {
    /** Whether the panel serves raw MPEG-TS for live channels. */
    val allowsTs: Boolean get() = allowedOutputFormats.isEmpty() || "ts" in allowedOutputFormats
    val allowsHls: Boolean get() = allowedOutputFormats.isEmpty() || "m3u8" in allowedOutputFormats

    companion object {
        fun from(json: JSONObject): XtreamAccount {
            val user = json.obj("user_info") ?: JSONObject()
            val server = json.obj("server_info")
            val formats = (user.opt("allowed_output_formats") as? JSONArray)?.let { a ->
                (0 until a.length()).mapNotNull { a.optString(it).takeIf(String::isNotBlank) }
            } ?: emptyList()
            return XtreamAccount(
                username = user.str("username") ?: "",
                status = user.str("status") ?: "",
                isAuthenticated = user.bool("auth") ?: false,
                expiresAt = user.dbl("exp_date")?.toLong(),
                isTrial = user.bool("is_trial") ?: false,
                activeConnections = user.int("active_cons"),
                maxConnections = user.int("max_connections"),
                allowedOutputFormats = formats,
                timezone = server?.str("timezone"),
            )
        }
    }
}

/** Which of the three Xtream catalogs a category or stream belongs to. */
enum class Section(val title: String, val categoriesAction: String, val streamsAction: String, val path: String) {
    LIVE("Live TV", "get_live_categories", "get_live_streams", "live"),
    MOVIES("Movies", "get_vod_categories", "get_vod_streams", "movie"),
    SERIES("Series", "get_series_categories", "get_series", "series");
}

data class XtreamCategory(val id: String, val name: String) {
    companion object {
        fun from(json: JSONObject) = XtreamCategory(
            id = json.str("category_id") ?: "",
            name = json.str("category_name") ?: "Untitled",
        )
    }
}

/** A live channel, a movie, or a series, as listed in a catalog. */
data class XtreamStream(
    val section: Section,
    /** `stream_id` for live and movies, `series_id` for series. */
    val streamId: Int,
    val name: String,
    val number: Int? = null,
    val iconUrl: String? = null,
    val backdropUrl: String? = null,
    val categoryId: String? = null,
    val categoryIds: List<String> = emptyList(),
    val epgChannelId: String? = null,
    val containerExtension: String? = null,
    val rating: Double? = null,
    val plot: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    /** Seconds since 1970. */
    val added: Long? = null,
    val hasArchive: Boolean = false,
    val archiveDays: Int? = null,
) {
    val id: String get() = "${section.name.lowercase()}-$streamId"

    fun belongs(category: String) = categoryId == category || category in categoryIds

    companion object {
        /** Decodes one catalog entry; null for entries with no usable id. */
        fun from(c: JSONObject, section: Section): XtreamStream? {
            val id = c.int(if (section == Section.SERIES) "series_id" else "stream_id") ?: return null
            val name = c.str("name") ?: c.str("title") ?: "Untitled"
            val icon = c.str(if (section == Section.SERIES) "cover" else "stream_icon")
                ?: c.str("stream_icon") ?: c.str("cover")
            var rating = c.dbl("rating")
            if (rating == null || rating == 0.0) {
                c.dbl("rating_5based")?.takeIf { it > 0 }?.let { rating = it * 2 }
            }
            return XtreamStream(
                section = section,
                streamId = id,
                name = name.trim(),
                number = c.int("num"),
                iconUrl = lenientUrl(icon),
                backdropUrl = c.strList("backdrop_path").firstOrNull()?.let(::lenientUrl),
                categoryId = c.str("category_id"),
                categoryIds = c.strList("category_ids"),
                epgChannelId = c.str("epg_channel_id"),
                containerExtension = c.str("container_extension"),
                rating = rating?.takeIf { it > 0 },
                plot = c.str("plot"),
                genre = c.str("genre"),
                releaseDate = c.str("releaseDate") ?: c.str("release_date"),
                added = (c.dbl("added") ?: c.dbl("last_modified"))?.toLong(),
                hasArchive = c.bool("tv_archive") ?: false,
                archiveDays = c.int("tv_archive_duration"),
            )
        }

        /** Decodes a catalog array, skipping entries that do not decode. */
        fun list(array: JSONArray, section: Section): List<XtreamStream> {
            val out = ArrayList<XtreamStream>(array.length())
            for (i in 0 until array.length()) {
                val o = array.opt(i) as? JSONObject ?: continue
                runCatching { from(o, section) }.getOrNull()?.let(out::add)
            }
            return out
        }
    }
}

/** Extra metadata from `get_vod_info` / `get_series_info`. */
data class XtreamMediaInfo(
    val plot: String? = null,
    val cast: String? = null,
    val director: String? = null,
    val genre: String? = null,
    val releaseDate: String? = null,
    val duration: String? = null,
    val rating: Double? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
) {
    companion object {
        /** Panels with no metadata send `"info": []` instead of an object. */
        fun from(c: JSONObject?): XtreamMediaInfo {
            c ?: return XtreamMediaInfo()
            val poster = c.str("movie_image") ?: c.str("cover_big") ?: c.str("cover")
            return XtreamMediaInfo(
                plot = c.str("plot") ?: c.str("description"),
                cast = c.str("cast") ?: c.str("actors"),
                director = c.str("director"),
                genre = c.str("genre"),
                releaseDate = c.str("releasedate") ?: c.str("release_date"),
                duration = c.str("duration"),
                rating = c.dbl("rating")?.takeIf { it > 0 },
                posterUrl = lenientUrl(poster),
                backdropUrl = c.strList("backdrop_path").firstOrNull()?.let(::lenientUrl),
            )
        }
    }
}

data class XtreamMovieDetails(val info: XtreamMediaInfo, val containerExtension: String?) {
    companion object {
        fun from(c: JSONObject) = XtreamMovieDetails(
            info = XtreamMediaInfo.from(c.obj("info")),
            containerExtension = c.obj("movie_data")?.str("container_extension"),
        )
    }
}

data class XtreamEpisode(
    /** The episode's stream id, used to build its playback URL. */
    val id: String,
    val season: Int,
    val episodeNumber: Int,
    val title: String,
    val containerExtension: String?,
    val plot: String?,
    val durationSeconds: Int?,
    val imageUrl: String?,
) {
    val label: String get() = "S%02dE%02d".format(season, episodeNumber)

    companion object {
        fun from(c: JSONObject): XtreamEpisode {
            val info = c.obj("info")
            val number = c.int("episode_num") ?: 0
            return XtreamEpisode(
                id = c.str("id") ?: "",
                season = c.int("season") ?: 0,
                episodeNumber = number,
                title = c.str("title") ?: "Episode $number",
                containerExtension = c.str("container_extension"),
                plot = info?.str("plot"),
                durationSeconds = info?.int("duration_secs"),
                imageUrl = lenientUrl(info?.str("movie_image")),
            )
        }
    }
}

data class XtreamSeriesDetails(
    val info: XtreamMediaInfo,
    /** Episodes keyed by season number, each season sorted by episode. */
    val episodesBySeason: Map<Int, List<XtreamEpisode>>,
) {
    val seasons: List<Int> get() = episodesBySeason.keys.sorted()

    companion object {
        fun from(c: JSONObject): XtreamSeriesDetails {
            val grouped = mutableMapOf<Int, MutableList<XtreamEpisode>>()
            fun episodes(a: JSONArray) = (0 until a.length()).mapNotNull { i ->
                (a.opt(i) as? JSONObject)?.let { runCatching { XtreamEpisode.from(it) }.getOrNull() }
            }
            // `episodes` is normally `{"1": [...], "2": [...]}`, but some panels
            // send a bare array of seasons, and empty series send `[]`.
            when (val e = c.opt("episodes")) {
                is JSONObject -> for (key in e.keys()) {
                    val list = (e.opt(key) as? JSONArray)?.let(::episodes) ?: continue
                    val season = key.toIntOrNull() ?: list.firstOrNull()?.season ?: 0
                    grouped.getOrPut(season) { mutableListOf() }.addAll(list)
                }
                is JSONArray -> for (i in 0 until e.length()) {
                    val list = (e.opt(i) as? JSONArray)?.let(::episodes) ?: continue
                    for (ep in list) grouped.getOrPut(ep.season) { mutableListOf() }.add(ep)
                }
            }
            return XtreamSeriesDetails(
                info = XtreamMediaInfo.from(c.obj("info")),
                episodesBySeason = grouped.mapValues { (_, v) -> v.sortedBy { it.episodeNumber } },
            )
        }
    }
}

/** One programme from `get_short_epg`. Times are epoch milliseconds. */
data class XtreamProgramme(
    val id: String,
    val title: String,
    val description: String,
    val start: Long,
    val end: Long,
) {
    /** Fraction of the programme that has aired at `now`, clamped to 0…1. */
    fun progress(now: Long = System.currentTimeMillis()): Float {
        val length = end - start
        if (length <= 0) return 0f
        return ((now - start).toFloat() / length).coerceIn(0f, 1f)
    }

    companion object {
        fun from(c: JSONObject): XtreamProgramme? {
            val start = c.dbl("start_timestamp")?.let { (it * 1000).toLong() } ?: parseDate(c.str("start"))
            val end = c.dbl("stop_timestamp")?.let { (it * 1000).toLong() } ?: parseDate(c.str("end"))
            if (start == null || end == null) return null
            return XtreamProgramme(
                id = c.str("id") ?: "$start",
                title = decodeBase64(c.str("title")) ?: "",
                description = decodeBase64(c.str("description")) ?: "",
                start = start,
                end = end,
            )
        }

        fun list(c: JSONObject): List<XtreamProgramme> {
            val a = c.opt("epg_listings") as? JSONArray ?: return emptyList()
            return (0 until a.length())
                .mapNotNull { (a.opt(it) as? JSONObject)?.let(::from) }
                .sortedBy { it.start }
        }

        /** Titles and descriptions are base64 in nearly every panel. */
        fun decodeBase64(text: String?): String? {
            text ?: return null
            return try {
                val bytes = java.util.Base64.getMimeDecoder().decode(text)
                val decoded = String(bytes, Charsets.UTF_8)
                if (decoded.contains('�')) text else decoded
            } catch (_: Throwable) {
                text
            }
        }

        private fun parseDate(text: String?): Long? {
            text ?: return null
            return runCatching {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
                    .apply { timeZone = TimeZone.getTimeZone("UTC") }
                    .parse(text)?.time
            }.getOrNull()
        }
    }
}
