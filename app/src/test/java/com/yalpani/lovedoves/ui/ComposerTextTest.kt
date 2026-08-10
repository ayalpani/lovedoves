package com.yalpani.lovedoves.ui

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import org.junit.Assert.assertEquals
import org.junit.Test

class ComposerTextTest {
    @Test
    fun emojisAdvanceTheRememberedCursor() {
        val initial = TextFieldValue("Hallo ", TextRange(6))

        val updated = listOf("🥰", "❤️", "😘").fold(initial) { value, emoji ->
            value.insertAtSelection(emoji)
        }

        assertEquals("Hallo 🥰❤️😘", updated.text)
        assertEquals(updated.text.length, updated.selection.start)
        assertEquals(updated.selection.start, updated.selection.end)
    }

    @Test
    fun emojiReplacesSelectionAndPlacesCursorAfterIt() {
        val updated = TextFieldValue("Hallo Welt", TextRange(6, 10)).insertAtSelection("❤️")

        assertEquals("Hallo ❤️", updated.text)
        assertEquals(updated.text.length, updated.selection.start)
    }
}
