package com.yalpani.lovedoves.ui

import android.view.ContextThemeWrapper
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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
internal fun EmojiPickerKeyboard(
    modifier: Modifier = Modifier,
    onEmojiPicked: (String) -> Unit,
) {
    val currentOnEmojiPicked = rememberUpdatedState(onEmojiPicked)
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
        modifier = modifier.fillMaxWidth(),
    )
}
