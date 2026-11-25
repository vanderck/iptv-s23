package com.vanderck.xtreamtv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import com.vanderck.xtreamtv.data.Section
import com.vanderck.xtreamtv.data.Shelf
import com.vanderck.xtreamtv.data.XtreamStream

/** Artwork with a lettered placeholder while loading or when missing. */
@Composable
fun Artwork(
    url: String?,
    label: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderIcon: Boolean = false,
) {
    val placeholder: @Composable () -> Unit = {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.14f), Color.White.copy(0.04f)))),
            contentAlignment = Alignment.Center,
        ) {
            if (placeholderIcon) {
                Icon(Icons.Default.Tv, null, tint = TvStyle.secondaryText, modifier = Modifier.size(28.dp))
            } else {
                Text(
                    label.take(28),
                    color = TvStyle.secondaryText,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
    }
    if (url == null) {
        Box(modifier) { placeholder() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = label,
            contentScale = contentScale,
            modifier = modifier,
            loading = { placeholder() },
            error = { placeholder() },
        )
    }
}

/** A movie or series poster (2:3) with title underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PosterCard(
    stream: XtreamStream,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    width: Dp = 120.dp,
    progress: Float? = null,
) {
    Column(if (width.isSpecified) Modifier.width(width) else Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(TvStyle.cornerRadius))
                .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(TvStyle.cornerRadius))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        ) {
            Artwork(stream.iconUrl, stream.name, Modifier.fillMaxSize())
            if (isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
            if (progress != null && progress > 0f) ProgressBar(progress, Modifier.align(Alignment.BottomCenter))
        }
        Text(
            stream.name,
            fontSize = 13.sp,
            color = TvStyle.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

/** A 16:9 tile with a channel logo, for live TV. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChannelTile(
    stream: XtreamStream,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    width: Dp = 168.dp,
) {
    Column(if (width.isSpecified) Modifier.width(width) else Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(TvStyle.cornerRadius))
                .background(Brush.verticalGradient(listOf(Color.White.copy(0.12f), Color.White.copy(0.04f))))
                .border(1.dp, Color.White.copy(0.06f), RoundedCornerShape(TvStyle.cornerRadius))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                stream.iconUrl,
                stream.name,
                Modifier.fillMaxSize().padding(14.dp),
                contentScale = ContentScale.Fit,
            )
            stream.number?.let {
                Text(
                    "$it",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(0.8f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(Color.Black.copy(0.45f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            if (isFavorite) FavoriteBadge(Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
        Text(
            stream.name,
            fontSize = 13.sp,
            color = TvStyle.secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp, start = 2.dp),
        )
    }
}

@Composable
fun StreamCard(
    stream: XtreamStream,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    progress: Float? = null,
) {
    if (stream.section == Section.LIVE) {
        ChannelTile(stream, isFavorite, onClick, onLongClick)
    } else {
        PosterCard(stream, isFavorite, onClick, onLongClick, progress = progress)
    }
}

@Composable
fun FavoriteBadge(modifier: Modifier = Modifier) {
    Box(
        modifier.size(22.dp).background(Color.Black.copy(0.55f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Default.Star, null, tint = TvStyle.favorite, modifier = Modifier.size(14.dp))
    }
}

@Composable
fun FavoriteIcon(isFavorite: Boolean) {
    Icon(
        if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
        contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
        tint = if (isFavorite) TvStyle.favorite else Color.White,
    )
}

@Composable
fun ProgressBar(progress: Float, modifier: Modifier = Modifier, height: Dp = 4.dp) {
    Box(modifier.fillMaxWidth().padding(6.dp)) {
        Box(Modifier.fillMaxWidth().height(height).clip(CircleShape).background(Color.White.copy(0.3f)))
        Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(height).clip(CircleShape).background(Color.White))
    }
}

/** A titled horizontal row of cards. */
@Composable
fun ShelfRow(
    shelf: Shelf,
    favorites: Set<String>,
    onOpen: (XtreamStream) -> Unit,
    onToggleFavorite: (XtreamStream) -> Unit,
    onSeeAll: (() -> Unit)? = null,
    progressFor: (XtreamStream) -> Float? = { null },
) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(if (onSeeAll != null) Modifier.clickable(onClick = onSeeAll) else Modifier)
                .padding(horizontal = 20.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(shelf.title, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            if (onSeeAll != null) {
                Spacer(Modifier.width(6.dp))
                Text("${shelf.items.size}", fontSize = 14.sp, color = TvStyle.tertiaryText)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = TvStyle.tertiaryText)
            }
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(shelf.items.take(40), key = { it.id }) { stream ->
                StreamCard(
                    stream,
                    stream.id in favorites,
                    onClick = { onOpen(stream) },
                    onLongClick = { onToggleFavorite(stream) },
                    progress = progressFor(stream),
                )
            }
        }
    }
}

@Composable
fun CenteredMessage(title: String, detail: String? = null, loading: Boolean = false, action: (@Composable () -> Unit)? = null) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 3.dp)
            Spacer(Modifier.size(16.dp))
        }
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        if (detail != null) {
            Spacer(Modifier.size(8.dp))
            Text(detail, fontSize = 15.sp, color = TvStyle.secondaryText, textAlign = TextAlign.Center)
        }
        if (action != null) {
            Spacer(Modifier.size(16.dp))
            action()
        }
    }
}
