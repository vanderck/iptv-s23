package com.vanderck.xtreamtv.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.Normalizer

/** One horizontal row on a browse page. */
data class Shelf(val id: String, val title: String, val items: List<XtreamStream>)

data class Catalog(
    val categories: List<XtreamCategory> = emptyList(),
    val streams: List<XtreamStream> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
) {
    /** Streams by category id, built once per load. */
    val byCategory: Map<String, List<XtreamStream>> by lazy {
        val map = HashMap<String, MutableList<XtreamStream>>()
        for (s in streams) {
            s.categoryId?.let { map.getOrPut(it) { mutableListOf() }.add(s) }
            for (extra in s.categoryIds) if (extra != s.categoryId) map.getOrPut(extra) { mutableListOf() }.add(s)
        }
        map
    }
    val byId: Map<String, XtreamStream> by lazy { streams.associateBy { it.id } }
    /** Folded names, parallel to `streams`, for fast search. */
    val searchKeys: List<String> by lazy { streams.map { TvModel.fold(it.name) } }
}

data class Shelves(
    val home: List<Shelf> = emptyList(),
    val featured: List<XtreamStream> = emptyList(),
    val sections: Map<Section, List<Shelf>> = emptyMap(),
)

sealed interface Phase {
    data object SignedOut : Phase
    data object SigningIn : Phase
    data object Ready : Phase
    data class Failed(val message: String) : Phase
}

/** Something the player can open. */
data class Playable(
    val title: String,
    val subtitle: String?,
    val url: String,
    /** Tried if `url` fails, e.g. HLS when TS was refused. */
    val fallbackUrl: String?,
    val isLive: Boolean,
    /** Key for resume points; null for live. */
    val resumeKey: String?,
    val artworkUrl: String?,
    /** The channel, for EPG and zapping. */
    val stream: XtreamStream? = null,
)

/**
 * Everything the app knows: who is signed in, what each catalog holds, and
 * favorites/recents. One instance lives for the process so the player
 * activity and the browse UI share it.
 */
class TvModel(context: Context) {
    val store = Store(context.applicationContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _phase = MutableStateFlow<Phase>(Phase.SignedOut)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _account = MutableStateFlow<XtreamAccount?>(null)
    val account: StateFlow<XtreamAccount?> = _account.asStateFlow()

    private val _catalogs = MutableStateFlow(Section.entries.associateWith { Catalog() })
    val catalogs: StateFlow<Map<Section, Catalog>> = _catalogs.asStateFlow()

    private val _favorites = MutableStateFlow(store.favorites)
    val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

    private val _recents = MutableStateFlow(store.recents)
    val recents: StateFlow<List<String>> = _recents.asStateFlow()

    private val _shelves = MutableStateFlow(Shelves())
    val shelves: StateFlow<Shelves> = _shelves.asStateFlow()

    private val _liveFormat = MutableStateFlow(store.liveFormat)
    val liveFormat: StateFlow<LiveFormat> = _liveFormat.asStateFlow()

    private val _autoPip = MutableStateFlow(store.autoPip)
    val autoPip: StateFlow<Boolean> = _autoPip.asStateFlow()

    var client: XtreamClient? = null
        private set

    // MARK: Account

    fun restoreSession() {
        if (_phase.value != Phase.SignedOut) return
        store.credentials?.let(::signIn)
    }

    fun signIn(credentials: XtreamCredentials) {
        _phase.value = Phase.SigningIn
        val client = XtreamClient(credentials)
        scope.launch {
            try {
                val account = client.authenticate()
                this@TvModel.client = client
                _account.value = account
                store.credentials = credentials
                _phase.value = Phase.Ready
                reload()
            } catch (e: Exception) {
                _phase.value = Phase.Failed(e.message ?: "Sign-in failed.")
            }
        }
    }

    fun signOut() {
        store.credentials = null
        client = null
        _account.value = null
        _catalogs.value = Section.entries.associateWith { Catalog() }
        _shelves.value = Shelves()
        _phase.value = Phase.SignedOut
    }

    // MARK: Catalog

    private val loadJobs = mutableMapOf<Section, Job>()

    fun reload() = Section.entries.forEach(::loadSection)

    fun loadSection(section: Section) {
        val client = client ?: return
        if (loadJobs[section]?.isActive == true) return
        _catalogs.update { it + (section to it.getValue(section).copy(loading = true, error = null)) }
        loadJobs[section] = scope.launch {
            try {
                val categories = async { client.categories(section) }
                val streams = async { client.streams(section) }
                val catalog = Catalog(categories.await(), streams.await())
                _catalogs.update { it + (section to catalog) }
                rebuildShelves()
            } catch (e: Exception) {
                _catalogs.update { it + (section to it.getValue(section).copy(loading = false, error = e.message)) }
            }
        }
    }

    private var shelfJob: Job? = null

    /** Shelves come from catalogs of tens of thousands of entries; build them off the main thread. */
    private fun rebuildShelves() {
        shelfJob?.cancel()
        val catalogs = _catalogs.value
        val favorites = _favorites.value
        val recents = _recents.value
        shelfJob = scope.launch {
            _shelves.value = withContext(Dispatchers.Default) { ShelfBuilder.build(catalogs, favorites, recents) }
        }
    }

    fun find(id: String): XtreamStream? {
        val section = Section.entries.firstOrNull { id.startsWith(it.name.lowercase() + "-") } ?: return null
        return _catalogs.value[section]?.byId?.get(id)
    }

    fun search(query: String, section: Section, limit: Int = 60): List<XtreamStream> {
        val q = fold(query.trim())
        if (q.length < 2) return emptyList()
        val catalog = _catalogs.value[section] ?: return emptyList()
        val keys = catalog.searchKeys
        val out = ArrayList<XtreamStream>()
        for (i in keys.indices) {
            if (keys[i].contains(q)) out.add(catalog.streams[i])
            if (out.size >= limit) break
        }
        return out
    }

    // MARK: Favorites & recents

    fun isFavorite(stream: XtreamStream) = stream.id in _favorites.value

    fun toggleFavorite(stream: XtreamStream) {
        val next = _favorites.value.toMutableSet().apply { if (!add(stream.id)) remove(stream.id) }
        _favorites.value = next
        store.favorites = next
        rebuildShelves()
    }

    private fun remember(stream: XtreamStream) {
        val next = (listOf(stream.id) + _recents.value.filter { it != stream.id }).take(30)
        _recents.value = next
        store.recents = next
        if (stream.section == Section.LIVE) store.lastChannel = stream.id
        rebuildShelves()
    }

    fun setLiveFormat(format: LiveFormat) {
        _liveFormat.value = format
        store.liveFormat = format
    }

    fun setAutoPip(enabled: Boolean) {
        _autoPip.value = enabled
        store.autoPip = enabled
    }

    // MARK: Details

    suspend fun movieDetails(stream: XtreamStream): XtreamMovieDetails? =
        runCatching { client?.movieDetails(stream.streamId) }.getOrNull()

    suspend fun seriesDetails(stream: XtreamStream): XtreamSeriesDetails =
        (client ?: throw XtreamException.NotAuthenticated()).seriesDetails(stream.streamId)

    suspend fun shortEpg(stream: XtreamStream): List<XtreamProgramme> =
        runCatching { client?.shortEpg(stream.streamId) }.getOrNull().orEmpty()
            .filter { it.end > System.currentTimeMillis() }

    // MARK: Playback

    fun playable(stream: XtreamStream, containerExtension: String? = null): Playable? {
        val client = client ?: return null
        remember(stream)
        if (stream.section == Section.LIVE) {
            val account = _account.value
            // ExoPlayer plays raw MPEG-TS natively, and it is usually the more
            // stable of the two; fall back to the panel's HLS remux.
            val (primary, secondary) = when (_liveFormat.value) {
                LiveFormat.TS -> "ts" to null
                LiveFormat.HLS -> "m3u8" to null
                LiveFormat.AUTO -> when {
                    account == null || (account.allowsTs && account.allowsHls) -> "ts" to "m3u8"
                    account.allowsTs -> "ts" to null
                    else -> "m3u8" to null
                }
            }
            return Playable(
                title = stream.name,
                subtitle = stream.number?.let { "Channel $it" },
                url = client.streamUrl(stream, primary),
                fallbackUrl = secondary?.let { client.streamUrl(stream, it) },
                isLive = true,
                resumeKey = null,
                artworkUrl = stream.iconUrl,
                stream = stream,
            )
        }
        return Playable(
            title = stream.name,
            subtitle = stream.genre,
            url = client.streamUrl(stream, containerExtension ?: stream.containerExtension),
            fallbackUrl = null,
            isLive = false,
            resumeKey = stream.id,
            artworkUrl = stream.backdropUrl ?: stream.iconUrl,
            stream = stream,
        )
    }

    fun playable(episode: XtreamEpisode, series: XtreamStream): Playable? {
        val client = client ?: return null
        remember(series)
        return Playable(
            title = series.name,
            subtitle = "${episode.label} · ${episode.title}",
            url = client.episodeUrl(episode),
            fallbackUrl = null,
            isLive = false,
            resumeKey = "episode-${episode.id}",
            artworkUrl = episode.imageUrl ?: series.backdropUrl ?: series.iconUrl,
        )
    }

    /** Channel up/down while watching live TV, staying inside the category. */
    fun zap(current: XtreamStream, delta: Int): Playable? {
        val catalog = _catalogs.value[Section.LIVE] ?: return null
        val pool = current.categoryId?.let { catalog.byCategory[it] } ?: catalog.streams
        val index = pool.indexOfFirst { it.id == current.id }
        if (index < 0 || pool.size < 2) return null
        return playable(pool[(index + delta + pool.size) % pool.size])
    }

    /** The channels in the same category, for the in-player channel list. */
    fun channelsLike(current: XtreamStream): List<XtreamStream> {
        val catalog = _catalogs.value[Section.LIVE] ?: return emptyList()
        return current.categoryId?.let { catalog.byCategory[it] } ?: catalog.streams
    }

    companion object {
        @Volatile private var instance: TvModel? = null

        fun get(context: Context): TvModel =
            instance ?: synchronized(this) { instance ?: TvModel(context).also { instance = it } }

        /** Case- and accent-insensitive comparison key. */
        private val marks = Regex("\\p{Mn}+")
        fun fold(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase()
    }
}
