package com.example.flower_show.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.*
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
    val context = LocalContext.current
    var input by remember(keyword) { mutableStateOf(keyword) }

    // Re-search when keyword changes
    LaunchedEffect(keyword) { viewModel.dispatch(SearchIntent.Search(keyword)) }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A))) {
        // Top bar: back + editable search input (same structure as SearchScreen)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("←", color = Color.White, fontSize = 20.sp,
                modifier = Modifier.clickable(onClick = onBack).padding(end = 8.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 16.dp)
                ) {
                    SearchIcon(tint = Color.White.copy(alpha = 0.5f), size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text("搜索", color = Color.White.copy(alpha = 0.4f)) },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            cursorColor = Color.White,
                            focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            if (input.isNotBlank()) viewModel.dispatch(SearchIntent.Search(input))
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        // Results / 搜索结果
        when {
            state.isSearching -> Text("搜索中...", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(32.dp))
            state.error != null -> Text(state.error!!, color = Color.Red, modifier = Modifier.padding(32.dp))
            state.results.isEmpty() -> Text("暂无搜索结果", color = Color.White.copy(alpha = 0.5f), modifier = Modifier.padding(32.dp))
            else -> LazyColumn {
                items(state.results) { item ->
                    if (item is VideoItem) {
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onResultClick(item.id) }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = ImageRequest.Builder(context).data(item.coverUrl).crossfade(true).build(),
                                contentDescription = "缩略图",
                                modifier = Modifier.size(130.dp, 78.dp).clip(RoundedCornerShape(6.dp)),
                                contentScale = ContentScale.Crop,
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.title, color = Color.White, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.height(4.dp))
                                Text("@${item.author}", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                                Spacer(Modifier.height(4.dp))
                                Text("${fmt(item.likes)}赞 · ${fmt(item.comments)}评论", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun fmt(n: Int): String = if (n >= 10000) "${n / 10000}.${(n % 10000) / 1000}万" else n.toString()
