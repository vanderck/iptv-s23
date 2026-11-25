package com.vanderck.xtreamtv.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.vanderck.xtreamtv.data.Section
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamEpisode
import com.vanderck.xtreamtv.data.XtreamMediaInfo
import com.vanderck.xtreamtv.data.XtreamSeriesDetails
import com.vanderck.xtreamtv.data.XtreamStream

@Composable
fun DetailScreen(model: TvModel, actions: Actions, id: String) {
    val stream = remember(id) { model.find(id) }
    if (stream == null) {
        CenteredMessage("This title is no longer available")
        return
    }
    val favorites by model.favorites.collectAsStateWithLifecycle()

    var info by remember { mutableStateOf<XtreamMediaInfo?>(null) }
    var containerExt by remember { mutableStateOf<String?>(null) }
    var series by remember { mutableStateOf<XtreamSeriesDetails?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var season by rememberSaveable { mutableIntStateOf(-1) }

    // Re-read resume points whenever the screen comes back from the player.
    var resumeTick by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { resumeTick++ } }

    LaunchedEffect(stream.id) {
        loading = true
        try {
            if (stream.section == Section.SERIES) {
                val details = model.seriesDetails(stream)
                series = details
                info = details.info
                if (season == -1) season = details.seasons.firstOrNull() ?: 0
            } else {
                val details = model.movieDetails(stream)
                info = details?.info
                containerExt = details?.containerExtension
            }
        } catch (e: Exception) {
            error = e.message
        }
        loading = false
    }

    val backdrop = info?.backdropUrl ?: stream.backdropUrl
    val poster = info?.posterUrl ?: stream.iconUrl

    Box(Modifier.fillMaxSize().background(TvStyle.background)) {
        // Blurred backdrop behind everything.
        Artwork(
            backdrop ?: poster, stream.name,
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.75f)
                .then(if (Build.VERSION.SDK_INT >= 31 && backdrop == null) Modifier.blur(40.dp) else Modifier),
        )
        Box(
            Modifier.fillMaxWidth().aspectRatio(0.75f).background(
                Brush.verticalGradient(0f to Color.Black.copy(0.3f), 0.35f to Color.Transparent, 1f to TvStyle.background),
            ),
        )

        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item { Spacer(Modifier.statusBarsPadding().height(200.dp)) }
            item {
                Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.Bottom) {
                    Artwork(
                        poster, stream.name,
                        Modifier.width(110.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(TvStyle.cornerRadius)),
                    )
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stream.name, fontSize = 26.sp, fontWeight = FontWeight.Bold, lineHeight = 30.sp)
                        val meta = listOfNotNull(
                            (info?.releaseDate ?: stream.releaseDate)?.take(4),
                            info?.genre ?: stream.genre,
                            info?.duration,
                            (info?.rating ?: stream.rating)?.let { "★ %.1f".format(it) },
                        )
                        if (meta.isNotEmpty()) {
                            Text(meta.joinToString(" · "), fontSize = 13.sp, color = TvStyle.secondaryText,
                                modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
            item {
                val isFavorite = stream.id in favorites
                Row(Modifier.padding(20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (stream.section == Section.MOVIES) {
                        val resume = remember(resumeTick) { model.store.position(stream.id) }
                        Button(
                            onClick = { actions.play(model.playable(stream, containerExt)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(if (resume > 0) "Resume · ${formatTime(resume)}" else "Play", fontWeight = FontWeight.SemiBold)
                        }
                    } else {
                        val first = series?.let { s -> s.episodesBySeason[s.seasons.firstOrNull()]?.firstOrNull() }
                        Button(
                            onClick = { first?.let { actions.play(model.playable(it, stream)) } },
                            enabled = first != null,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            modifier = Modifier.weight(1f).height(50.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(6.dp))
                            Text(first?.let { "Play ${it.label}" } ?: "Play", fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Button(
                        onClick = { actions.toggleFavorite(stream) },
                        colors = ButtonDefaults.buttonColors(containerColor = TvStyle.platterHigh, contentColor = Color.White),
                        modifier = Modifier.height(50.dp),
                    ) { FavoriteIcon(isFavorite) }
                }
            }
            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    (info?.plot ?: stream.plot)?.let {
                        Text(it, fontSize = 15.sp, color = Color.White.copy(0.9f), lineHeight = 21.sp)
                        Spacer(Modifier.height(10.dp))
                    }
                    info?.cast?.let { Text("Cast: $it", fontSize = 13.sp, color = TvStyle.secondaryText, maxLines = 3, overflow = TextOverflow.Ellipsis) }
                    info?.director?.let { Text("Director: $it", fontSize = 13.sp, color = TvStyle.secondaryText) }
                    if (loading) {
                        Spacer(Modifier.height(20.dp))
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
                    }
                    error?.let { Text(it, color = TvStyle.live, fontSize = 14.sp, modifier = Modifier.padding(top = 12.dp)) }
                }
            }

            val details = series
            if (details != null && details.seasons.isNotEmpty()) {
                item {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(details.seasons) { s -> Chip("Season $s", selected = s == season) { season = s } }
                    }
                }
                items(details.episodesBySeason[season].orEmpty(), key = { it.id }) { episode ->
                    val key = "episode-${episode.id}"
                    val progress = remember(resumeTick, key) {
                        val d = model.store.duration(key)
                        if (d > 0) model.store.position(key).toFloat() / d else null
                    }
                    EpisodeRow(episode, stream, progress) { actions.play(model.playable(episode, stream)) }
                }
            }
        }

        IconButton(
            onClick = { actions.back() },
            modifier = Modifier
                .statusBarsPadding()
                .padding(8.dp)
                .background(Color.Black.copy(0.4f), CircleShape),
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }
}

@Composable
private fun EpisodeRow(episode: XtreamEpisode, series: XtreamStream, progress: Float?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(140.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp))) {
            Artwork(episode.imageUrl ?: series.backdropUrl ?: series.iconUrl, episode.label, Modifier.fillMaxSize())
            Icon(
                Icons.Default.PlayArrow, null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(32.dp).background(Color.Black.copy(0.4f), CircleShape).padding(4.dp),
            )
            if (progress != null && progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter), height = 3.dp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(episode.label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TvStyle.secondaryText)
            Text(episode.title, fontSize = 15.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            episode.durationSeconds?.takeIf { it > 0 }?.let {
                Text("${it / 60} min", fontSize = 12.sp, color = TvStyle.tertiaryText)
            }
            episode.plot?.let {
                Text(it, fontSize = 12.sp, color = TvStyle.secondaryText, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
