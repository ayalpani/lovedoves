package com.yalpani.lovedoves.ui

import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationScrollTest {
    @Test
    fun contextMenuTimestampUsesRelativeDays() {
        val zone = ZoneId.of("Europe/Berlin")
        val now = ZonedDateTime.of(2026, 8, 13, 12, 0, 0, 0, zone)

        assertEquals(
            "Heute um 09:42",
            messageContextTimestamp(
                ZonedDateTime.of(2026, 8, 13, 9, 42, 0, 0, zone).toInstant().toEpochMilli(),
                now.toInstant().toEpochMilli(),
                zone,
            ),
        )
        assertEquals(
            "Gestern um 21:16",
            messageContextTimestamp(
                ZonedDateTime.of(2026, 8, 12, 21, 16, 0, 0, zone).toInstant().toEpochMilli(),
                now.toInstant().toEpochMilli(),
                zone,
            ),
        )
    }

    @Test
    fun ownMessageAlwaysPinsNewestItem() {
        assertTrue(shouldPinNewestMessage(newestOutgoing = true, firstVisibleItemIndex = 20))
    }

    @Test
    fun incomingMessageOnlyPinsWhenAlreadyNearBottom() {
        assertTrue(shouldPinNewestMessage(newestOutgoing = false, firstVisibleItemIndex = 1))
        assertFalse(shouldPinNewestMessage(newestOutgoing = false, firstVisibleItemIndex = 2))
    }

    @Test
    fun pinnedBannerAdvancesAndWrapsAfterEachTap() {
        assertEquals(1, nextPinnedMessageIndex(currentIndex = 0, pinnedMessageCount = 3))
        assertEquals(2, nextPinnedMessageIndex(currentIndex = 1, pinnedMessageCount = 3))
        assertEquals(0, nextPinnedMessageIndex(currentIndex = 2, pinnedMessageCount = 3))
        assertEquals(0, nextPinnedMessageIndex(currentIndex = 0, pinnedMessageCount = 1))
    }

    @Test
    fun pinnedMessageScrollAnimatesTheFinalViewport() {
        assertEquals(
            44,
            pinnedScrollApproachIndex(
                currentIndex = 0,
                targetIndex = 50,
                itemCount = 100,
                visibleItemCount = 6,
            ),
        )
        assertEquals(
            6,
            pinnedScrollApproachIndex(
                currentIndex = 50,
                targetIndex = 0,
                itemCount = 100,
                visibleItemCount = 6,
            ),
        )
        assertNull(
            pinnedScrollApproachIndex(
                currentIndex = 5,
                targetIndex = 8,
                itemCount = 100,
                visibleItemCount = 6,
            ),
        )
    }

    @Test
    fun pinnedMessageVisibilityExcludesHeaderAndComposer() {
        assertFalse(
            messageIsFullyVisibleInChatViewport(
                itemOffsetPx = 80,
                itemSizePx = 60,
                viewportTopPx = 120,
                viewportBottomPx = 700,
            ),
        )
        assertTrue(
            messageIsFullyVisibleInChatViewport(
                itemOffsetPx = 120,
                itemSizePx = 80,
                viewportTopPx = 120,
                viewportBottomPx = 700,
            ),
        )
        assertFalse(
            messageIsFullyVisibleInChatViewport(
                itemOffsetPx = 660,
                itemSizePx = 60,
                viewportTopPx = 120,
                viewportBottomPx = 700,
            ),
        )
    }

    @Test
    fun approachDistanceCountsOnlyMessagesInsideTheChatViewport() {
        assertFalse(
            messageIntersectsChatViewport(
                itemOffsetPx = 20,
                itemSizePx = 80,
                viewportTopPx = 120,
                viewportBottomPx = 700,
            ),
        )
        assertTrue(
            messageIntersectsChatViewport(
                itemOffsetPx = 100,
                itemSizePx = 80,
                viewportTopPx = 120,
                viewportBottomPx = 700,
            ),
        )
        assertFalse(
            messageIntersectsChatViewport(
                itemOffsetPx = 700,
                itemSizePx = 80,
                viewportTopPx = 120,
                viewportBottomPx = 700,
            ),
        )
    }

    @Test
    fun reverseLazyListOffsetIsConvertedToItsPhysicalScreenPosition() {
        assertEquals(
            424,
            lazyListItemPhysicalOffset(
                itemOffsetPx = 0,
                itemSizePx = 60,
                viewportSizePx = 600,
                viewportStartOffsetPx = -116,
                reverseLayout = true,
            ),
        )
        assertEquals(
            136,
            lazyListItemPhysicalOffset(
                itemOffsetPx = 0,
                itemSizePx = 60,
                viewportSizePx = 600,
                viewportStartOffsetPx = -136,
                reverseLayout = false,
            ),
        )
    }

    @Test
    fun pinnedMessageScrollTargetsTheHeaderEdgeInReverseLayout() {
        assertEquals(
            -220f,
            pinnedMessageScrollDelta(
                currentOffsetPx = 340,
                desiredOffsetPx = 120,
                reverseLayout = true,
            ),
        )
        assertEquals(
            220f,
            pinnedMessageScrollDelta(
                currentOffsetPx = 340,
                desiredOffsetPx = 120,
                reverseLayout = false,
            ),
        )
    }
}
