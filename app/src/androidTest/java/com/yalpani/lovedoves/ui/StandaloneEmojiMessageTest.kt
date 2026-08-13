package com.yalpani.lovedoves.ui

import android.content.Context
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.yalpani.lovedoves.LoveDovesTheme
import com.yalpani.lovedoves.R
import com.yalpani.lovedoves.data.ConversationEventEntity
import com.yalpani.lovedoves.domain.LoveDovesRepository
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StandaloneEmojiMessageTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun oneEmojiGraphemeIsRecognizedWithoutTreatingPlainTextAsEmoji() {
        assertTrue(isSingleEmoji("😀"))
        assertTrue(isSingleEmoji("👍🏽"))
        assertTrue(isSingleEmoji("👨‍👩‍👧‍👦"))
        assertTrue(isSingleEmoji("🇩🇪"))
        assertTrue(isSingleEmoji("1️⃣"))
        assertTrue(isSingleEmoji("❤"))
        assertFalse(isSingleEmoji("1"))
        assertFalse(isSingleEmoji("A"))
        assertFalse(isSingleEmoji("😀😀"))
        assertFalse(isSingleEmoji(" 😀"))
    }

    @Test
    fun loveCategoryIncludesRequestedEmojiFamilies() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val emoji = context.resources.openRawResource(R.raw.emoji_category_love)
            .bufferedReader()
            .useLines { lines -> lines.map { it.substringBefore(',') }.toSet() }

        setOf(
            "👄", "🫦", "👅", "🧠", "🫀", "🩸", "🫂", "🫶", "🤝", "👥",
            "❤️", "🧡", "💛", "💚", "🩵", "💙", "💜", "🤎", "🖤", "🩶", "🤍", "🩷",
            "🧑‍🤝‍🧑", "👭", "👬", "👫", "💏", "💑",
            "👩‍❤️‍💋‍👨", "👨‍❤️‍💋‍👨", "👩‍❤️‍💋‍👩",
            "👩‍❤️‍👨", "👨‍❤️‍👨", "👩‍❤️‍👩", "🕊️", "🔥", "🌈",
            "🌞", "⭐", "🌟", "✨", "🍉", "🍈",
            "💐", "🌹", "🥀", "🌺", "🌷", "🪷", "🌸", "💮", "🏵️", "🪻", "🌻", "🌼",
        ).forEach { expected ->
            assertTrue("Missing $expected from the love category", expected in emoji)
        }
    }

    @Test
    fun standaloneEmojiIncludesRoomBelowForItsMetadata() {
        val message = ConversationEventEntity(
            id = "emoji-message",
            outgoing = true,
            kind = LoveDovesRepository.KIND_TEXT,
            body = "😀",
            mediaId = null,
            createdAtEpochMillis = 0L,
            deliveryState = LoveDovesRepository.DELIVERY_READ,
        )
        composeRule.setContent {
            LoveDovesTheme {
                StandaloneEmojiMessage(message)
            }
        }

        val node = composeRule.onNodeWithTag(STANDALONE_EMOJI_TAG).assertExists()
            .fetchSemanticsNode()
        val minimumHeightPx = with(composeRule.density) { 78.dp.toPx() }
        assertTrue(node.boundsInRoot.height >= minimumHeightPx)
    }
}
