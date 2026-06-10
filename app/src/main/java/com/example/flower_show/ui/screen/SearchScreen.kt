package com.example.flower_show.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flower_show.ai.SearchSuggestionEngine
import com.example.flower_show.ui.component.SearchIcon
import com.example.flower_show.ui.theme.ArcticColors
import com.example.flower_show.viewmodel.SearchIntent
import com.example.flower_show.viewmodel.SearchViewModel

private const val HistoryCollapsedCount = 6
private const val GuessPageSize = 8

private val SearchPageBackground = ArcticColors.Background
private val SearchTextPrimary = ArcticColors.OnSurface
private val SearchTextSecondary = ArcticColors.Muted
private val SearchDivider = ArcticColors.Outline.copy(alpha = 0.55f)

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by rememberSaveable { mutableStateOf("") }
    var historyExpanded by rememberSaveable { mutableStateOf(false) }
    var guessPage by rememberSaveable { mutableIntStateOf(0) }

    fun submit(keyword: String) {
        val trimmed = keyword.trim()
        if (trimmed.isNotEmpty()) {
            onSearch(trimmed)
        }
    }

    val historyKeywords = if (historyExpanded) {
        state.history
    } else {
        state.history.take(HistoryCollapsedCount)
    }
    val guessKeywords = remember(state.history, guessPage) {
        SearchSuggestionEngine.guessSearches(
            history = state.history,
            page = guessPage,
            count = GuessPageSize,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SearchPageBackground)
            .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 14.dp),
    ) {
        SearchTopBar(
            input = input,
            onInputChange = { input = it },
            onBack = onBack,
            onSubmit = { submit(input) },
        )

        Spacer(Modifier.height(24.dp))

        KeywordSection(
            title = "历史记录",
            keywords = historyKeywords,
            emptyText = "暂无历史记录",
            modifier = Modifier.weight(1f),
            onKeywordClick = { submit(it) },
            actions = {
                if (state.history.size > HistoryCollapsedCount) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .height(40.dp)
                            .clickable { historyExpanded = !historyExpanded }
                            .padding(horizontal = 6.dp),
                    ) {
                        Text(
                            text = if (historyExpanded) "收起" else "展开",
                            color = SearchTextSecondary,
                            fontSize = 16.sp,
                        )
                        Spacer(Modifier.width(4.dp))
                        ChevronDownIcon(
                            tint = SearchTextSecondary,
                            size = 18.dp,
                            expanded = historyExpanded,
                        )
                    }
                    VerticalSeparator()
                }

                IconButton(
                    onClick = { viewModel.dispatch(SearchIntent.ClearHistory) },
                    enabled = state.history.isNotEmpty(),
                    modifier = Modifier.size(40.dp),
                ) {
                    TrashOutlineIcon(
                        tint = SearchTextSecondary.copy(alpha = if (state.history.isNotEmpty()) 1f else 0.35f),
                        size = 22.dp,
                    )
                }
            },
        )

        DividerLine()

        KeywordSection(
            title = "猜你想搜",
            keywords = guessKeywords,
            modifier = Modifier.weight(1f),
            onKeywordClick = { submit(it) },
            actions = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .height(40.dp)
                        .clickable { guessPage += 1 }
                        .padding(horizontal = 6.dp),
                ) {
                    RefreshIcon(tint = SearchTextSecondary, size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text("换一换", color = SearchTextSecondary, fontSize = 16.sp)
                }
                VerticalSeparator()
                IconButton(
                    onClick = { guessPage += 1 },
                    modifier = Modifier.size(40.dp),
                ) {
                    MoreVerticalIcon(tint = SearchTextSecondary, size = 22.dp)
                }
            },
        )
    }
}

@Composable
private fun SearchTopBar(
    input: String,
    onInputChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
    ) {
        IconButton(
            onClick = onBack,
            modifier = Modifier.size(48.dp),
        ) {
            BackChevronIcon(tint = SearchTextPrimary, size = 30.dp)
        }

        Spacer(Modifier.width(10.dp))

        val searchShape = RoundedCornerShape(8.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .weight(1f)
                .height(52.dp)
                .clip(searchShape)
                .background(ArcticColors.Surface.copy(alpha = 0.92f))
                .border(1.dp, ArcticColors.Outline.copy(alpha = 0.70f), searchShape)
                .padding(start = 18.dp, end = 8.dp),
        ) {
            SearchIcon(tint = SearchTextSecondary, size = 21.dp)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = input,
                onValueChange = onInputChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = SearchTextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Normal,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (input.isEmpty()) {
                            Text(
                                text = "搜索精彩内容",
                                color = SearchTextSecondary.copy(alpha = 0.56f),
                                fontSize = 18.sp,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                },
            )

            VerticalSeparator(height = 30.dp)

            Box(
                modifier = Modifier
                    .height(48.dp)
                    .width(70.dp)
                    .clickable { onSubmit() },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "搜索",
                    color = ArcticColors.Primary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun KeywordSection(
    title: String,
    keywords: List<String>,
    modifier: Modifier = Modifier,
    emptyText: String? = null,
    onKeywordClick: (String) -> Unit,
    actions: @Composable RowScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
        ) {
            Text(
                text = title,
                color = SearchTextSecondary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f),
            )
            actions()
        }

        Spacer(Modifier.height(12.dp))

        if (keywords.isEmpty()) {
            Text(
                text = emptyText.orEmpty(),
                color = SearchTextSecondary.copy(alpha = 0.7f),
                fontSize = 18.sp,
                modifier = Modifier.padding(top = 6.dp),
            )
        } else {
            KeywordGrid(
                keywords = keywords,
                onKeywordClick = onKeywordClick,
            )
        }
    }
}

@Composable
private fun KeywordGrid(
    keywords: List<String>,
    onKeywordClick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(15.dp)) {
        keywords.chunked(2).forEach { rowKeywords ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowKeywords.forEach { keyword ->
                    KeywordText(
                        keyword = keyword,
                        modifier = Modifier.weight(1f),
                        onClick = { onKeywordClick(keyword) },
                    )
                }
                if (rowKeywords.size == 1) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun KeywordText(
    keyword: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .heightIn(min = 34.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = keyword,
            color = SearchTextPrimary,
            fontSize = 18.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(SearchDivider),
    )
}

@Composable
private fun VerticalSeparator(height: Dp = 22.dp) {
    Box(
        modifier = Modifier
            .padding(horizontal = 8.dp)
            .width(1.dp)
            .height(height)
            .background(SearchDivider),
    )
}

@Composable
private fun BackChevronIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "返回",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun ChevronDownIcon(
    tint: Color,
    size: Dp,
    expanded: Boolean,
) {
    Icon(
        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
        contentDescription = if (expanded) "收起" else "展开",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun MoreVerticalIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.MoreVert,
        contentDescription = "更多",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun RefreshIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.Refresh,
        contentDescription = "刷新",
        tint = tint,
        modifier = Modifier.size(size),
    )
}

@Composable
private fun TrashOutlineIcon(
    tint: Color,
    size: Dp,
) {
    Icon(
        imageVector = Icons.Filled.DeleteOutline,
        contentDescription = "删除",
        tint = tint,
        modifier = Modifier.size(size),
    )
}
