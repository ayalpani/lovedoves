package com.yalpani.lovedoves.ui

import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface as MaterialSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.domain.LoveDovesController
import com.yalpani.lovedoves.domain.LoveDovesRepository
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
internal fun MediaDetailScreen(
    messages: List<ConversationEventEntity>,
    initialEventId: String,
    photoBitmaps: PhotoBitmapLoader,
    controller: LoveDovesController,
    onBack: () -> Unit,
) {
    val mediaMessages = remember(messages) {
        messages.filter {
            it.kind == LoveDovesRepository.KIND_PHOTO ||
                it.kind == LoveDovesRepository.KIND_VIDEO ||
                it.kind == LoveDovesRepository.KIND_ROUND_VIDEO
        }
    }
    if (mediaMessages.isEmpty()) return
    val initialPage = remember(mediaMessages, initialEventId) {
        mediaMessages.indexOfFirst { it.id == initialEventId }.coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPage) { mediaMessages.size }
    BackHandler(onBack = onBack)
    CameraSystemBars()
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { mediaMessages[it].id },
            beyondViewportPageCount = 0,
        ) { page ->
            val message = mediaMessages[page]
            val mediaId = requireNotNull(message.mediaId)
            if (message.kind == LoveDovesRepository.KIND_PHOTO) {
                EncryptedPhotoImage(
                    mediaId = mediaId,
                    photoBitmaps = photoBitmaps,
                    contentDescription = "Foto ${page + 1} von ${mediaMessages.size}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    zoomable = true,
                    backgroundColor = Color.Black,
                )
            } else {
                VideoGalleryPage(
                    mediaId = mediaId,
                    page = page,
                    pageCount = mediaMessages.size,
                    active = pagerState.currentPage == page,
                    photoBitmaps = photoBitmaps,
                    controller = controller,
                )
            }
        }
        if (mediaMessages.size > 1) {
            MaterialSurface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 18.dp),
                color = Color.Black.copy(alpha = 0.48f),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(
                    text = "${pagerState.currentPage + 1} / ${mediaMessages.size}",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    color = Color.White,
                )
            }
        }
        CameraCloseButton("Medienansicht schließen", onBack)
    }
}

@Composable
private fun VideoGalleryPage(
    mediaId: String,
    page: Int,
    pageCount: Int,
    active: Boolean,
    photoBitmaps: PhotoBitmapLoader,
    controller: LoveDovesController,
) {
    if (!active) {
        EncryptedPhotoImage(
            mediaId = mediaId,
            photoBitmaps = photoBitmaps,
            contentDescription = "Video ${page + 1} von $pageCount",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            backgroundColor = Color.Black,
        )
        return
    }
    val videoBytes by produceState<ByteArray?>(null, mediaId, controller) {
        value = controller.mediaBytes(mediaId)
    }
    DisposableEffect(videoBytes) {
        val bytes = videoBytes
        onDispose { bytes?.fill(0) }
    }
    Box(Modifier.fillMaxSize()) {
        videoBytes?.let { bytes ->
            MemoryVideoPlayer(bytes = bytes, autoplay = true)
        } ?: CircularProgressIndicator(
            modifier = Modifier.align(Alignment.Center),
            color = Color.White,
        )
    }
}

@Composable
private fun MemoryVideoPlayer(bytes: ByteArray, autoplay: Boolean) {
    var textureView by remember { mutableStateOf<TextureView?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var isPlaying by remember { mutableStateOf(false) }
    var position by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(1f) }
    var aspectRatio by remember { mutableFloatStateOf(9f / 16f) }

    DisposableEffect(textureView, bytes) {
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
            currentMemory = MemoryMediaFile.fromBytes(bytes)
            currentDescriptor = currentMemory?.duplicate()
            currentSurface = Surface(surfaceTexture)
            currentPlayer = MediaPlayer().apply {
                val descriptor = requireNotNull(currentDescriptor)
                setDataSource(descriptor.fileDescriptor, 0L, bytes.size.toLong())
                setSurface(currentSurface)
                setOnPreparedListener { prepared ->
                    duration = prepared.duration.coerceAtLeast(1).toFloat()
                    if (prepared.videoWidth > 0 && prepared.videoHeight > 0) {
                        aspectRatio = prepared.videoWidth.toFloat() / prepared.videoHeight
                    }
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

    LaunchedEffect(autoplay, player) {
        player?.let { current ->
            if (autoplay && !current.isPlaying) {
                current.start()
                isPlaying = true
            } else if (!autoplay && current.isPlaying) {
                current.pause()
                isPlaying = false
            }
        }
    }
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            position = runCatching { player?.currentPosition?.toFloat() ?: 0f }.getOrDefault(0f)
            delay(100)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val videoModifier = if (maxWidth / maxHeight > aspectRatio) {
            Modifier.fillMaxHeight().aspectRatio(aspectRatio)
        } else {
            Modifier.fillMaxWidth().aspectRatio(aspectRatio)
        }
        AndroidView(
            factory = { TextureView(it).also { view -> textureView = view } },
            modifier = videoModifier.semantics { contentDescription = "Video" },
        )
        IconButton(
            onClick = {
                player?.let { current ->
                    if (current.isPlaying) {
                        current.pause()
                        isPlaying = false
                    } else {
                        current.start()
                        isPlaying = true
                    }
                }
            },
            modifier = Modifier.align(Alignment.Center).size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.5f),
                contentColor = Color.White,
            ),
        ) {
            if (isPlaying) PauseIcon("Pausieren") else PlayIcon("Abspielen")
        }
        Slider(
            value = position.coerceIn(0f, duration),
            onValueChange = {
                position = it
                player?.seekTo(it.roundToInt())
            },
            valueRange = 0f..duration,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 18.dp),
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.3f),
            ),
        )
    }
}
