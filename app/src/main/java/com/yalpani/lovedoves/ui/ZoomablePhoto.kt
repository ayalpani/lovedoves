package com.yalpani.lovedoves.ui

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.flow.Flow
import me.saket.telephoto.zoomable.ZoomableImage
import me.saket.telephoto.zoomable.ZoomableImageSource

@Composable
internal fun ZoomablePhoto(
    bitmap: Bitmap,
    contentDescription: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val painter = remember(bitmap) { BitmapPainter(bitmap.asImageBitmap()) }
    val source = remember(painter) { MemoryBitmapSource(painter) }
    key(bitmap) {
        ZoomableImage(
            image = source,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
        )
    }
}

private class MemoryBitmapSource(
    private val painter: BitmapPainter,
) : ZoomableImageSource {
    @Composable
    override fun resolve(canvasSize: Flow<Size>) = ZoomableImageSource.ResolveResult(
        ZoomableImageSource.PainterDelegate(painter),
    )
}
