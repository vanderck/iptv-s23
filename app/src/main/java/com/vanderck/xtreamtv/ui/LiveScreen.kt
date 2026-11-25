package com.vanderck.xtreamtv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vanderck.xtreamtv.data.Section
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamStream

private const val ALL = "__all"
private const val FAVORITES = "__favorites"
private const val RECENT = "__recent"

@Composable
fun LiveScreen(model: TvModel, actions: Actions) {
    val catalogs by model.catalogs.collectAsStateWithLifecycle()
    val favorites by model.favorites.collectAsStateWithLifecycle()
    val recents by model.recents.collectAsStateWithLifecycle()
    val catalog = catalogs.getValue(Section.LIVE)

    var group by rememberSaveable { mutableStateOf(if (favorites.isNotEmpty()) FAVORITES else ALL) }
    var query by rememberSaveable { mutableStateOf("") }

    if (catalog.streams.isEmpty()) {
        CenteredMessage(
            title = when {
                catalog.loading -> "Loading channels…"
                catalog.error != null -> "Couldn't load channels"
                else -> "No live channels from this provider"
            },
            detail = catalog.error,
            loading = catalog.loading,
            action = if (!catalog.loading) ({ Button(onClick = { model.loadSection(Section.LIVE) }) { Text("Try Again") } }) else null,
        )
        return
    }

    val channels = remember(catalog, group, query, favorites, recents) {
        val base = when (group) {
            ALL -> catalog.streams
            FAVORITES -> catalog.streams.filter { it.id in favorites }
            RECENT -> recents.mapNotNull { catalog.byId[it] }
            else -> catalog.byCategory[group].orEmpty()
        }
        val q = TvModel.fold(query.trim())
        if (q.isEmpty()) base else base.filter { TvModel.fold(it.name).contains(q) }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(group) { listState.scrollToItem(0) }

    val lastChannel = remember(catalog) { model.store.lastChannel?.let { catalog.byId[it] } }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(start = 20.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Live TV", fontSize = 34.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (lastChannel != null) {
                Button(
                    onClick = { actions.open(lastChannel) },
                    colors = ButtonDefaults.buttonColors(containerColor = TvStyle.platterHigh, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(lastChannel.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp,
                        modifier = Modifier.padding(end = 2.dp).width(110.dp))
                }
            }
        }

        SearchField(query, { query = it }, "Search channels", Modifier.padding(horizontal = 16.dp, vertical = 10.dp))

        val chips = buildList {
            add(ALL to "All (${catalog.streams.size})")
            if (favorites.any { it.startsWith("live-") }) add(FAVORITES to "★ Favorites")
            if (recents.any { it.startsWith("live-") }) add(RECENT to "Recent")
            for (c in catalog.categories) {
                val n = catalog.byCategory[c.id]?.size ?: 0
                if (n > 0) add(c.id to c.name)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(chips, key = { it.first }) { (id, label) ->
                Chip(label, selected = id == group) { group = id }
            }
        }

        if (channels.isEmpty()) {
            CenteredMessage(if (query.isNotBlank()) "No matching channels" else "No channels in this group")
            return@Column
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = 8.dp, bottom = TabBarClearance),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(channels, key = { it.id }) { stream ->
                ChannelRow(
                    stream,
                    isFavorite = stream.id in favorites,
                    onClick = { actions.open(stream) },
                    onLongClick = { actions.toggleFavorite(stream) },
                    onToggleFavorite = { actions.toggleFavorite(stream) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelRow(
    stream: XtreamStream,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (highlighted) Color.White.copy(0.12f) else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stream.number?.toString() ?: "",
            fontSize = 13.sp, color = TvStyle.tertiaryText,
            modifier = Modifier.width(38.dp),
        )
        Box(
            Modifier
                .size(width = 72.dp, height = 44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(TvStyle.platter),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(stream.iconUrl, stream.name, Modifier.fillMaxSize().padding(5.dp),
                contentScale = ContentScale.Fit, placeholderIcon = true)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(stream.name, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (stream.hasArchive) {
                Text("Catch-up ${stream.archiveDays?.let { "· $it days" } ?: ""}", fontSize = 12.sp, color = TvStyle.tertiaryText)
            }
        }
        IconButton(onClick = onToggleFavorite) { FavoriteIcon(isFavorite) }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) Color.White else TvStyle.platter)
            .combinedClickableCompat(onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            label, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            color = if (selected) Color.Black else Color.White, maxLines = 1,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit) = combinedClickable(onClick = onClick)

@Composable
fun SearchField(value: String, onChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Default.Search, null) },
        trailingIcon = {
            if (value.isNotEmpty()) IconButton(onClick = { onChange("") }) { Icon(Icons.Default.Close, "Clear") }
        },
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = TvStyle.platter,
            unfocusedContainerColor = TvStyle.platter,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            cursorColor = Color.White,
        ),
        modifier = modifier.fillMaxWidth().height(54.dp),
    )
}
