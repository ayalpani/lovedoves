package com.yalpani.lovedoves.ui

import android.content.Context
import androidx.compose.ui.graphics.Color

internal enum class ChatBackgroundOption(
    val label: String,
    val color: Color,
) {
    PAPER("Papier", Color(0xFFD2CCC1)),
    BLUSH("Rosa", Color(0xFFDCA8B4)),
    PEACH("Pfirsich", Color(0xFFE2B895)),
    SAGE("Salbei", Color(0xFFAFC8AB)),
    SKY("Himmel", Color(0xFFACC8DD)),
    LAVENDER("Lavendel", Color(0xFFC3B3DA));

    fun persist(context: Context) {
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(BACKGROUND_KEY, name)
            .apply()
    }

    companion object {
        fun load(context: Context): ChatBackgroundOption {
            val stored = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
                .getString(BACKGROUND_KEY, null)
            return entries.firstOrNull { it.name == stored } ?: PAPER
        }

        private const val PREFERENCES = "love-doves-appearance"
        private const val BACKGROUND_KEY = "chat-background"
    }
}

internal object ComposerInputPreferences {
    fun keyboardHeightPx(context: Context, orientation: Int): Int =
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getInt(key(orientation), 0)
            .coerceAtLeast(0)

    fun persistKeyboardHeightPx(context: Context, orientation: Int, heightPx: Int) {
        if (heightPx <= 0) return
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putInt(key(orientation), heightPx)
            .apply()
    }

    private fun key(orientation: Int): String = "$KEY_PREFIX-$orientation"

    private const val PREFERENCES = "love-doves-input"
    private const val KEY_PREFIX = "keyboard-height-px"
}
