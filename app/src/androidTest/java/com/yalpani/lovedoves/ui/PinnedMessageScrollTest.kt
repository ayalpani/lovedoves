package com.yalpani.lovedoves.ui

import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class PinnedMessageScrollTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun reverseListPlacesTheTargetAtTheVisibleHeaderEdge() {
        val scrollFinished = AtomicBoolean(false)
        composeRule.setContent {
            val listState: LazyListState = rememberLazyListState()
            val density = LocalDensity.current
            LazyColumn(
                state = listState,
                modifier = Modifier.width(320.dp).height(600.dp).testTag("list"),
                contentPadding = PaddingValues(top = 136.dp, bottom = 116.dp),
                reverseLayout = true,
            ) {
                items((0 until 100).toList()) { index ->
                    Box(Modifier.fillMaxWidth().height(60.dp).testTag("item-$index"))
                }
            }
            LaunchedEffect(listState) {
                listState.scrollToItem(50)
                val layoutInfo = listState.layoutInfo
                val item = layoutInfo.visibleItemsInfo.first { it.index == 50 }
                val currentPhysicalOffsetPx = lazyListItemPhysicalOffset(
                    itemOffsetPx = item.offset,
                    itemSizePx = item.size,
                    viewportSizePx = layoutInfo.viewportSize.height,
                    viewportStartOffsetPx = layoutInfo.viewportStartOffset,
                    reverseLayout = layoutInfo.reverseLayout,
                )
                val desiredOffsetPx = with(density) { 121.5.dp.roundToPx() }
                listState.animateScrollBy(
                    value = pinnedMessageScrollDelta(
                        currentOffsetPx = currentPhysicalOffsetPx,
                        desiredOffsetPx = desiredOffsetPx,
                        reverseLayout = layoutInfo.reverseLayout,
                    ),
                    animationSpec = tween(1),
                )
                scrollFinished.set(true)
            }
        }

        composeRule.waitUntil(5_000L) { scrollFinished.get() }
        val listBounds = composeRule.onNodeWithTag("list").fetchSemanticsNode().boundsInRoot
        val itemBounds = composeRule.onNodeWithTag("item-50").fetchSemanticsNode().boundsInRoot
        val desiredOffsetPx = with(composeRule.density) { 121.5.dp.toPx() }
        assertEquals(desiredOffsetPx, itemBounds.top - listBounds.top, 1f)
    }
}
