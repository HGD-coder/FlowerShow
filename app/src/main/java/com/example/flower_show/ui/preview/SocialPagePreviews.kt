package com.example.flower_show.ui.preview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.RemoteChatMessageType
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.ui.screen.ChatScreen
import com.example.flower_show.ui.screen.ConnectionsScreen
import com.example.flower_show.ui.screen.MessageListScreen
import com.example.flower_show.ui.screen.MyProfileScreen
import com.example.flower_show.ui.screen.OtherProfileScreen
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.viewmodel.ChatState
import com.example.flower_show.viewmodel.SocialState

@Preview(name = "我的主页", showBackground = true, backgroundColor = 0xFF0B1326)
@Composable
private fun MyProfilePagePreview() {
    val state = remember { previewSocialState() }
    FlowerShowTheme {
        MyProfileScreen(
            profile = state.myProfile,
            selectedTab = state.selectedProfileTab,
            onTabSelected = {},
            onBack = {},
            onFollowingClick = {},
            onFollowersClick = {},
            onVideoClick = {},
            onWorkVisibilityChange = { _, _ -> },
        )
    }
}

@Preview(name = "他人主页", showBackground = true, backgroundColor = 0xFF0B1326)
@Composable
private fun OtherProfilePagePreview() {
    val friend = remember { previewFriend() }
    FlowerShowTheme {
        OtherProfileScreen(
            profile = friend,
            onBack = {},
            onFollowClick = {},
            onMessageClick = {},
            onVideoClick = {},
        )
    }
}

@Preview(name = "关注与粉丝", showBackground = true, backgroundColor = 0xFF0B1326)
@Composable
private fun ConnectionsPagePreview() {
    val state = remember { previewSocialState() }
    FlowerShowTheme {
        ConnectionsScreen(
            state = state,
            onBack = {},
            onTabSelected = {},
            onQueryChange = {},
            onProfileClick = {},
            onFollowClick = {},
        )
    }
}

@Preview(name = "消息列表", showBackground = true, backgroundColor = 0xFF0B1326)
@Composable
private fun MessagesPagePreview() {
    val socialState = remember { previewSocialState() }
    val chatState = remember { previewChatState() }
    FlowerShowTheme {
        MessageListScreen(
            state = socialState,
            chatState = chatState,
            onBack = {},
            onConversationClick = {},
        )
    }
}

@Preview(name = "聊天页", showBackground = true, backgroundColor = 0xFF0B1326)
@Composable
private fun ChatPagePreview() {
    val state = remember { previewChatState() }
    FlowerShowTheme {
        ChatScreen(
            conversationId = "preview-conversation",
            state = state,
            onBack = {},
            onDraftChange = {},
            onSend = {},
            onRetrySend = {},
            onLoadOlder = {},
            onRetryRoom = {},
            onStartRoom = {},
            onStopRoom = {},
            onGroupInfoClick = {},
            onVideoClick = {},
        )
    }
}

private fun previewSocialState(): SocialState {
    val friend = previewFriend()
    val me = SocialProfile(
        id = "preview-me",
        account = "flowershow",
        nickname = "北屿",
        bio = "记录花、风和每一个值得留住的瞬间。",
        region = "上海",
        avatarUrl = "",
        worksCount = 0,
        followingCount = 1,
        followersCount = 8_642,
        likesReceivedCount = 126_000,
    )
    return SocialState(
        myProfile = me,
        people = listOf(friend),
        relationProfiles = listOf(friend),
    )
}

private fun previewFriend() = SocialProfile(
    id = "preview-friend",
    account = "linwu_daily",
    nickname = "林雾",
    bio = "去山里，也去生活里。",
    region = "杭州",
    avatarUrl = "",
    worksCount = 12,
    followingCount = 96,
    followersCount = 12_480,
    likesReceivedCount = 368_000,
    isFollowing = true,
    followsMe = true,
)

private fun previewChatState(): ChatState {
    val message = RemoteChatMessage(
        messageId = "preview-message",
        conversationId = "preview-conversation",
        senderUserId = "preview-friend",
        senderNickname = "林雾",
        senderAvatarUrl = null,
        type = RemoteChatMessageType.TEXT,
        text = "明天一起去拍花吗？",
        sharedContentId = null,
        videoPreview = null,
        clientMessageId = "preview-client-message",
        createdAt = "10:32",
    )
    val detail = ChatConversationDetail(
        conversationId = "preview-conversation",
        type = ChatConversationType.DIRECT,
        name = null,
        ownerUserId = null,
        state = ChatConversationState.ACTIVE,
        dissolvedAt = null,
        dissolvedByUserId = null,
        members = listOf(
            ChatMember(
                userId = "preview-me",
                nickname = "北屿",
                avatarUrl = null,
                role = ChatMemberRole.MEMBER,
                joinedAt = "2026-07-27T00:00:00Z",
            ),
            ChatMember(
                userId = "preview-friend",
                nickname = "林雾",
                avatarUrl = null,
                role = ChatMemberRole.MEMBER,
                joinedAt = "2026-07-27T00:00:00Z",
            ),
        ),
        createdAt = "2026-07-27T00:00:00Z",
        updatedAt = "2026-07-27T10:32:00Z",
    )
    return ChatState(
        currentUserId = "preview-me",
        conversations = listOf(
            ChatConversationSummary(
                conversationId = detail.conversationId,
                type = detail.type,
                name = detail.name,
                displayName = "林雾",
                displayAvatarUrl = null,
                ownerUserId = null,
                state = detail.state,
                dissolvedAt = null,
                lastMessage = message,
                unreadCount = 1,
                updatedAt = "10:32",
                avatarMembers = emptyList(),
            ),
        ),
        activeConversationId = detail.conversationId,
        activeConversation = detail,
        messages = listOf(message),
        messagesHasMore = false,
    )
}
