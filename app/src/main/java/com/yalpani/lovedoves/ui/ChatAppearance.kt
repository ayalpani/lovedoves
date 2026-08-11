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
