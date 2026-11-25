package com.vanderck.xtreamtv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanderck.xtreamtv.data.Section
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamStream
import kotlinx.coroutines.delay

/** Bottom space so the floating tab bar never covers the last row. */
val TabBarClearance = 110.dp

@Composable
fun HomeScreen(model: TvModel, actions: Actions) {
    val shelves by model.shelves.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val catalogs by model.catalogs.collectAsStateWithLifecycle()

    if (shelves.home.isEmpty()) {
        val loading = catalogs.values.any { it.loading }
        val error = catalogs.values.firstNotNullOfOrNull { it.error }
        CenteredMessage(
            title = if (loading) "Loading your library…" else "Nothing here yet",
            detail = error,
            loading = loading,
            action = if (!loading) ({
                Button(onClick = model::reload) { Text("Reload") }
            }) else null,
        )
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = TabBarClearance)) {
        if (shelves.featured.isNotEmpty()) {
            item(key = "hero") { HeroCarousel(shelves.featured, actions) }
        } else {
            item { Spacer(Modifier.windowInsetsTopHeight(WindowInsets.statusBars)) }
        }
        items(shelves.home, key = { it.id }) { shelf ->
            ShelfRow(
                shelf, favorites,
                onOpen = actions::open,
                onToggleFavorite = actions::toggleFavorite,
                progressFor = actions::progress,
            )
        }
    }
}

/** Full-bleed carousel of the newest movies and series. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarousel(items: List<XtreamStream>, actions: Actions) {
    val pager = rememberPagerState { items.size }
    LaunchedEffect(pager, items.size) {
        while (true) {
            delay(7000)
            if (!pager.isScrollInProgress && items.isNotEmpty()) {
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(4f / 5f)) {
        HorizontalPager(pager, Modifier.fillMaxSize()) { page ->
            val stream = items[page]
            Box(Modifier.fillMaxSize().clickable { actions.open(stream) }) {
                Artwork(stream.backdropUrl ?: stream.iconUrl, stream.name, Modifier.fillMaxSize())
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(0.35f),
                            0.25f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to TvStyle.background,
                        ),
                    ),
                )
                Column(Modifier.align(Alignment.BottomStart).padding(20.dp).padding(bottom = 18.dp)) {
                    Text(
                        if (stream.section == Section.SERIES) "NEW SERIES" else "NEW MOVIE",
                        fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TvStyle.secondaryText,
                    )
                    Text(
                        stream.name, fontSize = 32.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                    listOfNotNull(stream.genre, stream.releaseDate?.take(4), stream.rating?.let { "★ %.1f".format(it) })
                        .takeIf { it.isNotEmpty() }
                        ?.let { Text(it.joinToString(" · "), fontSize = 14.sp, color = TvStyle.secondaryText) }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { actions.open(stream) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    ) { Text("View", fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(items.size) { i ->
                Box(
                    Modifier.size(7.dp).background(
                        if (i == pager.currentPage) Color.White else Color.White.copy(0.3f), CircleShape,
                    ),
                )
            }
        }
    }
}

/** Movies or Series: one shelf per category. */
@Composable
fun BrowseScreen(model: TvModel, actions: Actions, section: Section) {
    val shelves by model.shelves.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val catalogs by model.catalogs.collectAsStateWithLifecycle()
    val catalog = catalogs.getValue(section)
    val list = shelves.sections[section].orEmpty()

    if (list.isEmpty()) {
        CenteredMessage(
            title = when {
                catalog.loading -> "Loading ${section.title}…"
                catalog.error != null -> "Couldn't load ${section.title}"
                else -> "No ${section.title.lowercase()} from this provider"
            },
            detail = catalog.error,
            loading = catalog.loading,
            action = if (!catalog.loading) ({ Button(onClick = { model.loadSection(section) }) { Text("Try Again") } }) else null,
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = TabBarClearance),
    ) {
        item {
            Text(
                section.title, fontSize = 34.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.statusBarsPadding().padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
            )
        }
        items(list, key = { it.id }) { shelf ->
            ShelfRow(
                shelf, favorites,
                onOpen = actions::open,
                onToggleFavorite = actions::toggleFavorite,
                onSeeAll = { actions.seeAll(section, shelf.id) },
                progressFor = actions::progress,
            )
        }
    }
}

/** Every item in one shelf, as a grid. */
@Composable
fun GridScreen(model: TvModel, actions: Actions, section: Section, shelfId: String) {
    val shelves by model.shelves.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val shelf = shelves.sections[section]?.firstOrNull { it.id == shelfId }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
            IconButton(onClick = { actions.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(
                shelf?.title ?: "", fontSize = 22.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        if (shelf == null) return@Column
        val live = section == Section.LIVE
        LazyVerticalGrid(
            columns = GridCells.Adaptive(if (live) 150.dp else 105.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(shelf.items, key = { it.id }) { stream ->
                if (live) {
                    ChannelTile(stream, stream.id in favorites, { actions.open(stream) }, { actions.toggleFavorite(stream) },
                        width = Dp.Unspecified)
                } else {
                    PosterCard(stream, stream.id in favorites, { actions.open(stream) }, { actions.toggleFavorite(stream) },
                        width = Dp.Unspecified, progress = actions.progress(stream))
                }
            }
        }
    }
}
