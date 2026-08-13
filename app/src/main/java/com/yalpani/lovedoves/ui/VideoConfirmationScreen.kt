package com.yalpani.lovedoves.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.domain.PreparedVideo
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun VideoConfirmationSurface(
    video: PreparedVideo,
    landscape: Boolean,
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
) {
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    val duration = video.durationMillis.toFloat().coerceAtLeast(1f)

    DisposableEffect(textureView, video) {
        val view = textureView
        var currentPlayer: MediaPlayer? = null
        var currentSurface: Surface? = null
        var currentMemory: MemoryMediaFile? = null
        var currentDescriptor: android.os.ParcelFileDescriptor? = null

        fun releasePlayback() {
            runCatching { currentPlayer?.release() }
            currentPlayer = null
            player = null
            currentSurface?.release()
            currentSurface = null
            runCatching { currentDescriptor?.close() }
            currentDescriptor = null
            runCatching { currentMemory?.close() }
            currentMemory = null
        }

        fun preparePlayback(surfaceTexture: SurfaceTexture) {
            releasePlayback()
            currentMemory = MemoryMediaFile.fromBytes(video.mp4)
            currentDescriptor = currentMemory?.duplicate()
            currentSurface = Surface(surfaceTexture)
            currentPlayer = MediaPlayer().apply {
                val descriptor = requireNotNull(currentDescriptor)
                setDataSource(descriptor.fileDescriptor, 0L, video.mp4.size.toLong())
                setSurface(currentSurface)
                setOnPreparedListener { prepared ->
                    prepared.seekTo(1)
                    player = prepared
                }
                setOnCompletionListener { completed ->
                    isPlaying = false
                    position = 0f
                    completed.seekTo(1)
                }
                prepareAsync()
            }
        }

        val listener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = preparePlayback(surface)

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int,
            ) = Unit

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releasePlayback()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) = Unit
        }
        view?.surfaceTextureListener = listener
        if (view?.isAvailable == true) view.surfaceTexture?.let(::preparePlayback)

        onDispose {
            view?.surfaceTextureListener = null
            releasePlayback()
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = runCatching { player?.currentPosition?.toFloat() ?: 0f }.getOrDefault(0f)
            delay(100)
        }
    }

    val seek: (Float) -> Unit = {
        position = it
        player?.seekTo(it.roundToInt())
    }
    val togglePlayback: () -> Unit = {
        player?.let { current ->
            if (current.isPlaying) {
                current.pause()
                isPlaying = false
            } else {
                current.start()
                isPlaying = true
            }
        }
        Unit
    }
    val aspectRatio = video.width.toFloat() / video.height.coerceAtLeast(1)

    if (landscape) {
        Row(Modifier.fillMaxSize().background(Color.Black)) {
            VideoConfirmationPreview(
                aspectRatio,
                isPlaying,
                { textureView = it },
                togglePlayback,
                Modifier.weight(1f).fillMaxHeight(),
            )
            VideoConfirmationPanel(true, position, duration, seek, onDiscard, onAccept)
        }
    } else {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            VideoConfirmationPreview(
                aspectRatio,
                isPlaying,
                { textureView = it },
                togglePlayback,
                Modifier.fillMaxWidth().weight(1f),
            )
            VideoConfirmationPanel(false, position, duration, seek, onDiscard, onAccept)
        }
    }
}

@Composable
private fun VideoConfirmationPreview(
    aspectRatio: Float,
    isPlaying: Boolean,
    onTextureView: (TextureView) -> Unit,
    onTogglePlayback: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val mediaModifier = if (maxWidth / maxHeight > aspectRatio) {
            Modifier.fillMaxHeight().aspectRatio(aspectRatio)
        } else {
            Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        }
        Box(modifier = mediaModifier, contentAlignment = Alignment.Center) {
            AndroidView(
                factory = { TextureView(it).also(onTextureView) },
                modifier = Modifier.fillMaxSize().semantics {
                    contentDescription = "Aufgenommenes Video"
                },
            )
            IconButton(
                onClick = onTogglePlayback,
                modifier = Modifier.size(72.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.88f),
                    contentColor = LoveInk,
                ),
            ) {
                if (isPlaying) {
                    PauseIcon("Videowiedergabe pausieren")
                } else {
                    PlayIcon("Video abspielen", modifier = Modifier.size(30.dp))
                }
            }
        }
    }
}

@Composable
private fun VideoConfirmationPanel(
    landscape: Boolean,
    position: Float,
    duration: Float,
    onSeek: (Float) -> Unit,
    onDiscard: () -> Unit,
    onAccept: () -> Unit,
) {
    AnimatedMediaConfirmationPanel(
        landscape = landscape,
        onDiscard = onDiscard,
        onAccept = onAccept,
        acceptLabel = "Video verwenden",
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Slider(
                value = position.coerceIn(0f, duration),
                onValueChange = onSeek,
                valueRange = 0f..duration,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = LoveInk,
                    activeTrackColor = LoveInk,
                    inactiveTrackColor = LoveInk.copy(alpha = 0.24f),
                ),
            )
            Text(
                text = formatMediaDuration(duration.toLong()),
                color = LoveInk,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

internal fun formatMediaDuration(durationMillis: Long): String {
    val totalSeconds = durationMillis.coerceAtLeast(0L) / 1_000L
    return "%d:%02d".format(totalSeconds / 60L, totalSeconds % 60L)
}
