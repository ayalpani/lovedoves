package com.yalpani.lovedoves.ui

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import com.yalpani.lovedoves.LoveBlush
import com.yalpani.lovedoves.LoveDovesTheme
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.LovePaper

private const val AnnouncementPreferences = "love-doves-announcements"
private const val LastDismissedAnnouncement = "last-dismissed-id"

private data class FeatureAnnouncement(
    val id: String,
    val title: String,
    val description: String,
    val artwork: @Composable (Modifier) -> Unit,
)

// Release checklist: replace this entry. A new id makes it appear once per installation.
private val LatestFeatureAnnouncement = FeatureAnnouncement(
    id = "message-actions-2026-08",
    title = "Mehr Möglichkeiten im Chat",
    description = "Halte eine Nachricht gedrückt. Du kannst jetzt antworten, anpinnen und eigene Texte ändern.",
    artwork = { MessageActionsArtwork(it) },
)

@Composable
internal fun FeatureAnnouncementHost(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val announcement = remember(context) {
        LatestFeatureAnnouncement.takeUnless {
            context.getSharedPreferences(AnnouncementPreferences, Context.MODE_PRIVATE)
                .getString(LastDismissedAnnouncement, null) == it.id
        }
    }
    var dismissed by remember(context) { mutableStateOf(false) }

    AnimatedVisibility(
        visible = visible && announcement != null && !dismissed,
        modifier = modifier,
        enter = fadeIn(tween(220)) + slideInVertically(tween(320)) { it / 4 },
        exit = fadeOut(tween(160)) + slideOutVertically(tween(220)) { it / 5 },
        label = "feature announcement",
    ) {
        announcement?.let { item ->
            FeatureAnnouncementCard(
                announcement = item,
                onDismiss = {
                    context.getSharedPreferences(AnnouncementPreferences, Context.MODE_PRIVATE)
                        .edit { putString(LastDismissedAnnouncement, item.id) }
                    dismissed = true
                },
            )
        }
    }
}

@Composable
private fun FeatureAnnouncementCard(
    announcement: FeatureAnnouncement,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val cardWidth = minOf(maxWidth * 0.78f, 320.dp)
        Box(Modifier.width(cardWidth).padding(top = 12.dp, end = 12.dp)) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(248.dp),
                shape = RoundedCornerShape(20.dp),
                color = Color.White,
                border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.12f)),
                shadowElevation = 10.dp,
            ) {
                Column {
                    announcement.artwork(Modifier.fillMaxWidth().height(136.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(112.dp)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Text(
                            text = announcement.title,
                            color = LoveInk,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = announcement.description,
                            color = LoveInk.copy(alpha = 0.68f),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).size(48.dp),
            ) {
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = Color.White,
                    border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.12f)),
                    shadowElevation = 3.dp,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        CloseIcon("Ankündigung schließen", Modifier.size(15.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageActionsArtwork(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        LoveBlush,
                        Color(0xFFE7E1F3),
                        Color(0xFFF3E5CB),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .width(204.dp)
                .rotate(-3f),
            shape = RoundedCornerShape(17.dp),
            color = Color.White.copy(alpha = 0.94f),
            border = BorderStroke(1.dp, LoveInk.copy(alpha = 0.08f)),
            shadowElevation = 5.dp,
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 11.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ReplyIcon(modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "Bis später",
                        color = LoveInk.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = "Ich freu mich auf dich ❤️",
                    color = LoveInk,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 13.dp)
                .width(150.dp)
                .height(42.dp),
            shape = CircleShape,
            color = LoveInk,
            contentColor = Color.White,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReplyIcon(modifier = Modifier.size(17.dp))
                PinIcon(modifier = Modifier.size(17.dp), color = Color.White)
                EditIcon(modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFFF7F5F0, widthDp = 412, heightDp = 360)
@Composable
private fun FeatureAnnouncementPreview() {
    LoveDovesTheme {
        Box(
            Modifier.fillMaxSize().background(LovePaper).padding(16.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            FeatureAnnouncementCard(LatestFeatureAnnouncement, onDismiss = {})
        }
    }
}
