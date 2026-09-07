package com.example.flower_show.viewmodel

import com.example.flower_show.model.ProfileVideo

sealed interface ChatIntent {
    data class BindCurrentUser(val userId: String) : ChatIntent

    data object StartConversationPolling : ChatIntent
    data object StopConversationPolling : ChatIntent
    data object RefreshConversations : ChatIntent
    data object LoadMoreConversations : ChatIntent

    data class ResolveDirectConversation(val userId: String) : ChatIntent

    data class StartRoom(val conversationId: String) : ChatIntent
    data class StopRoom(val conversationId: String) : ChatIntent
    data object RefreshRoom : ChatIntent
    data object LoadOlderMessages : ChatIntent
    data class UpdateDraft(val text: String) : ChatIntent
    data object SendText : ChatIntent
    data object RetryText : ChatIntent

    data class OpenShareComposer(val video: ProfileVideo) : ChatIntent
    data class UpdateShareQuery(val query: String) : ChatIntent
    data class SelectShareRecipient(val userId: String) : ChatIntent
    data object SendVideoShare : ChatIntent
    data object DismissShareComposer : ChatIntent

    data object OpenGroupComposer : ChatIntent
    data class UpdateGroupName(val name: String) : ChatIntent
    data class UpdateGroupQuery(val query: String) : ChatIntent
    data class ToggleGroupMember(val userId: String) : ChatIntent
    data object CreateGroup : ChatIntent
    data object DismissGroupComposer : ChatIntent

    data class LoadGroupInfo(val conversationId: String) : ChatIntent
    data class UpdateRenameDraft(val name: String) : ChatIntent
    data object RenameGroup : ChatIntent
    data class PrepareAddMembers(val conversationId: String) : ChatIntent
    data class UpdateAddMembersQuery(val query: String) : ChatIntent
    data class ToggleAddMember(val userId: String) : ChatIntent
    data object AddSelectedMembers : ChatIntent
    data object DismissAddMembers : ChatIntent
    data class RemoveMember(val userId: String) : ChatIntent
    data class TransferGroupOwner(val userId: String) : ChatIntent
    data object DissolveGroup : ChatIntent
    data object LeaveConversation : ChatIntent

    data object DismissError : ChatIntent
}

sealed interface ChatEffect {
    data class NavigateToConversation(val conversationId: String) : ChatEffect
    data class GroupCreated(val conversationId: String) : ChatEffect
    data class VideoShared(val conversationId: String) : ChatEffect
    data class MembersAdded(val conversationId: String) : ChatEffect
    data class ShowError(val message: String) : ChatEffect
    data object ConversationExited : ChatEffect
}
