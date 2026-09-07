package com.example.flower_show.viewmodel

import com.example.flower_show.data.remote.chat.ChatRealtimeClient
import com.example.flower_show.data.remote.chat.ChatRealtimeConnectionState
import com.example.flower_show.data.remote.chat.ChatRealtimeEvent
import com.example.flower_show.data.repository.FollowResult
import com.example.flower_show.data.repository.IChatRepository
import com.example.flower_show.data.repository.ISocialRepository
import com.example.flower_show.data.repository.ProfileVideoPage
import com.example.flower_show.data.repository.SocialProfilePage
import com.example.flower_show.model.ChatAvatarMember
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatCursorPage
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.ChatReadReceipt
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.RemoteChatMessageType
import com.example.flower_show.model.Result as SocialResult
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {
    @get:Rule
    val mainDispatcherRule: TestWatcher = ChatMainDispatcherRule()

    @Test
    fun firstMessagePageIsReversedToChronologicalAndMarkedRead() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            messagePages += Result.success(
                ChatCursorPage(
                    items = listOf(message("m3"), message("m2"), message("m1")),
                    nextCursor = "older",
                    hasMore = true,
                ),
            )
        }
        val viewModel = viewModel(repository)

        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))

        assertEquals(listOf("m1", "m2", "m3"), viewModel.state.value.messages.ids())
        assertEquals(listOf("conversation" to "m3"), repository.markReadRequests)
    }

    @Test
    fun olderPageIsReversedPrependedDeduplicatedAndKeepsLastMessageId() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            messagePages += Result.success(
                ChatCursorPage(
                    items = listOf(message("m4"), message("m3")),
                    nextCursor = "page-2",
                    hasMore = true,
                ),
            )
            messagePages += Result.success(
                ChatCursorPage(
                    items = listOf(message("m3"), message("m2"), message("m1")),
                    nextCursor = null,
                    hasMore = false,
                ),
            )
        }
        val viewModel = viewModel(repository)
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))
        val lastBefore = viewModel.state.value.messages.last().messageId

        viewModel.dispatch(ChatIntent.LoadOlderMessages)
        runCurrent()

        assertEquals(listOf("m1", "m2", "m3", "m4"), viewModel.state.value.messages.ids())
        assertEquals(lastBefore, viewModel.state.value.messages.last().messageId)
    }

    @Test
    fun failedTextRetryReusesUuidAndOnlyAddsServerMessageOnSuccess() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            messagePages += Result.success(ChatCursorPage(emptyList(), null, false))
            textResults += Result.failure(IllegalStateException("暂时不可用"))
            textResults += Result.success(
                message(
                    id = "server-message",
                    senderId = "me",
                    text = "你好",
                    clientMessageId = "stable-uuid",
                ),
            )
        }
        val viewModel = viewModel(repository, uuid = "stable-uuid")
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))
        viewModel.dispatch(ChatIntent.UpdateDraft("  你好  "))

        viewModel.dispatch(ChatIntent.SendText)
        runCurrent()

        assertTrue(viewModel.state.value.messages.isEmpty())
        assertEquals("stable-uuid", viewModel.state.value.outgoingText?.clientMessageId)
        assertEquals("暂时不可用", viewModel.state.value.outgoingText?.error)

        viewModel.dispatch(ChatIntent.RetryText)
        runCurrent()

        assertEquals(
            listOf("stable-uuid", "stable-uuid"),
            repository.textRequests.map { it.clientMessageId },
        )
        assertEquals(listOf("server-message"), viewModel.state.value.messages.ids())
        assertEquals("", viewModel.state.value.draft)
        assertNull(viewModel.state.value.outgoingText)
    }

    @Test
    fun dismissingFailedTextAllowsTheDraftToBeSentAgain() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            messagePages += Result.success(ChatCursorPage(emptyList(), null, false))
            textResults += Result.failure(IllegalStateException("暂时不可用"))
        }
        val viewModel = viewModel(repository, uuid = "stable-uuid")
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))
        viewModel.dispatch(ChatIntent.UpdateDraft("你好"))
        viewModel.dispatch(ChatIntent.SendText)
        runCurrent()

        viewModel.dispatch(ChatIntent.DismissError)
        assertNull(viewModel.state.value.outgoingText)
        assertEquals("你好", viewModel.state.value.draft)

        viewModel.dispatch(ChatIntent.SendText)
        runCurrent()

        assertEquals(2, repository.textRequests.size)
        assertEquals(listOf("sent-2"), viewModel.state.value.messages.ids())
        assertEquals("", viewModel.state.value.draft)
    }

    @Test
    fun olderPageFailureDoesNotLeakIntoANewRoom() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            messagePages += Result.success(
                ChatCursorPage(
                    items = listOf(message("m2")),
                    nextCursor = "older",
                    hasMore = true,
                ),
            )
            messagePages += Result.failure(IllegalStateException("旧会话失败"))
        }
        val viewModel = viewModel(repository)
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))

        viewModel.dispatch(ChatIntent.LoadOlderMessages)
        viewModel.dispatch(ChatIntent.StartRoom("other"))
        viewModel.dispatch(ChatIntent.StopRoom("other"))
        runCurrent()

        assertEquals("other", viewModel.state.value.activeConversationId)
        assertNull(viewModel.state.value.roomError)
    }

    @Test
    fun switchingGroupInfoClearsStaleDetailAndBlocksOwnerActionsUntilLoaded() = runTest {
        val repository = FakeChatRepository().apply {
            detail = groupDetail("原群聊", "me", listOf("me", "mutual"))
        }
        val viewModel = viewModel(repository)
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.LoadGroupInfo("group"))
        runCurrent()
        viewModel.dispatch(ChatIntent.UpdateRenameDraft("新名称"))

        viewModel.dispatch(ChatIntent.LoadGroupInfo("other-group"))

        assertEquals("other-group", viewModel.state.value.activeConversationId)
        assertNull(viewModel.state.value.activeConversation)
        assertEquals("", viewModel.state.value.renameDraft)
        viewModel.dispatch(ChatIntent.RenameGroup)
        assertTrue(repository.renameRequests.isEmpty())
        runCurrent()
    }

    @Test
    fun conversationAndRoomPollingStartOnlyOnceAndStopCancelsFutureCalls() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
        }
        val viewModel = viewModel(
            repository = repository,
            conversationInterval = 100L,
            roomInterval = 50L,
        )
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))

        viewModel.dispatch(ChatIntent.StartConversationPolling)
        viewModel.dispatch(ChatIntent.StartConversationPolling)
        runCurrent()
        assertEquals(1, repository.listConversationCalls)
        advanceTimeBy(100L)
        runCurrent()
        assertEquals(2, repository.listConversationCalls)
        viewModel.dispatch(ChatIntent.StopConversationPolling)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(2, repository.listConversationCalls)

        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        assertEquals(1, repository.listMessageCalls)
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(2, repository.listMessageCalls)
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))
        advanceTimeBy(250L)
        runCurrent()
        assertEquals(2, repository.listMessageCalls)
    }

    @Test
    fun connectedRealtimeStopsFallbackPollingAndConnectionReadyRunsRestCatchUp() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
        }
        val realtime = FakeChatRealtimeClient()
        val viewModel = viewModel(
            repository = repository,
            realtime = realtime,
            conversationInterval = 100L,
            roomInterval = 50L,
        )
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartConversationPolling)
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()

        // BindCurrentUser 1 次 + 轮询循环“realtime 未连接时低频重连”第 1 轮 1 次。
        assertEquals(2, realtime.connectCalls)
        assertEquals(1, repository.listConversationCalls)
        assertEquals(1, repository.listMessageCalls)

        realtime.setConnectionState(ChatRealtimeConnectionState.Connected)
        runCurrent()

        assertEquals(2, repository.listConversationCalls)
        assertEquals(2, repository.listMessageCalls)
        advanceTimeBy(500L)
        runCurrent()
        assertEquals(2, repository.listConversationCalls)
        assertEquals(2, repository.listMessageCalls)

        realtime.setConnectionState(
            ChatRealtimeConnectionState.Reconnecting(attempt = 1, delayMillis = 100L),
        )
        runCurrent()
        assertEquals(3, repository.listConversationCalls)
        advanceTimeBy(50L)
        runCurrent()
        assertEquals(3, repository.listMessageCalls)
        viewModel.dispatch(ChatIntent.StopConversationPolling)
        viewModel.dispatch(ChatIntent.StopRoom("conversation"))
    }

    @Test
    fun realtimeMessageIsDeduplicatedAndUpdatesActiveConversationSummary() = runTest {
        val avatarMembers = listOf(
            ChatAvatarMember(
                userId = "mutual",
                nickname = "好友",
                avatarUrl = "https://cdn.example/friend.jpg",
            ),
        )
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            conversationPage = ChatCursorPage(
                items = listOf(
                    conversationSummary(
                        unreadCount = 4L,
                        avatarMembers = avatarMembers,
                    ),
                ),
                nextCursor = null,
                hasMore = false,
            )
        }
        val realtime = FakeChatRealtimeClient()
        val viewModel = viewModel(repository = repository, realtime = realtime)
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.StartConversationPolling)
        viewModel.dispatch(ChatIntent.StartRoom("conversation"))
        runCurrent()
        realtime.setConnectionState(ChatRealtimeConnectionState.Connected)
        runCurrent()
        val event = ChatRealtimeEvent.MessageCreated(
            eventId = "event-live",
            occurredAt = "2026-07-30T08:00:00Z",
            message = message("live-message"),
        )

        realtime.emit(event)
        realtime.emit(event)
        runCurrent()

        assertEquals(listOf("live-message"), viewModel.state.value.messages.ids())
        assertEquals(
            "live-message",
            viewModel.state.value.conversations.single().lastMessage?.messageId,
        )
        assertEquals(0L, viewModel.state.value.conversations.single().unreadCount)
        assertEquals(avatarMembers, viewModel.state.value.conversations.single().avatarMembers)
        assertEquals("conversation" to "live-message", repository.markReadRequests.last())
    }

    @Test
    fun videoShareLoadsOnlyMutualContactsThenResolvesDirectAndSends() = runTest {
        val repository = FakeChatRepository().apply {
            detail = directDetail()
            videoResult = Result.success(
                message(
                    id = "video-message",
                    senderId = "me",
                    type = RemoteChatMessageType.VIDEO_SHARE,
                    text = null,
                    clientMessageId = "video-uuid",
                ),
            )
        }
        val social = FakeChatSocialRepository()
        val viewModel = viewModel(repository, social, uuid = "video-uuid")
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        val video = ProfileVideo("video-9", "花园散步", "作者", "cover")

        viewModel.dispatch(ChatIntent.OpenShareComposer(video))
        runCurrent()

        assertEquals(
            listOf("mutual", "mutual-2"),
            viewModel.state.value.shareComposer?.candidates?.map { it.id },
        )
        assertFalse(
            viewModel.state.value.shareComposer?.candidates.orEmpty()
                .any { it.id == "not-mutual" },
        )
        viewModel.dispatch(ChatIntent.SelectShareRecipient("mutual"))
        viewModel.dispatch(ChatIntent.SendVideoShare)
        runCurrent()

        assertEquals(listOf("mutual"), repository.directRequests)
        assertEquals(
            listOf(VideoRequest("conversation", "video-9", "video-uuid")),
            repository.videoRequests,
        )
        assertNull(viewModel.state.value.shareComposer)
    }

    @Test
    fun directConversationFailureStaysPutAndSuccessEmitsConversationNavigation() = runTest {
        val repository = FakeChatRepository().apply {
            directFailure = IllegalStateException("无法创建私聊")
        }
        val viewModel = viewModel(repository)
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        val failureEffect = async { viewModel.effects.first() }

        viewModel.dispatch(ChatIntent.ResolveDirectConversation("mutual"))
        runCurrent()

        assertNull(viewModel.state.value.activeConversationId)
        assertEquals("无法创建私聊", viewModel.state.value.actionError)
        assertEquals(ChatEffect.ShowError("无法创建私聊"), failureEffect.await())

        repository.directFailure = null
        val successEffect = async { viewModel.effects.first() }
        viewModel.dispatch(ChatIntent.ResolveDirectConversation("mutual"))
        runCurrent()

        assertEquals("conversation", viewModel.state.value.activeConversationId)
        assertEquals(
            ChatEffect.NavigateToConversation("conversation"),
            successEffect.await(),
        )
    }

    @Test
    fun groupCreateRenameAddRemoveTransferAndLeaveRefreshAuthoritativeDetail() = runTest {
        val repository = FakeChatRepository().apply {
            detail = groupDetail(
                name = "原群名",
                ownerId = "me",
                memberIds = listOf("me", "mutual"),
            )
        }
        val viewModel = viewModel(repository, FakeChatSocialRepository())
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))

        viewModel.dispatch(ChatIntent.OpenGroupComposer)
        runCurrent()
        viewModel.dispatch(ChatIntent.UpdateGroupName("新群"))
        viewModel.dispatch(ChatIntent.ToggleGroupMember("mutual"))
        viewModel.dispatch(ChatIntent.CreateGroup)
        runCurrent()
        assertEquals(listOf("新群" to listOf("mutual")), repository.createGroupRequests)

        viewModel.dispatch(ChatIntent.LoadGroupInfo("group"))
        runCurrent()
        viewModel.dispatch(ChatIntent.UpdateRenameDraft("改名后"))
        viewModel.dispatch(ChatIntent.RenameGroup)
        runCurrent()
        assertEquals(listOf("改名后"), repository.renameRequests)
        assertEquals("改名后", viewModel.state.value.activeConversation?.name)

        viewModel.dispatch(ChatIntent.PrepareAddMembers("group"))
        runCurrent()
        viewModel.dispatch(ChatIntent.ToggleAddMember("mutual-2"))
        viewModel.dispatch(ChatIntent.AddSelectedMembers)
        runCurrent()
        assertEquals(listOf(listOf("mutual-2")), repository.addRequests)
        assertTrue(
            viewModel.state.value.activeConversation?.members.orEmpty()
                .any { it.userId == "mutual-2" },
        )

        viewModel.dispatch(ChatIntent.RemoveMember("mutual-2"))
        runCurrent()
        assertEquals(listOf("mutual-2"), repository.removeRequests)
        assertFalse(
            viewModel.state.value.activeConversation?.members.orEmpty()
                .any { it.userId == "mutual-2" },
        )

        viewModel.dispatch(ChatIntent.TransferGroupOwner("mutual"))
        runCurrent()
        assertEquals(listOf("mutual"), repository.transferRequests)
        assertEquals("mutual", viewModel.state.value.activeConversation?.ownerUserId)

        viewModel.dispatch(ChatIntent.LeaveConversation)
        runCurrent()
        assertEquals(1, repository.leaveCalls)
        assertTrue(repository.getConversationCalls >= 5)
    }

    @Test
    fun dissolvedGroupBecomesReadOnlyAndBlocksFurtherManagementCalls() = runTest {
        val repository = FakeChatRepository().apply {
            detail = groupDetail(
                name = "待解散",
                ownerId = "me",
                memberIds = listOf("me", "mutual"),
            )
        }
        val viewModel = viewModel(repository)
        viewModel.dispatch(ChatIntent.BindCurrentUser("me"))
        viewModel.dispatch(ChatIntent.LoadGroupInfo("group"))
        runCurrent()

        viewModel.dispatch(ChatIntent.DissolveGroup)
        runCurrent()

        assertEquals(1, repository.dissolveCalls)
        assertEquals(ChatConversationState.DISSOLVED, viewModel.state.value.activeConversation?.state)
        assertFalse(viewModel.state.value.canSendMessage)

        viewModel.dispatch(ChatIntent.UpdateRenameDraft("不应成功"))
        viewModel.dispatch(ChatIntent.RenameGroup)
        runCurrent()
        assertTrue(repository.renameRequests.isEmpty())
        assertEquals("群聊已解散，只能查看历史消息", viewModel.state.value.groupActionError)
    }

    private fun kotlinx.coroutines.test.TestScope.viewModel(
        repository: FakeChatRepository,
        social: ISocialRepository = FakeChatSocialRepository(),
        uuid: String = "uuid",
        conversationInterval: Long = 7_500L,
        roomInterval: Long = 2_500L,
        realtime: ChatRealtimeClient = FakeChatRealtimeClient(),
    ) = ChatViewModel(
        chatRepository = repository,
        socialRepository = social,
        ioDispatcher = StandardTestDispatcher(testScheduler),
        uuidProvider = { uuid },
        conversationPollIntervalMillis = conversationInterval,
        roomPollIntervalMillis = roomInterval,
        realtimeClient = realtime,
    )
}

private data class TextRequest(
    val conversationId: String,
    val text: String,
    val clientMessageId: String,
)

private data class VideoRequest(
    val conversationId: String,
    val contentId: String,
    val clientMessageId: String,
)

private class FakeChatRepository : IChatRepository {
    var detail: ChatConversationDetail = directDetail()
    var conversationPage = ChatCursorPage<ChatConversationSummary>(emptyList(), null, false)
    val messagePages = ArrayDeque<Result<ChatCursorPage<RemoteChatMessage>>>()
    val textResults = ArrayDeque<Result<RemoteChatMessage>>()
    var videoResult: Result<RemoteChatMessage> = Result.success(
        message("video-default", type = RemoteChatMessageType.VIDEO_SHARE, text = null),
    )
    var directFailure: Throwable? = null
    var listConversationCalls = 0
    var listMessageCalls = 0
    var getConversationCalls = 0
    val markReadRequests = mutableListOf<Pair<String, String?>>()
    val textRequests = mutableListOf<TextRequest>()
    val videoRequests = mutableListOf<VideoRequest>()
    val directRequests = mutableListOf<String>()
    val createGroupRequests = mutableListOf<Pair<String, List<String>>>()
    val renameRequests = mutableListOf<String>()
    val addRequests = mutableListOf<List<String>>()
    val removeRequests = mutableListOf<String>()
    val transferRequests = mutableListOf<String>()
    var dissolveCalls = 0
    var leaveCalls = 0

    override suspend fun listConversations(
        cursor: String?,
        pageSize: Int,
    ): Result<ChatCursorPage<ChatConversationSummary>> {
        listConversationCalls++
        return Result.success(conversationPage)
    }

    override suspend fun createOrResolveDirectConversation(
        userId: String,
    ): Result<ChatConversationDetail> {
        directRequests += userId
        return directFailure?.let(Result.Companion::failure)
            ?: Result.success(directDetail())
    }

    override suspend fun createGroupConversation(
        name: String,
        memberUserIds: List<String>,
    ): Result<ChatConversationDetail> {
        createGroupRequests += name to memberUserIds
        detail = groupDetail(name, "me", listOf("me") + memberUserIds)
        return Result.success(detail)
    }

    override suspend fun getConversation(
        conversationId: String,
    ): Result<ChatConversationDetail> {
        getConversationCalls++
        return Result.success(detail)
    }

    override suspend fun renameGroup(
        conversationId: String,
        name: String,
    ): Result<ChatConversationDetail> {
        renameRequests += name
        detail = detail.copy(name = name)
        return Result.success(detail)
    }

    override suspend fun addMembers(
        conversationId: String,
        userIds: List<String>,
    ): Result<ChatConversationDetail> {
        addRequests += userIds
        val existing = detail.members.mapTo(mutableSetOf()) { it.userId }
        detail = detail.copy(
            members = detail.members + userIds.filterNot { it in existing }.map(::member),
        )
        return Result.success(detail)
    }

    override suspend fun removeMember(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail> {
        removeRequests += userId
        detail = detail.copy(members = detail.members.filterNot { it.userId == userId })
        return Result.success(detail)
    }

    override suspend fun transferGroupOwner(
        conversationId: String,
        userId: String,
    ): Result<ChatConversationDetail> {
        transferRequests += userId
        detail = detail.copy(
            ownerUserId = userId,
            members = detail.members.map {
                it.copy(
                    role = if (it.userId == userId) ChatMemberRole.OWNER
                    else ChatMemberRole.MEMBER,
                )
            },
        )
        return Result.success(detail)
    }

    override suspend fun dissolveGroup(
        conversationId: String,
    ): Result<ChatConversationDetail> {
        dissolveCalls++
        detail = detail.copy(state = ChatConversationState.DISSOLVED)
        return Result.success(detail)
    }

    override suspend fun leaveConversation(conversationId: String): Result<Boolean> {
        leaveCalls++
        return Result.success(true)
    }

    override suspend fun listMessages(
        conversationId: String,
        cursor: String?,
        pageSize: Int,
    ): Result<ChatCursorPage<RemoteChatMessage>> {
        listMessageCalls++
        return if (messagePages.isEmpty()) {
            Result.success(ChatCursorPage(emptyList(), null, false))
        } else {
            messagePages.removeFirst()
        }
    }

    override suspend fun sendText(
        conversationId: String,
        text: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage> {
        textRequests += TextRequest(conversationId, text, clientMessageId)
        return if (textResults.isEmpty()) {
            Result.success(
                message(
                    id = "sent-${textRequests.size}",
                    senderId = "me",
                    text = text,
                    clientMessageId = clientMessageId,
                ),
            )
        } else {
            textResults.removeFirst()
        }
    }

    override suspend fun sendVideoShare(
        conversationId: String,
        contentId: String,
        clientMessageId: String,
    ): Result<RemoteChatMessage> {
        videoRequests += VideoRequest(conversationId, contentId, clientMessageId)
        return videoResult
    }

    override suspend fun markRead(
        conversationId: String,
        messageId: String?,
    ): Result<ChatReadReceipt> {
        markReadRequests += conversationId to messageId
        return Result.success(
            ChatReadReceipt(conversationId, messageId, "now", unreadCount = 0),
        )
    }
}

private class FakeChatRealtimeClient(
    initialState: ChatRealtimeConnectionState = ChatRealtimeConnectionState.Disconnected,
) : ChatRealtimeClient {
    private val mutableConnectionState = MutableStateFlow(initialState)
    private val mutableEvents = MutableSharedFlow<ChatRealtimeEvent>(extraBufferCapacity = 16)

    override val connectionState: StateFlow<ChatRealtimeConnectionState> =
        mutableConnectionState
    override val events: Flow<ChatRealtimeEvent> = mutableEvents

    var connectCalls = 0
        private set
    var disconnectCalls = 0
        private set

    override fun connect() {
        connectCalls++
    }

    override fun disconnect() {
        disconnectCalls++
        mutableConnectionState.value = ChatRealtimeConnectionState.Disconnected
    }

    override fun close() {
        disconnect()
    }

    fun setConnectionState(state: ChatRealtimeConnectionState) {
        mutableConnectionState.value = state
    }

    fun emit(event: ChatRealtimeEvent) {
        check(mutableEvents.tryEmit(event))
    }
}

private class FakeChatSocialRepository : ISocialRepository {
    private val contacts = listOf(
        profile("mutual", mutual = true),
        profile("mutual-2", mutual = true),
        profile("not-mutual", mutual = false),
    )

    override fun loadProfile(userId: String): SocialResult<SocialProfile> =
        SocialResult.success(profile(userId, mutual = false))

    override fun loadProfileVideos(
        userId: String,
        tab: String,
        page: Int,
        pageSize: Int,
    ): SocialResult<ProfileVideoPage> =
        SocialResult.success(ProfileVideoPage(emptyList(), false))

    override fun loadRelations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ): SocialResult<SocialProfilePage> = SocialResult.success(
        SocialProfilePage(
            items = contacts.filter {
                keyword.isBlank() || it.nickname.contains(keyword, ignoreCase = true)
            },
            hasMore = false,
        ),
    )

    override fun setFollowing(
        actorUserId: String,
        targetUserId: String,
        following: Boolean,
    ): SocialResult<FollowResult> = SocialResult.error("unused")

    override fun setWorkVisible(
        userId: String,
        contentId: String,
        visible: Boolean,
    ): SocialResult<Boolean> = SocialResult.error("unused")

    override fun loadNotifications(
        cursor: String?,
        pageSize: Int,
    ): SocialResult<CursorPage<SocialNotification>> =
        SocialResult.success(CursorPage(emptyList(), null, false))

    override fun loadNotificationUnreadCount(): SocialResult<Int> = SocialResult.success(0)

    override fun markNotificationRead(notificationId: String): SocialResult<Int> =
        SocialResult.success(0)

    override fun markAllNotificationsRead(): SocialResult<Int> = SocialResult.success(0)
}

private fun directDetail() = ChatConversationDetail(
    conversationId = "conversation",
    type = ChatConversationType.DIRECT,
    name = null,
    ownerUserId = null,
    state = ChatConversationState.ACTIVE,
    dissolvedAt = null,
    dissolvedByUserId = null,
    members = listOf(member("me"), member("mutual")),
    createdAt = "created",
    updatedAt = "updated",
)

private fun conversationSummary(
    unreadCount: Long = 0L,
    avatarMembers: List<ChatAvatarMember> = emptyList(),
) = ChatConversationSummary(
    conversationId = "conversation",
    type = ChatConversationType.DIRECT,
    name = null,
    displayName = "好友",
    displayAvatarUrl = null,
    ownerUserId = null,
    state = ChatConversationState.ACTIVE,
    dissolvedAt = null,
    lastMessage = null,
    unreadCount = unreadCount,
    updatedAt = "2026-07-30T07:59:00Z",
    avatarMembers = avatarMembers,
)

private fun groupDetail(
    name: String,
    ownerId: String,
    memberIds: List<String>,
) = ChatConversationDetail(
    conversationId = "group",
    type = ChatConversationType.GROUP,
    name = name,
    ownerUserId = ownerId,
    state = ChatConversationState.ACTIVE,
    dissolvedAt = null,
    dissolvedByUserId = null,
    members = memberIds.distinct().map {
        member(it, if (it == ownerId) ChatMemberRole.OWNER else ChatMemberRole.MEMBER)
    },
    createdAt = "created",
    updatedAt = "updated",
)

private fun member(
    id: String,
    role: ChatMemberRole = ChatMemberRole.MEMBER,
) = ChatMember(
    userId = id,
    nickname = id,
    avatarUrl = null,
    role = role,
    joinedAt = "joined",
)

private fun message(
    id: String,
    senderId: String = "mutual",
    type: RemoteChatMessageType = RemoteChatMessageType.TEXT,
    text: String? = id,
    clientMessageId: String = "client-$id",
) = RemoteChatMessage(
    messageId = id,
    conversationId = "conversation",
    senderUserId = senderId,
    senderNickname = senderId,
    senderAvatarUrl = null,
    type = type,
    text = text,
    sharedContentId = null,
    videoPreview = null,
    clientMessageId = clientMessageId,
    createdAt = id,
)

private fun profile(id: String, mutual: Boolean) = SocialProfile(
    id = id,
    account = id,
    nickname = id,
    bio = "",
    region = "",
    avatarUrl = "",
    worksCount = 0,
    followingCount = 0,
    followersCount = 0,
    likesReceivedCount = 0,
    isFollowing = true,
    followsMe = mutual,
)

private fun List<RemoteChatMessage>.ids(): List<String> = map(RemoteChatMessage::messageId)

@OptIn(ExperimentalCoroutinesApi::class)
private class ChatMainDispatcherRule(
    private val dispatcher: TestDispatcher = UnconfinedTestDispatcher(),
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
