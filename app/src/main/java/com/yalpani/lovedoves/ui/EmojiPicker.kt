package com.yalpani.lovedoves.ui

import android.view.ContextThemeWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.rememberNestedScrollInteropConnection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import com.yalpani.lovedoves.R

private object LoveDovesRecentEmojiProvider : RecentEmojiProvider {
    override suspend fun getRecentEmojiList(): List<String> = listOf(
        "❤️", "🥰", "😘", "😍", "😊", "😂", "🤗", "💕",
    )

    // Emoji history would itself be private content, so Love Doves does not persist it unencrypted.
    override fun recordSelection(emoji: String) = Unit
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun EmojiPickerBottomSheet(
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onEmojiPicked: (String) -> Unit,
) {
    LoveModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        BackHandler(onBack = onDismiss)
        EmojiPickerSheet(onEmojiPicked)
    }
}

@Composable
private fun EmojiPickerSheet(onEmojiPicked: (String) -> Unit) {
    val context = LocalContext.current
    val currentOnEmojiPicked = rememberUpdatedState(onEmojiPicked)
    val nestedScrollConnection = rememberNestedScrollInteropConnection()
    Column(
        Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(bottom = 12.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Emoji wählen",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        AndroidView(
            factory = { viewContext ->
                val pickerContext = ContextThemeWrapper(viewContext, R.style.Theme_LoveDoves)
                EmojiPickerView(pickerContext).apply {
                    emojiGridColumns = 8
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    setRecentEmojiProvider(LoveDovesRecentEmojiProvider)
                    setOnEmojiPickedListener { currentOnEmojiPicked.value(it.emoji) }
                }
            },
            update = { picker ->
                picker.setOnEmojiPickedListener { currentOnEmojiPicked.value(it.emoji) }
            },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(nestedScrollConnection),
        )
    }
}
