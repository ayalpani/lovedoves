package com.yalpani.lovedoves.ui

import android.content.Context
import android.icu.lang.UCharacter
import android.util.TypedValue
import android.view.ContextThemeWrapper
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.emoji2.emojipicker.EmojiPickerView
import androidx.emoji2.emojipicker.RecentEmojiProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.yalpani.lovedoves.LoveInk
import com.yalpani.lovedoves.LoveMist
import com.yalpani.lovedoves.R
import androidx.emoji2.emojipicker.R as EmojiPickerResources
import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    var searchQuery by remember { mutableStateOf("") }
    val context = LocalContext.current
    val searchEntries by produceState(emptyList<EmojiSearchEntry>(), context) {
        value = withContext(Dispatchers.Default) { loadEmojiSearchEntries(context) }
    }
    val searchResults = remember(searchQuery, searchEntries) {
        val tokens = normalizeSearchText(searchQuery).split(' ').filter(String::isNotBlank)
        if (tokens.isEmpty()) emptyList() else searchEntries.filter { entry ->
            tokens.all(entry.keywords::contains)
        }
    }
    val currentOnEmojiPicked = rememberUpdatedState(onEmojiPicked)
    Column(modifier.fillMaxWidth().background(Color.White)) {
        EmojiSearchField(searchQuery, onQueryChange = { searchQuery = it })
        if (searchQuery.isBlank()) {
            AndroidView(
                factory = { viewContext ->
                    val pickerContext = ContextThemeWrapper(viewContext, R.style.Theme_LoveDoves)
                    EmojiPickerView(pickerContext).apply {
                        emojiGridColumns = EMOJI_COLUMNS
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                        setRecentEmojiProvider(LoveDovesRecentEmojiProvider)
                        setOnEmojiPickedListener { currentOnEmojiPicked.value(it.emoji) }
                        installCompactEmojiRendering()
                    }
                },
                update = { picker ->
                    picker.setOnEmojiPickedListener { currentOnEmojiPicked.value(it.emoji) }
                },
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
        } else if (searchResults.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    "Kein Emoji gefunden",
                    color = LoveInk.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(EMOJI_COLUMNS),
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(
                    horizontal = EMOJI_GRID_HORIZONTAL_PADDING_DP.dp,
                    vertical = EMOJI_GRID_VERTICAL_PADDING_DP.dp,
                ),
            ) {
                items(searchResults, key = EmojiSearchEntry::emoji) { entry ->
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clickable { currentOnEmojiPicked.value(entry.emoji) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(entry.emoji, fontSize = SEARCH_EMOJI_SIZE)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Surface(
        color = LoveMist,
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .height(40.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SearchIcon(modifier = Modifier.size(19.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = LoveInk),
                cursorBrush = SolidColor(LoveInk),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                decorationBox = { innerTextField ->
                    Box {
                        if (query.isEmpty()) {
                            Text(
                                "Emojis suchen",
                                color = LoveInk.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (query.isNotEmpty()) {
                IconButton(
                    onClick = { onQueryChange("") },
                    modifier = Modifier.size(34.dp),
                ) {
                    CloseIcon("Suche löschen", modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

private fun EmojiPickerView.installCompactEmojiRendering() {
    var installed = false
    fun installWhenReady() {
        if (installed) return
        val body = findViewById<RecyclerView>(EmojiPickerResources.id.emoji_picker_body) ?: return
        val header = findViewById<RecyclerView>(EmojiPickerResources.id.emoji_picker_header) ?: return
        val headerAdapter = header.adapter ?: return
        installed = true
        fun compact(view: View) {
            if (view.javaClass.name == EMOJI_VIEW_CLASS) {
                view.scaleX = NATIVE_EMOJI_SCALE
                view.scaleY = NATIVE_EMOJI_SCALE
            }
            view.findViewById<TextView?>(EmojiPickerResources.id.category_name)?.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                CATEGORY_TITLE_SIZE_SP,
            )
        }
        val horizontalPadding =
            (EMOJI_GRID_HORIZONTAL_PADDING_DP * resources.displayMetrics.density).roundToInt()
        val verticalPadding =
            (EMOJI_GRID_VERTICAL_PADDING_DP * resources.displayMetrics.density).roundToInt()
        body.setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        header.layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
        header.itemAnimator = null
        val categoryTabWidth = resources.getDimensionPixelSize(
            EmojiPickerResources.dimen.emoji_picker_header_icon_holder_width,
        )
        val categoryIconSize = resources.getDimensionPixelSize(
            EmojiPickerResources.dimen.emoji_picker_header_icon_width,
        )
        fun fixCategoryTabSize(view: View) {
            view.layoutParams = view.layoutParams.apply { width = categoryTabWidth }
            view.findViewById<View>(EmojiPickerResources.id.emoji_picker_header_icon)?.let { icon ->
                icon.layoutParams = icon.layoutParams.apply {
                    width = categoryIconSize
                    height = categoryIconSize
                }
                icon.scaleX = 1f
                icon.scaleY = 1f
            }
        }
        header.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = fixCategoryTabSize(view)
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            },
        )
        for (index in 0 until header.childCount) fixCategoryTabSize(header.getChildAt(index))
        header.addOnItemTouchListener(
            object : RecyclerView.SimpleOnItemTouchListener() {
                override fun onInterceptTouchEvent(recyclerView: RecyclerView, event: MotionEvent): Boolean {
                    if (event.actionMasked == MotionEvent.ACTION_DOWN) body.stopScroll()
                    return false
                }
            },
        )
        var selectedCategory = 0
        val keepSelectedCategoryVisible = Runnable {
            val layoutManager = header.layoutManager as? LinearLayoutManager ?: return@Runnable
            if (selectedCategory !in
                layoutManager.findFirstCompletelyVisibleItemPosition()..
                layoutManager.findLastCompletelyVisibleItemPosition()
            ) {
                header.smoothScrollToPosition(selectedCategory)
            }
        }
        headerAdapter.registerAdapterDataObserver(
            object : RecyclerView.AdapterDataObserver() {
                override fun onItemRangeChanged(positionStart: Int, itemCount: Int) {
                    selectedCategory = positionStart
                    header.removeCallbacks(keepSelectedCategoryVisible)
                    header.post(keepSelectedCategoryVisible)
                }
            },
        )
        body.addOnChildAttachStateChangeListener(
            object : RecyclerView.OnChildAttachStateChangeListener {
                override fun onChildViewAttachedToWindow(view: View) = compact(view)
                override fun onChildViewDetachedFromWindow(view: View) = Unit
            },
        )
        for (index in 0 until body.childCount) compact(body.getChildAt(index))
    }
    setOnHierarchyChangeListener(
        object : ViewGroup.OnHierarchyChangeListener {
            override fun onChildViewAdded(parent: View?, child: View?) {
                post(::installWhenReady)
            }

            override fun onChildViewRemoved(parent: View?, child: View?) = Unit
        },
    )
    post(::installWhenReady)
}

private data class EmojiSearchEntry(
    val emoji: String,
    val keywords: String,
)

private data class EmojiResourceGroup(
    val resourceId: Int,
    val keywords: String,
)

private fun loadEmojiSearchEntries(context: Context): List<EmojiSearchEntry> {
    val groups = listOf(
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_emotions, "smiley gefuehl emotion"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_people, "mensch person leute hand koerper"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_animals_nature, "tier natur pflanze animal nature"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_food_drink, "essen trinken food drink"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_travel_places, "reise ort verkehr travel place"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_activity, "aktivitaet sport spiel activity"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_objects, "objekt ding object"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_symbols, "symbol zeichen"),
        EmojiResourceGroup(EmojiPickerResources.raw.emoji_category_flags, "flagge land flag country"),
    )
    return groups.flatMap { group ->
        context.resources.openRawResource(group.resourceId)
            .bufferedReader()
            .useLines { lines ->
                lines.mapNotNull { line ->
                    line.substringBefore(',').trim().takeIf(String::isNotEmpty)?.let { emoji ->
                        EmojiSearchEntry(
                            emoji = emoji,
                            keywords = normalizeSearchText(
                                "$emoji ${unicodeNames(emoji)} ${group.keywords} ${commonAliases(emoji)}",
                            ),
                        )
                    }
                }.toList()
            }
    }.distinctBy(EmojiSearchEntry::emoji)
}

private fun unicodeNames(emoji: String): String = buildString {
    emoji.codePoints().forEach { codePoint ->
        if (codePoint != ZERO_WIDTH_JOINER && codePoint != VARIATION_SELECTOR) {
            UCharacter.getName(codePoint)?.let {
                if (isNotEmpty()) append(' ')
                append(it)
            }
        }
    }
}

private fun commonAliases(emoji: String): String = when (emoji) {
    "❤", "❤️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟" -> "herz liebe verliebt heart love"
    "😘", "😗", "😙", "😚", "💋" -> "kuss kuessen liebe kiss love"
    "😀", "😃", "😄", "😁", "😊", "🙂" -> "lachen gluecklich froh smile happy"
    "😂", "🤣" -> "traenen lachen lustig tears laugh funny"
    "😭", "😢", "😥", "😞", "😔", "☹️", "🙁" -> "weinen traurig traenen cry sad tears"
    "🥰", "😍", "🤩" -> "liebe verliebt begeistert love heart eyes"
    "👍" -> "ja gut daumen hoch yes good like"
    "👎" -> "nein schlecht daumen runter no bad dislike"
    "🙏" -> "danke bitte beten thank please pray"
    "🎉", "🥳", "🎊" -> "party feiern glueckwunsch celebrate congratulations"
    "🔥" -> "feuer heiss flame fire hot"
    "⭐", "🌟", "✨" -> "stern funkeln star sparkle"
    "☀️", "🌞" -> "sonne sonnig sun sunny"
    "🌙", "🌛", "🌜", "🌚", "🌝" -> "mond nacht moon night"
    "🐶", "🐕" -> "hund dog"
    "🐱", "🐈" -> "katze cat"
    "📷", "📸", "🎥" -> "kamera foto video camera photo"
    "🎵", "🎶", "🎼", "🎧" -> "musik lied music song"
    else -> ""
}

private fun normalizeSearchText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFD)
    .replace(COMBINING_MARKS, "")
    .lowercase(Locale.ROOT)
    .replace("ß", "ss")

private const val EMOJI_COLUMNS = 8
private const val EMOJI_GRID_HORIZONTAL_PADDING_DP = 10
private const val EMOJI_GRID_VERTICAL_PADDING_DP = 4
private const val EMOJI_VIEW_CLASS = "androidx.emoji2.emojipicker.EmojiView"
private const val NATIVE_EMOJI_SCALE = 0.8f
private const val CATEGORY_TITLE_SIZE_SP = 12f
private val SEARCH_EMOJI_SIZE = 24.sp
private const val ZERO_WIDTH_JOINER = 0x200D
private const val VARIATION_SELECTOR = 0xFE0F
private val COMBINING_MARKS = "\\p{M}+".toRegex()
