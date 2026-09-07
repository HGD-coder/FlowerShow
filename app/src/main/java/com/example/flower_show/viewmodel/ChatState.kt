package com.example.flower_show.viewmodel

import androidx.compose.runtime.Immutable
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.SocialProfile

@Immutable
data class OutgoingTextState(
    val conversationId: String,
    val text: String,
    val clientMessageId: String,
    val isSending: Boolean,
    val error: String? = null,
)

@Immutable
data class ChatShareComposerState(
    val video: ProfileVideo,
    val query: String = "",
    val candidates: List<SocialProfile> = emptyList(),
    val selectedUserId: String? = null,
    val clientMessageId: String? = null,
    val isLoadingCandidates: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
)

@Immutable
data class GroupComposerState(
    val name: String = "",
    val query: String = "",
    val candidates: List<SocialProfile> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val isLoadingCandidates: Boolean = false,
    val isCreating: Boolean = false,
    val error: String? = null,
)

@Immutable
data class AddMembersState(
    val conversationId: String,
    val query: String = "",
    val candidates: List<SocialProfile> = emptyList(),
    val selectedUserIds: Set<String> = emptySet(),
    val isLoadingCandidates: Boolean = false,
    val isAdding: Boolean = false,
    val error: String? = null,
)

@Immutable
data class ChatState(
    val currentUserId: String = "",
    val conversations: List<ChatConversationSummary> = emptyList(),
    val conversationsNextCursor: String? = null,
    val conversationsHasMore: Boolean = true,
    val isLoadingConversations: Boolean = false,
    val isLoadingMoreConversations: Boolean = false,
    val conversationError: String? = null,
    val isResolvingDirectConversation: Boolean = false,
    val actionError: String? = null,
    val activeConversationId: String? = null,
    val activeConversation: ChatConversationDetail? = null,
    val messages: List<RemoteChatMessage> = emptyList(),
    val messagesNextCursor: String? = null,
    val messagesHasMore: Boolean = true,
    val isLoadingRoom: Boolean = false,
    val isLoadingOlderMessages: Boolean = false,
    val roomError: String? = null,
    val draft: String = "",
    val outgoingText: OutgoingTextState? = null,
    val shareComposer: ChatShareComposerState? = null,
    val groupComposer: GroupComposerState? = null,
    val addMembers: AddMembersState? = null,
    val renameDraft: String = "",
    val isLoadingGroupInfo: Boolean = false,
    val isGroupActionInProgress: Boolean = false,
    val groupActionError: String? = null,
) {
    val isRoomDissolved: Boolean
        get() = activeConversation?.state == ChatConversationState.DISSOLVED

    val canSendMessage: Boolean
        get() = activeConversation != null && !isRoomDissolved

    val isCurrentUserOwner: Boolean
        get() = activeConversation?.ownerUserId == currentUserId

    val currentMember: ChatMember?
        get() = activeConversation?.members?.firstOrNull { it.userId == currentUserId }
}
