package com.example.flower_show.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.flower_show.model.ChatAvatarMember
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.RemoteChatMessageType
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.ui.component.GroupAvatar
import com.example.flower_show.ui.component.SocialAvatar
import com.example.flower_show.ui.component.SocialDimens
import com.example.flower_show.ui.component.SocialSearchField
import com.example.flower_show.ui.component.SocialTopBar
import com.example.flower_show.viewmodel.ChatShareComposerState
import com.example.flower_show.viewmodel.ChatState
import com.example.flower_show.viewmodel.MessageListTab
import com.example.flower_show.viewmodel.SocialState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(
    state: SocialState,
    chatState: ChatState,
    onBack: () -> Unit,
    onConversationClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    onTabSelected: (MessageListTab) -> Unit = {},
    onNotificationClick: (String) -> Unit = {},
    onMarkAllNotificationsRead: () -> Unit = {},
    onLoadMoreNotifications: () -> Unit = {},
    onLoadMoreConversations: () -> Unit = {},
    onRetryConversations: () -> Unit = {},
    onCreateGroup: () -> Unit = {},
    onStartPolling: () -> Unit = {},
    onStopPolling: () -> Unit = {},
) {
    DisposableEffect(Unit) {
        onStartPolling()
        onDispose(onStopPolling)
    }

    Scaffold(
        modifier = modifier.testTag("message_list_screen"),
        topBar = {
            SocialTopBar(
                title = "消息",
                onBack = onBack,
                trailing = {
                    when {
                        state.selectedMessageTab == MessageListTab.Notifications &&
                            state.notificationUnreadCount > 0 -> {
                            TextButton(onClick = onMarkAllNotificationsRead) {
                                Text("全部已读")
                            }
                        }
                        state.selectedMessageTab == MessageListTab.Chats -> {
                            IconButton(
                                onClick = onCreateGroup,
                                modifier = Modifier.testTag("create_group_button"),
                            ) {
                                Icon(Icons.Default.GroupAdd, contentDescription = "创建群聊")
                            }
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = state.selectedMessageTab.ordinal) {
                MessageListTab.entries.forEach { tab ->
                    Tab(
                        selected = tab == state.selectedMessageTab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.testTag("message_tab_${tab.name.lowercase()}"),
                        text = {
                            val title = when (tab) {
                                MessageListTab.Notifications -> "通知"
                                MessageListTab.Chats -> "聊天"
                            }
                            Text(
                                if (
                                    tab == MessageListTab.Notifications &&
                                    state.notificationUnreadCount > 0
                                ) {
                                    "$title (${state.notificationUnreadCount})"
                                } else {
                                    title
                                },
                            )
                        },
                    )
                }
            }

            when (state.selectedMessageTab) {
                MessageListTab.Notifications -> NotificationList(
                    notifications = state.notifications,
                    loading = state.isNotificationsLoading,
                    hasMore = state.notificationsHasMore,
                    onNotificationClick = onNotificationClick,
                    onLoadMore = onLoadMoreNotifications,
                    modifier = Modifier.weight(1f),
                )
                MessageListTab.Chats -> ConversationList(
                    state = chatState,
                    onConversationClick = onConversationClick,
                    onLoadMore = onLoadMoreConversations,
                    onRetry = onRetryConversations,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun ConversationList(
    state: ChatState,
    onConversationClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.isLoadingConversations && state.conversations.isEmpty() -> {
            StatusPane("会话加载中…", loading = true, modifier = modifier)
        }
        state.conversationError != null && state.conversations.isEmpty() -> {
            StatusPane(
                text = state.conversationError,
                actionLabel = "重试",
                onAction = onRetry,
                modifier = modifier,
            )
        }
        state.conversations.isEmpty() -> {
            StatusPane("还没有会话，去找互关朋友聊聊吧", modifier = modifier)
        }
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            items(
                items = state.conversations,
                key = ChatConversationSummary::conversationId,
            ) { conversation ->
                ConversationRow(
                    conversation = conversation,
                    onClick = { onConversationClick(conversation.conversationId) },
                    modifier = Modifier.testTag("conversation_${conversation.conversationId}"),
                )
                HorizontalDivider(modifier = Modifier.padding(start = 84.dp))
            }
            if (state.conversationError != null) {
                item("conversation_error") {
                    InlineError(
                        message = state.conversationError,
                        onRetry = onRetry,
                    )
                }
            }
            if (state.conversationsHasMore) {
                item("conversation_load_more") {
                    TextButton(
                        onClick = onLoadMore,
                        enabled = !state.isLoadingMoreConversations,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("conversations_load_more"),
                    ) {
                        Text(if (state.isLoadingMoreConversations) "加载中…" else "加载更多")
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(
    conversation: ChatConversationSummary,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = conversation.displayName
        ?.takeIf(String::isNotBlank)
        ?: conversation.name?.takeIf(String::isNotBlank)
        ?: if (conversation.type == ChatConversationType.GROUP) "群聊" else "私信"
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = SocialDimens.spaceMd, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ConversationAvatar(conversation, title)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = conversation.updatedAt,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            Spacer(Modifier.height(SocialDimens.spaceXs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conversation.previewText(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (conversation.unreadCount > 0) {
                    Spacer(Modifier.width(SocialDimens.spaceSm))
                    Badge(
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    ) {
                        Text(conversation.unreadCount.coerceAtMost(99).toString())
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationAvatar(
    conversation: ChatConversationSummary,
    title: String,
) {
    if (conversation.type == ChatConversationType.GROUP) {
        GroupAvatar(
            displayAvatarUrl = conversation.displayAvatarUrl,
            avatarMembers = conversation.avatarMembers,
            displayName = title,
            size = 56.dp,
            testTag = "group_avatar_${conversation.conversationId}",
        )
        return
    }

    Surface(
        modifier = Modifier.size(56.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (!conversation.displayAvatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = conversation.displayAvatarUrl,
                contentDescription = "${title}头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = title.take(1),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun NotificationList(
    notifications: List<SocialNotification>,
    loading: Boolean,
    hasMore: Boolean,
    onNotificationClick: (String) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (notifications.isEmpty()) {
        StatusPane(
            text = if (loading) "通知加载中…" else "暂无通知",
            loading = loading,
            modifier = modifier,
        )
        return
    }
    LazyColumn(modifier = modifier.fillMaxSize()) {
        items(notifications, key = SocialNotification::id) { notification ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onNotificationClick(notification.id) }
                    .testTag("notification_${notification.id}")
                    .background(
                        if (notification.isRead) Color.Transparent
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.18f),
                    )
                    .padding(horizontal = SocialDimens.spaceMd, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = notification.actorNickname?.takeIf(String::isNotBlank)
                            ?: "系统通知",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = notification.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (notification.createdAt.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = notification.createdAt,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (!notification.isRead) {
                    Spacer(Modifier.width(8.dp))
                    Badge()
                }
            }
            HorizontalDivider()
        }
        if (hasMore) {
            item("notifications_load_more") {
                TextButton(
                    onClick = onLoadMore,
                    enabled = !loading,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notifications_load_more"),
                ) {
                    Text(if (loading) "加载中…" else "加载更多")
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    conversationId: String,
    state: ChatState,
    onBack: () -> Unit,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onRetrySend: () -> Unit,
    onLoadOlder: () -> Unit,
    onRetryRoom: () -> Unit,
    onStartRoom: (String) -> Unit,
    onStopRoom: (String) -> Unit,
    onGroupInfoClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(conversationId) {
        onStartRoom(conversationId)
        onDispose { onStopRoom(conversationId) }
    }

    val listState = rememberLazyListState()
    val lastMessageId = state.messages.lastOrNull()?.messageId
    val messageHeaderCount = 1 +
        (if (state.isRoomDissolved) 1 else 0) +
        (if (state.roomError != null) 1 else 0)
    // 只有首屏加载、或用户停留在列表末尾时才自动跟随新消息；
    // 用户往上翻历史时新消息到达不再强行拽回底部。
    var previousMessageCount by remember { mutableIntStateOf(0) }
    LaunchedEffect(lastMessageId) {
        if (lastMessageId == null) {
            // 会话切换清空消息时重置计数，让新会话首屏加载仍能滚到底部。
            previousMessageCount = 0
            return@LaunchedEffect
        }
        val countBefore = previousMessageCount
        previousMessageCount = state.messages.size
        val firstLoad = countBefore == 0
        val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val previousLastIndex = countBefore - 1 + messageHeaderCount
        if (firstLoad || lastVisibleIndex >= previousLastIndex) {
            listState.scrollToItem(state.messages.lastIndex + messageHeaderCount)
        }
    }
    val detail = state.activeConversation?.takeIf { it.conversationId == conversationId }
    val summary = state.conversations.firstOrNull { it.conversationId == conversationId }
    val title = summary?.displayName
        ?.takeIf(String::isNotBlank)
        ?: detail?.name?.takeIf(String::isNotBlank)
        ?: detail?.members
            ?.firstOrNull { it.userId != state.currentUserId }
            ?.nickname
        ?: "聊天"
    val isGroup = detail?.type == ChatConversationType.GROUP ||
        summary?.type == ChatConversationType.GROUP
    val groupAvatarMembers = summary?.avatarMembers
        ?.takeIf(List<ChatAvatarMember>::isNotEmpty)
        ?: detail?.members.orEmpty()
            .take(4)
            .map { member ->
                ChatAvatarMember(
                    userId = member.userId,
                    nickname = member.nickname,
                    avatarUrl = member.avatarUrl,
                )
            }

    Scaffold(
        modifier = modifier
            .testTag("chat_screen")
            .imePadding(),
        topBar = {
            SocialTopBar(
                title = title,
                onBack = onBack,
                titleLeading = if (isGroup) {
                    {
                        GroupAvatar(
                            displayAvatarUrl = summary?.displayAvatarUrl,
                            avatarMembers = groupAvatarMembers,
                            displayName = title,
                            size = 36.dp,
                            testTag = "group_chat_avatar",
                        )
                    }
                } else {
                    null
                },
                trailing = {
                    if (isGroup) {
                        IconButton(
                            onClick = { onGroupInfoClick(conversationId) },
                            modifier = Modifier.testTag("group_info_button"),
                        ) {
                            Icon(Icons.Default.Info, contentDescription = "群资料")
                        }
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                value = state.draft,
                onValueChange = onDraftChange,
                onSend = onSend,
                enabled = state.canSendMessage && state.outgoingText == null,
                dissolved = state.isRoomDissolved,
                outgoingError = state.outgoingText?.error,
                onRetry = onRetrySend,
            )
        },
    ) { contentPadding ->
        when {
            state.isLoadingRoom && state.messages.isEmpty() -> {
                StatusPane(
                    "消息加载中…",
                    loading = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }
            state.roomError != null && state.messages.isEmpty() -> {
                StatusPane(
                    text = state.roomError,
                    actionLabel = "重试",
                    onAction = onRetryRoom,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                )
            }
            else -> LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(horizontal = SocialDimens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
            ) {
                item("older_messages") {
                    when {
                        state.messagesHasMore -> TextButton(
                            onClick = onLoadOlder,
                            enabled = !state.isLoadingOlderMessages,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("messages_load_older"),
                        ) {
                            Text(
                                if (state.isLoadingOlderMessages) "加载中…"
                                else "加载更早消息",
                            )
                        }
                        state.messages.isNotEmpty() -> Text(
                            text = "没有更早消息了",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp),
                        )
                    }
                }
                if (state.isRoomDissolved) {
                    item("dissolved_notice") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "群聊已解散，仅可查看历史消息",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                if (state.roomError != null) {
                    item("room_inline_error") {
                        InlineError(state.roomError, onRetryRoom)
                    }
                }
                if (state.messages.isEmpty() && !state.isLoadingRoom) {
                    item("empty_messages") {
                        Text(
                            text = "暂无消息，来打个招呼吧",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                        )
                    }
                }
                items(
                    items = state.messages,
                    key = RemoteChatMessage::messageId,
                ) { message ->
                    RemoteMessageRow(
                        message = message,
                        currentUserId = state.currentUserId,
                        showSenderName = isGroup,
                        onVideoClick = onVideoClick,
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteMessageRow(
    message: RemoteChatMessage,
    currentUserId: String,
    showSenderName: Boolean,
    onVideoClick: (String) -> Unit,
) {
    val mine = message.senderUserId == currentUserId
    val senderName = message.senderNickname
        ?.takeIf(String::isNotBlank)
        ?: if (showSenderName) "群成员" else "对方"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!mine) {
            MessageAvatar(
                nickname = senderName,
                avatarUrl = message.senderAvatarUrl,
                testTag = "chat_sender_avatar_${message.messageId}",
            )
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier.fillMaxWidth(0.78f),
        ) {
            if (!mine && showSenderName) {
                Text(
                    text = senderName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 2.dp, bottom = 3.dp)
                        .testTag("chat_sender_name_${message.messageId}"),
                )
            }
            when (message.type) {
                RemoteChatMessageType.TEXT -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (mine) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            text = message.text.orEmpty(),
                            color = if (mine) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                                .testTag("chat_text_${message.messageId}"),
                        )
                    }
                }
                RemoteChatMessageType.VIDEO_SHARE -> {
                    RemoteVideoShareCard(
                        message = message,
                        onVideoClick = onVideoClick,
                    )
                }
                RemoteChatMessageType.UNKNOWN -> {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            "暂不支持的消息",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(12.dp),
                        )
                    }
                }
            }
            Text(
                text = message.createdAt,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
private fun MessageAvatar(
    nickname: String,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    testTag: String = "message_avatar",
) {
    Surface(
        modifier = Modifier
            .size(34.dp)
            .then(modifier)
            .semantics { contentDescription = "${nickname}头像" }
            .testTag(testTag),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("${testTag}_image"),
            )
        } else {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = nickname.trim().take(1).ifBlank { "聊" },
                    modifier = Modifier.testTag("${testTag}_initial"),
                )
            }
        }
    }
}

@Composable
private fun RemoteVideoShareCard(
    message: RemoteChatMessage,
    onVideoClick: (String) -> Unit,
) {
    val preview = message.videoPreview
    val contentId = preview?.contentId ?: message.sharedContentId
    val cover = preview?.coverThumbnailUrl ?: preview?.coverUrl
    Surface(
        modifier = Modifier
            .width(250.dp)
            .clickable(enabled = !contentId.isNullOrBlank()) {
                contentId?.let(onVideoClick)
            }
            .testTag("chat_video_${contentId.orEmpty()}"),
        shape = RoundedCornerShape(8.dp),
        tonalElevation = 3.dp,
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(132.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (!cover.isNullOrBlank()) {
                    AsyncImage(
                        model = cover,
                        contentDescription = "${preview?.title.orEmpty()}封面",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Surface(shape = CircleShape, color = Color.Black.copy(alpha = 0.56f)) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "播放视频",
                        tint = Color.White,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            }
            Text(
                text = preview?.title?.takeIf(String::isNotBlank) ?: "分享的视频",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun ChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    enabled: Boolean,
    dissolved: Boolean,
    outgoingError: String?,
    onRetry: () -> Unit,
) {
    Surface(tonalElevation = 4.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = SocialDimens.spaceSm, vertical = SocialDimens.spaceSm),
        ) {
            if (dissolved) {
                Text(
                    "群聊已解散，发送功能已关闭",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (outgoingError != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = outgoingError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(
                        onClick = onRetry,
                        modifier = Modifier.testTag("chat_retry_button"),
                    ) {
                        Text("重试")
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled && !dissolved,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_input"),
                    placeholder = { Text(if (dissolved) "群聊已解散" else "发消息…") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                )
                Spacer(Modifier.width(SocialDimens.spaceSm))
                IconButton(
                    onClick = onSend,
                    enabled = enabled && !dissolved && value.isNotBlank(),
                    modifier = Modifier.testTag("chat_send_button"),
                ) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                }
            }
        }
    }
}

@Composable
fun ShareToFriendsDialog(
    state: ChatShareComposerState,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onFriendSelected: (String) -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(onDismissRequest = { if (!state.isSending) onDismiss() }) {
        Surface(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(max = 620.dp)
                .testTag("share_friends_dialog"),
            shape = RoundedCornerShape(8.dp),
            tonalElevation = 8.dp,
        ) {
            Column(modifier = Modifier.padding(SocialDimens.spaceMd)) {
                Text(
                    text = "分享给朋友",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(SocialDimens.spaceSm))
                Text(
                    text = state.video.title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(SocialDimens.spaceMd))
                SocialSearchField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    placeholder = "搜索互关朋友",
                    testTag = "share_friend_search_input",
                )
                Spacer(Modifier.height(SocialDimens.spaceSm))
                when {
                    state.isLoadingCandidates -> {
                        StatusPane(
                            "联系人加载中…",
                            loading = true,
                            modifier = Modifier.height(160.dp),
                        )
                    }
                    state.candidates.isEmpty() -> {
                        StatusPane(
                            state.error ?: "没有找到互关朋友",
                            modifier = Modifier.height(160.dp),
                        )
                    }
                    else -> LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(state.candidates, key = SocialProfile::id) { friend ->
                            ContactSelectionRow(
                                friend = friend,
                                selected = state.selectedUserId == friend.id,
                                onClick = { onFriendSelected(friend.id) },
                                testTag = "share_friend_${friend.id}",
                            )
                        }
                    }
                }
                if (state.error != null && state.candidates.isNotEmpty()) {
                    Text(
                        text = state.error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(SocialDimens.spaceMd))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SocialDimens.spaceSm),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !state.isSending,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = onSend,
                        enabled = state.selectedUserId != null && !state.isSending,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("share_send_button"),
                    ) {
                        Text(
                            when {
                                state.isSending -> "发送中…"
                                state.error != null && state.clientMessageId != null -> "重试"
                                else -> "发送"
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun ContactSelectionRow(
    friend: SocialProfile,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
            )
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SocialAvatar(profile = friend, size = 44.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(friend.nickname, fontWeight = FontWeight.SemiBold)
            Text(
                text = "@${friend.account} · 互关",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun StatusPane(
    text: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (loading) CircularProgressIndicator(modifier = Modifier.size(28.dp))
            Text(
                text = text,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (actionLabel != null) {
                Button(onClick = onAction, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
private fun InlineError(
    message: String,
    onRetry: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onRetry) { Text("重试") }
    }
}

private fun ChatConversationSummary.previewText(): String {
    if (state == ChatConversationState.DISSOLVED) return "群聊已解散"
    val message = lastMessage ?: return "暂无消息"
    val content = when (message.type) {
        RemoteChatMessageType.TEXT -> message.text.orEmpty()
        RemoteChatMessageType.VIDEO_SHARE -> "[视频] ${
            message.videoPreview?.title?.takeIf(String::isNotBlank) ?: "视频分享"
        }"
        RemoteChatMessageType.UNKNOWN -> "[不支持的消息]"
    }
    val sender = message.senderNickname?.takeIf(String::isNotBlank)
    return if (type == ChatConversationType.GROUP && sender != null) {
        "$sender: $content"
    } else {
        content
    }
}
