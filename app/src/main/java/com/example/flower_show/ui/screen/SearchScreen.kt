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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.flower_show.ui.component.CloseIcon
import com.example.flower_show.ui.component.SearchIcon
import com.example.flower_show.viewmodel.SearchIntent
import com.example.flower_show.viewmodel.SearchViewModel

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onSearch: (String) -> Unit,
    viewModel: SearchViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var input by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1A1A1A)).padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(22.dp))
                    .background(Color.White.copy(alpha = 0.12f)),
                contentAlignment = Alignment.CenterStart,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
                    SearchIcon(tint = Color.White.copy(alpha = 0.5f), size = 18.dp)
                    Spacer(Modifier.width(8.dp))
                    TextField(
                        value = input, onValueChange = { input = it },
                        placeholder = { Text("搜索精彩内容", color = Color.White.copy(alpha = 0.4f)) },
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
                            if (input.isNotBlank()) { viewModel.dispatch(SearchIntent.Search(input)); onSearch(input) }
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Text("取消", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.clickable(onClick = onBack))
        }

        Spacer(Modifier.height(20.dp))

        if (state.history.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("搜索历史", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
                CloseIcon(
                    tint = Color.White.copy(alpha = 0.5f), size = 18.dp,
                    onClick = { viewModel.dispatch(SearchIntent.ClearHistory) },
                )
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn {
                items(state.history) { kw ->
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.dispatch(SearchIntent.Search(kw)); onSearch(kw) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SearchIcon(tint = Color.White.copy(alpha = 0.4f), size = 16.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(kw, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, modifier = Modifier.weight(1f))
                        Text("✕", color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp,
                            modifier = Modifier.clickable { viewModel.dispatch(SearchIntent.DeleteHistory(kw)) })
                    }
                }
            }
        }
    }
}
