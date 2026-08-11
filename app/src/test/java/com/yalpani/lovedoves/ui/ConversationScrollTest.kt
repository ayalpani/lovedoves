package com.yalpani.lovedoves.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationScrollTest {
    @Test
    fun ownMessageAlwaysPinsNewestItem() {
        assertTrue(shouldPinNewestMessage(newestOutgoing = true, firstVisibleItemIndex = 20))
    }

    @Test
    fun incomingMessageOnlyPinsWhenAlreadyNearBottom() {
        assertTrue(shouldPinNewestMessage(newestOutgoing = false, firstVisibleItemIndex = 1))
        assertFalse(shouldPinNewestMessage(newestOutgoing = false, firstVisibleItemIndex = 2))
    }
}
