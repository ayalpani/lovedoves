package com.yalpani.lovedoves.ui

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.yalpani.lovedoves.LovePaper

internal enum class ChatBackgroundOption(
    val label: String,
    val color: Color,
) {
    PAPER("Papier", LovePaper),
    BLUSH("Rosa", Color(0xFFFFE8EC)),
    PEACH("Pfirsich", Color(0xFFFFEBDD)),
    SAGE("Salbei", Color(0xFFE5F0E4)),
    SKY("Himmel", Color(0xFFE5EFF8)),
    LAVENDER("Lavendel", Color(0xFFEDE8F7));

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
