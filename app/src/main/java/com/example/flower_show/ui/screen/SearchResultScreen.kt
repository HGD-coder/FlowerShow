package com.example.flower_show.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.flower_show.model.VideoItem
import com.example.flower_show.ui.component.SearchIcon
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.viewmodel.SearchIntent
import com.example.flower_show.viewmodel.SearchViewModel

@Composable
fun SearchResultScreen(
    keyword: String,
    onBack: () -> Unit,
    onResultClick: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember(keyword) { mutableStateOf(keyword) }

    LaunchedEffect(keyword) { viewModel.dispatch(SearchIntent.Search(keyword)) }

    fun submitSearch() {
        val trimmed = input.trim()
        if (trimmed.isNotEmpty()) {
            viewModel.dispatch(SearchIntent.Search(trimmed))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ArcticColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
    ) {
        SearchResultHeader(
            input = input,
            onInputChange = { input = it },
            onBack = onBack,
            onSubmit = ::submitSearch,
        )

        ResultFilterChips()

        when {
            state.isSearching -> ResultStatusText("\u641c\u7d22\u4e2d...")
            state.error != null -> ResultStatusText(state.error.orEmpty(), emphasis = true)
            state.results.isEmpty() -> ResultStatusText("\u6682\u65e0\u641c\u7d22\u7ed3\u679c")
            else -> {
                val videos = state.results.filterIsInstance<VideoItem>()
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(videos, key = { it.id }) { item ->
                        SearchResultRow(
                            video = item,
                            onClick = { onResultClick(item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchResultHeader(
    input: String,
    onInputChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "\u2039",
            color = ArcticColors.Primary,
            fontSize = 34.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier
                .clickable(onClick = onBack)
                .padding(end = 12.dp),
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .height(46.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(ArcticColors.Surface.copy(alpha = 0.92f))
                .border(1.dp, ArcticColors.Outline.copy(alpha = 0.70f), RoundedCornerShape(8.dp))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchIcon(tint = ArcticColors.Muted, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = ArcticColors.OnSurface,
                    fontSize = 16.sp,
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isBlank()) {
                            Text(
                                text = "\u641c\u7d22",
                                color = ArcticColors.Muted.copy(alpha = 0.58f),
                                fontSize = 16.sp,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                text = "\u641c\u7d22",
                color = ArcticColors.Primary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onSubmit)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun ResultFilterChips() {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        listOf(
            "\u7efc\u5408",
            "\u89c6\u9891",
            "\u7528\u6237",
            "\u76f8\u5173\u641c\u7d22",
        ).forEachIndexed { index, label ->
            val selected = index == 0
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (selected) ArcticColors.PrimaryContainer else ArcticColors.Surface)
                    .border(
                        1.dp,
                        if (selected) ArcticColors.PrimaryContainer else ArcticColors.Outline.copy(alpha = 0.70f),
                        RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 13.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (selected) ArcticColors.OnPrimary else ArcticColors.OnSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    video: VideoItem,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context).data(video.coverUrl).crossfade(true).build(),
            contentDescription = "\u7f29\u7565\u56fe",
            modifier = Modifier
                .size(width = 130.dp, height = 78.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(ArcticColors.SurfaceHigh),
            contentScale = ContentScale.Crop,
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = video.title,
                color = ArcticColors.OnSurface,
                fontSize = 15.sp,
                lineHeight = 20.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "@${video.author}",
                color = ArcticColors.Muted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                text = "${fmt(video.likes)}\u8d5e  \u00b7  ${fmt(video.comments)}\u8bc4\u8bba",
                color = ArcticColors.Muted.copy(alpha = 0.82f),
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
private fun ResultStatusText(
    text: String,
    emphasis: Boolean = false,
) {
    Text(
        text = text,
        color = if (emphasis) ArcticColors.PrimaryContainer else ArcticColors.Muted,
        fontSize = 15.sp,
        modifier = Modifier.padding(32.dp),
    )
}

private fun fmt(n: Int): String {
    if (n < 10000) return n.toString()
    val major = n / 10000
    val minor = (n % 10000) / 1000
    return if (minor == 0) "$major\u4e07" else "$major.$minor\u4e07"
}
