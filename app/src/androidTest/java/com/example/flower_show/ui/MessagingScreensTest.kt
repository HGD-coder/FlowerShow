package com.example.flower_show.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.LargeTest
import com.example.flower_show.model.ChatAvatarMember
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.RemoteChatMessageType
import com.example.flower_show.ui.component.GroupAvatar
import com.example.flower_show.ui.screen.ChatScreen
import com.example.flower_show.ui.screen.MessageListScreen
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.viewmodel.ChatState
import com.example.flower_show.viewmodel.SocialState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@LargeTest
@RunWith(AndroidJUnit4::class)
class MessagingScreensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun groupAvatarUsesOnlyFirstFourMembersAndFallsBackToNicknameInitials() {
        composeRule.setContent {
            FlowerShowTheme {
                GroupAvatar(
                    displayAvatarUrl = null,
                    avatarMembers = listOf(
                        avatarMember("owner", "甲"),
                        avatarMember("member-2", "乙"),
                        avatarMember("member-3", "丙"),
                        avatarMember("member-4", "丁"),
                        avatarMember("member-5", "戊"),
                    ),
                    displayName = "测试群",
                    testTag = "avatar_under_test",
                )
            }
        }

        repeat(4) { index ->
            composeRule
                .onNodeWithTag("avatar_under_test_member_$index", useUnmergedTree = true)
                .assertIsDisplayed()
            composeRule
                .onNodeWithTag(
                    "avatar_under_test_member_${index}_initial",
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
        }
        composeRule
            .onNodeWithTag("avatar_under_test_member_4", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithText("甲", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("丁", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("戊", useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun customGroupAvatarTakesPriorityOverMemberGrid() {
        composeRule.setContent {
            FlowerShowTheme {
                GroupAvatar(
                    displayAvatarUrl = "https://cdn.example/group.jpg",
                    avatarMembers = listOf(avatarMember("owner", "甲")),
                    displayName = "测试群",
                    testTag = "custom_avatar",
                )
            }
        }

        composeRule
            .onNodeWithTag("custom_avatar_custom", useUnmergedTree = true)
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag("custom_avatar_member_0", useUnmergedTree = true)
            .assertDoesNotExist()
    }

    @Test
    fun conversationListUsesGroupAvatarAndPrefixesOnlyGroupPreviewWithSenderName() {
        val senderMessage = remoteMessage(
            id = "last-message",
            senderUserId = "member-2",
            senderNickname = "林",
            text = "今晚看花吗",
        )
        val group = conversationSummary(
            id = "group-conversation",
            type = ChatConversationType.GROUP,
            displayName = "花友群",
            lastMessage = senderMessage,
            avatarMembers = listOf(
                avatarMember("owner", "群主"),
                avatarMember("member-2", "林"),
                avatarMember("member-3", "叶"),
                avatarMember("member-4", "雨"),
            ),
        )
        val direct = conversationSummary(
            id = "direct-conversation",
            type = ChatConversationType.DIRECT,
            displayName = "林",
            lastMessage = senderMessage.copy(
                messageId = "direct-last-message",
                conversationId = "direct-conversation",
                text = "私聊消息",
            ),
        )

        composeRule.setContent {
            FlowerShowTheme {
                MessageListScreen(
                    state = SocialState(),
                    chatState = ChatState(conversations = listOf(group, direct)),
                    onBack = {},
                    onConversationClick = {},
                )
            }
        }

        composeRule
            .onNodeWithTag("group_avatar_group-conversation", useUnmergedTree = true)
            .assertIsDisplayed()
        repeat(4) { index ->
            composeRule
                .onNodeWithTag(
                    "group_avatar_group-conversation_member_$index",
                    useUnmergedTree = true,
                )
                .assertIsDisplayed()
        }
        composeRule.onNodeWithText("林: 今晚看花吗").assertIsDisplayed()
        composeRule.onNodeWithText("私聊消息").assertIsDisplayed()
        composeRule.onNodeWithText("林: 私聊消息").assertDoesNotExist()
    }

    @Test
    fun groupChatShowsDetailDerivedHeaderAvatarAndEveryRemoteSenderIdentity() {
        val detail = conversationDetail(
            id = "group-room",
            type = ChatConversationType.GROUP,
            name = "花友群",
            members = listOf(
                member("me", "我", ChatMemberRole.OWNER),
                member("member-2", "林雾"),
                member("member-3", "阿遥"),
                member("member-4", "叶子"),
                member("member-5", "第五人"),
            ),
        )
        val withRemoteAvatar = remoteMessage(
            id = "remote-image",
            senderUserId = "member-2",
            senderNickname = "林雾",
            senderAvatarUrl = "https://cdn.example/member-2.jpg",
            text = "带头像消息",
            conversationId = detail.conversationId,
        )
        val withInitial = remoteMessage(
            id = "remote-initial",
            senderUserId = "member-3",
            senderNickname = "阿遥",
            text = "首字回退消息",
            conversationId = detail.conversationId,
        )
        val mine = remoteMessage(
            id = "mine",
            senderUserId = "me",
            senderNickname = "我",
            text = "本人消息",
            conversationId = detail.conversationId,
        )

        composeRule.setContent {
            FlowerShowTheme {
                ChatScreen(
                    conversationId = detail.conversationId,
                    state = ChatState(
                        currentUserId = "me",
                        activeConversationId = detail.conversationId,
                        activeConversation = detail,
                        messages = listOf(withRemoteAvatar, withInitial, mine),
                        messagesHasMore = false,
                    ),
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

        composeRule.onNodeWithTag("group_chat_avatar", useUnmergedTree = true)
            .assertIsDisplayed()
        repeat(4) { index ->
            composeRule
                .onNodeWithTag("group_chat_avatar_member_$index", useUnmergedTree = true)
                .assertIsDisplayed()
        }
        composeRule.onNodeWithTag("group_chat_avatar_member_4", useUnmergedTree = true)
            .assertDoesNotExist()
        composeRule.onNodeWithTag("chat_sender_name_remote-image").assertIsDisplayed()
        composeRule.onNodeWithText("林雾").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_sender_avatar_remote-image").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "chat_sender_avatar_remote-image_image",
            useUnmergedTree = true,
        ).assertIsDisplayed()
        composeRule.onNodeWithTag("chat_sender_name_remote-initial").assertIsDisplayed()
        composeRule.onNodeWithText("阿遥").assertIsDisplayed()
        composeRule.onNodeWithTag(
            "chat_sender_avatar_remote-initial_initial",
            useUnmergedTree = true,
        ).assertIsDisplayed().assertTextEquals("阿")
        composeRule.onNodeWithTag("chat_sender_avatar_mine").assertDoesNotExist()
        composeRule.onNodeWithTag("chat_sender_name_mine").assertDoesNotExist()
    }

    @Test
    fun directChatDoesNotForceSenderNameLabel() {
        val detail = conversationDetail(
            id = "direct-room",
            type = ChatConversationType.DIRECT,
            name = null,
            members = listOf(
                member("me", "我"),
                member("friend", "私聊好友"),
            ),
        )
        val message = remoteMessage(
            id = "direct-message",
            senderUserId = "friend",
            senderNickname = "私聊好友",
            text = "私聊内容",
            conversationId = detail.conversationId,
        )

        composeRule.setContent {
            FlowerShowTheme {
                ChatScreen(
                    conversationId = detail.conversationId,
                    state = ChatState(
                        currentUserId = "me",
                        activeConversationId = detail.conversationId,
                        activeConversation = detail,
                        messages = listOf(message),
                        messagesHasMore = false,
                    ),
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

        composeRule.onNodeWithTag("chat_sender_avatar_direct-message").assertIsDisplayed()
        composeRule.onNodeWithTag("chat_sender_name_direct-message").assertDoesNotExist()
    }
}

private fun avatarMember(
    userId: String,
    nickname: String,
) = ChatAvatarMember(
    userId = userId,
    nickname = nickname,
    avatarUrl = null,
)

private fun member(
    userId: String,
    nickname: String,
    role: ChatMemberRole = ChatMemberRole.MEMBER,
) = ChatMember(
    userId = userId,
    nickname = nickname,
    avatarUrl = null,
    role = role,
    joinedAt = "2026-07-30T08:00:00Z",
)

private fun conversationDetail(
    id: String,
    type: ChatConversationType,
    name: String?,
    members: List<ChatMember>,
) = ChatConversationDetail(
    conversationId = id,
    type = type,
    name = name,
    ownerUserId = members.firstOrNull { it.role == ChatMemberRole.OWNER }?.userId,
    state = ChatConversationState.ACTIVE,
    dissolvedAt = null,
    dissolvedByUserId = null,
    members = members,
    createdAt = "2026-07-30T08:00:00Z",
    updatedAt = "2026-07-30T08:00:00Z",
)

private fun conversationSummary(
    id: String,
    type: ChatConversationType,
    displayName: String,
    lastMessage: RemoteChatMessage,
    avatarMembers: List<ChatAvatarMember> = emptyList(),
) = ChatConversationSummary(
    conversationId = id,
    type = type,
    name = displayName,
    displayName = displayName,
    displayAvatarUrl = null,
    ownerUserId = avatarMembers.firstOrNull()?.userId,
    state = ChatConversationState.ACTIVE,
    dissolvedAt = null,
    lastMessage = lastMessage.copy(conversationId = id),
    unreadCount = 0,
    updatedAt = "刚刚",
    avatarMembers = avatarMembers,
)

private fun remoteMessage(
    id: String,
    senderUserId: String,
    senderNickname: String,
    text: String,
    conversationId: String = "group-conversation",
    senderAvatarUrl: String? = null,
) = RemoteChatMessage(
    messageId = id,
    conversationId = conversationId,
    senderUserId = senderUserId,
    senderNickname = senderNickname,
    senderAvatarUrl = senderAvatarUrl,
    type = RemoteChatMessageType.TEXT,
    text = text,
    sharedContentId = null,
    videoPreview = null,
    clientMessageId = "client-$id",
    createdAt = "刚刚",
)
