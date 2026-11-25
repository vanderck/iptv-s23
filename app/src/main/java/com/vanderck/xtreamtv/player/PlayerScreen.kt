package com.vanderck.xtreamtv.player

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamProgramme
import com.vanderck.xtreamtv.ui.ChannelRow
import com.vanderck.xtreamtv.ui.FavoriteIcon
import com.vanderck.xtreamtv.ui.TvStyle
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(activity: PlayerActivity, model: TvModel) {
    val playable = activity.current ?: return
    val isLive = playable.isLive
    val inPip = activity.isInPip
    val favorites by model.favorites.collectAsStateWithLifecycle()

    var overlayVisible by remember { mutableStateOf(true) }
    var showChannels by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(true) }
    var zoom by remember { mutableStateOf(false) }

    DisposableEffect(activity.player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { buffering = state == Player.STATE_BUFFERING }
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
        }
        activity.player.addListener(listener)
        onDispose { activity.player.removeListener(listener) }
    }

    // Auto-hide the live overlay after a few seconds.
    LaunchedEffect(overlayVisible, playable, showChannels) {
        if (overlayVisible && isLive && !showChannels) {
            delay(5000)
            overlayVisible = false
        }
    }

    // EPG for live channels, refreshed every minute.
    var guide by remember(playable.stream?.id) { mutableStateOf<List<XtreamProgramme>>(emptyList()) }
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(playable.stream?.id) {
        val stream = playable.stream ?: return@LaunchedEffect
        if (!isLive) return@LaunchedEffect
        while (true) {
            guide = model.shortEpg(stream)
            repeat(4) {
                now = System.currentTimeMillis()
                delay(15_000)
            }
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = activity.player
                    keepScreenOn = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_NEVER)
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
                        overlayVisible = v == android.view.View.VISIBLE
                    })
                }
            },
            update = { view ->
                // Live TV uses the Compose overlay below; movies use the
                // built-in controller, which also offers audio and subtitle tracks.
                view.useController = !isLive && !inPip
                view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM else AspectRatioFrameLayout.RESIZE_MODE_FIT
                if (inPip) view.hideController()
            },
            modifier = Modifier.fillMaxSize(),
        )

        if (inPip) return@Box

        if (isLive) {
            // Tap toggles the overlay, double-tap zooms, vertical swipe changes channel.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(playable) {
                        detectTapGestures(
                            onTap = { overlayVisible = !overlayVisible; showChannels = false },
                            onDoubleTap = { zoom = !zoom },
                        )
                    }
                    .pointerInput(playable) {
                        var total = 0f
                        detectVerticalDragGestures(
                            onDragStart = { total = 0f },
                            onDragEnd = {
                                if (total < -120) activity.zap(+1) else if (total > 120) activity.zap(-1)
                            },
                        ) { _, dy -> total += dy }
                    },
            )
        }

        if (buffering && activity.errorMessage == null) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(48.dp))
        }

        AnimatedVisibility(
            visible = overlayVisible || activity.errorMessage != null,
            enter = fadeIn(), exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(Modifier.fillMaxSize()) {
                // Top bar.
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(0.7f), Color.Transparent)))
                        .safeDrawingPadding()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { activity.finish() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close", tint = Color.White) }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(playable.title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        playable.subtitle?.let { Text(it, fontSize = 13.sp, color = TvStyle.secondaryText, maxLines = 1) }
                    }
                    playable.stream?.let { stream ->
                        IconButton(onClick = { model.toggleFavorite(stream) }) { FavoriteIcon(stream.id in favorites) }
                    }
                    if (isLive) {
                        IconButton(onClick = { showChannels = !showChannels; overlayVisible = true }) {
                            Icon(Icons.AutoMirrored.Filled.List, "Channels", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { activity.enterPip() }) { Icon(Icons.Default.PictureInPictureAlt, "Picture in picture", tint = Color.White) }
                }

                if (isLive && activity.errorMessage == null) {
                    // Centre controls: channel down, play/pause, channel up.
                    Row(
                        Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RoundButton(Icons.Default.KeyboardArrowDown, "Previous channel") { activity.zap(-1) }
                        RoundButton(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, "Play/Pause", big = true) {
                            if (activity.player.isPlaying) activity.player.pause() else {
                                // Resuming live jumps back to the live edge.
                                activity.player.seekToDefaultPosition()
                                activity.player.play()
                            }
                        }
                        RoundButton(Icons.Default.KeyboardArrowUp, "Next channel") { activity.zap(+1) }
                    }
                    LiveBanner(guide, now, Modifier.align(Alignment.BottomCenter))
                }

                activity.errorMessage?.let { message ->
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                            .background(Color.Black.copy(0.75f), RoundedCornerShape(16.dp))
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("Can't play this stream", fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(message, fontSize = 14.sp, color = TvStyle.secondaryText)
                        Spacer(Modifier.height(16.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = activity::retry,
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                            ) { Text("Try Again") }
                            if (isLive) {
                                Button(
                                    onClick = { activity.zap(+1) },
                                    colors = ButtonDefaults.buttonColors(containerColor = TvStyle.platterHigh, contentColor = Color.White),
                                ) { Text("Next Channel") }
                            }
                        }
                    }
                }
            }
        }

        // Channel list panel.
        val stream = playable.stream
        AnimatedVisibility(
            visible = showChannels && isLive && stream != null,
            enter = slideInHorizontally { it } + fadeIn(),
            exit = slideOutHorizontally { it } + fadeOut(),
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            if (stream == null) return@AnimatedVisibility
            val channels = remember(stream.categoryId) { model.channelsLike(stream) }
            val index = channels.indexOfFirst { it.id == stream.id }.coerceAtLeast(0)
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = (index - 2).coerceAtLeast(0))
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(360.dp)
                    .background(Color(0xF0121216))
                    .safeDrawingPadding(),
            ) {
                Text(
                    "Channels", fontSize = 18.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(16.dp),
                )
                LazyColumn(state = listState) {
                    items(channels, key = { it.id }) { ch ->
                        ChannelRow(
                            ch,
                            isFavorite = ch.id in favorites,
                            highlighted = ch.id == stream.id,
                            onClick = { model.playable(ch)?.let(activity::open) },
                            onLongClick = { model.toggleFavorite(ch) },
                            onToggleFavorite = { model.toggleFavorite(ch) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    big: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(if (big) 72.dp else 56.dp)
            .background(Color.Black.copy(0.45f), CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(if (big) 40.dp else 32.dp))
    }
}

/** What's on now (with progress) and next, from the provider's EPG. */
@Composable
private fun LiveBanner(guide: List<XtreamProgramme>, now: Long, modifier: Modifier = Modifier) {
    val current = guide.firstOrNull { it.start <= now && it.end > now }
    val next = guide.firstOrNull { it.start >= (current?.end ?: now) }
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)

    Column(
        modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(0.85f))))
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "LIVE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White,
                modifier = Modifier.background(TvStyle.live, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                current?.title ?: "No programme information",
                fontSize = 17.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            current?.let {
                Text("${time.format(Date(it.start))} – ${time.format(Date(it.end))}", fontSize = 13.sp, color = TvStyle.secondaryText)
            }
        }
        if (current != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { current.progress(now) },
                color = Color.White,
                trackColor = Color.White.copy(0.25f),
                modifier = Modifier.fillMaxWidth().height(3.dp),
                drawStopIndicator = {},
            )
            if (current.description.isNotBlank()) {
                Text(current.description, fontSize = 13.sp, color = TvStyle.secondaryText, maxLines = 2,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
            }
        }
        next?.let {
            Text(
                "Next · ${time.format(Date(it.start))}  ${it.title}",
                fontSize = 13.sp, color = TvStyle.tertiaryText, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Text(
            "Swipe up/down to change channel · double-tap to zoom",
            fontSize = 11.sp, color = TvStyle.tertiaryText, modifier = Modifier.padding(top = 6.dp),
        )
    }
}
