package com.vanderck.xtreamtv.player

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.session.MediaSession
import com.vanderck.xtreamtv.data.Playable
import com.vanderck.xtreamtv.data.TvModel
import com.vanderck.xtreamtv.data.XtreamClient
import com.vanderck.xtreamtv.ui.XtreamTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Full-screen player. Runs in its own task-less single instance so that a
 * new channel replaces the current one, and so picture-in-picture works.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {
    companion object {
        /** The playable to open; handed over in-process rather than through the Intent. */
        @Volatile private var pending: Playable? = null

        fun launch(context: Context, playable: Playable) {
            pending = playable
            context.startActivity(
                Intent(context, PlayerActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
            )
        }
    }

    private lateinit var model: TvModel
    lateinit var player: ExoPlayer
        private set
    private var session: MediaSession? = null

    var current by mutableStateOf<Playable?>(null)
        private set
    var isInPip by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var usingFallback = false
    private var videoAspect = Rational(16, 9)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        model = TvModel.get(this)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        player = buildPlayer()
        session = runCatching { MediaSession.Builder(this, player).build() }.getOrNull()

        val playable = pending
        if (playable == null) {
            finish()
            return
        }
        pending = null
        open(playable)

        setContent {
            XtreamTheme { PlayerScreen(this, model) }
        }

        // Save resume points every 10 seconds while playing.
        lifecycleScope.launch {
            while (isActive) {
                delay(10_000)
                savePosition()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pending?.let {
            pending = null
            open(it)
        }
    }

    private fun buildPlayer(): ExoPlayer {
        val http = OkHttpDataSource.Factory(XtreamClient.defaultHttp).setUserAgent(XtreamClient.USER_AGENT)
        // IPTV MPEG-TS streams often lack an AUD/IDR at the start or carry
        // odd PIDs; these flags make the TS extractor far more forgiving.
        val extractors = DefaultExtractorsFactory()
            .setTsExtractorFlags(DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS)
            .setConstantBitrateSeekingEnabled(true)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 60_000, 1_500, 3_000)
            .build()
        val renderers = DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(true)
        return ExoPlayer.Builder(this, renderers)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http, extractors))
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(30_000)
            .build()
            .apply {
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) = handleError(error)
                    override fun onVideoSizeChanged(videoSize: VideoSize) {
                        if (videoSize.width > 0 && videoSize.height > 0) {
                            val ratio = videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                            // Android rejects PiP ratios outside 1:2.39 … 2.39:1.
                            videoAspect = Rational((ratio.coerceIn(0.42f, 2.38f) * 1000).toInt(), 1000)
                            updatePipParams()
                        }
                    }
                    override fun onIsPlayingChanged(isPlaying: Boolean) = updatePipParams()
                })
            }
    }

    /** Starts a new channel, movie or episode. */
    fun open(playable: Playable) {
        savePosition()
        current = playable
        usingFallback = false
        errorMessage = null
        start(playable.url)
        val resume = playable.resumeKey?.let { model.store.position(it) } ?: 0L
        if (resume > 0) player.seekTo(resume)
    }

    private fun start(url: String) {
        val playable = current ?: return
        val item = MediaItem.Builder()
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(playable.title)
                    .setSubtitle(playable.subtitle)
                    .setArtworkUri(playable.artworkUrl?.let(android.net.Uri::parse))
                    .build(),
            )
            .apply { if (url.endsWith(".m3u8")) setMimeType(androidx.media3.common.MimeTypes.APPLICATION_M3U8) }
            .build()
        player.setMediaItem(item)
        player.prepare()
        player.play()
    }

    fun retry() {
        val playable = current ?: return
        errorMessage = null
        usingFallback = false
        start(playable.url)
    }

    private fun handleError(error: PlaybackException) {
        val playable = current ?: return
        when {
            // A live stream that fell behind its window just needs a restart at the edge.
            error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                player.seekToDefaultPosition()
                player.prepare()
            }
            // Automatic mode: TS refused or unparseable → try the panel's HLS.
            !usingFallback && playable.fallbackUrl != null -> {
                usingFallback = true
                start(playable.fallbackUrl)
            }
            else -> errorMessage = describe(error)
        }
    }

    private fun describe(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> {
            val code = (error.cause as? androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException)?.responseCode
            when (code) {
                401, 403 -> "The provider refused this stream (HTTP $code). You may have too many connections open."
                404 -> "This stream is offline (HTTP 404)."
                null -> "The provider returned an error."
                else -> "The provider returned HTTP $code."
            }
        }
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Couldn't connect to the stream. Check your connection."
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED -> "This stream's format isn't supported."
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED -> "This phone can't decode this stream's video or audio."
        else -> error.localizedMessage ?: "Playback failed."
    }

    fun zap(delta: Int) {
        val stream = current?.stream ?: return
        model.zap(stream, delta)?.let(::open)
    }

    private fun savePosition() {
        val key = current?.resumeKey ?: return
        if (!::player.isInitialized) return
        val duration = player.duration
        if (duration == C.TIME_UNSET || duration <= 0) return
        model.store.savePosition(key, player.currentPosition, duration)
    }

    // MARK: Picture-in-picture

    private fun pipParams(): PictureInPictureParams? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val builder = PictureInPictureParams.Builder().setAspectRatio(videoAspect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(model.autoPip.value && player.isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }
        return builder.build()
    }

    private fun updatePipParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipParams()?.let { runCatching { setPictureInPictureParams(it) } }
    }

    fun enterPip() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) pipParams()?.let { runCatching { enterPictureInPictureMode(it) } }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Android 12+ enters automatically through setAutoEnterEnabled.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && model.autoPip.value && player.isPlaying) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip = isInPictureInPictureMode
        // Closing the PiP window stops the activity without resuming it.
        if (!isInPictureInPictureMode && lifecycle.currentState == Lifecycle.State.CREATED) finish()
        if (!isInPictureInPictureMode) hideSystemBars()
    }

    override fun onStop() {
        super.onStop()
        savePosition()
        if (!isInPip) player.pause()
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized && current != null && errorMessage == null) player.play()
    }

    override fun onDestroy() {
        savePosition()
        session?.release()
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
