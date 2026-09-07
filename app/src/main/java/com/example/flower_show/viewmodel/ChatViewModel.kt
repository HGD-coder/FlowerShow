package com.example.flower_show.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.remote.chat.ChatRealtimeClient
import com.example.flower_show.data.remote.chat.ChatRealtimeConnectionState
import com.example.flower_show.data.remote.chat.ChatRealtimeEvent
import com.example.flower_show.data.remote.chat.NoOpChatRealtimeClient
import com.example.flower_show.data.repository.IChatRepository
import com.example.flower_show.data.repository.ISocialRepository
import com.example.flower_show.model.ChatAvatarMember
import com.example.flower_show.model.ChatConversationDetail
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatConversationSummary
import com.example.flower_show.model.ChatConversationType
import com.example.flower_show.model.ChatCursorPage
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.RemoteChatMessage
import com.example.flower_show.model.Result as SocialResult
import com.example.flower_show.model.SocialProfile
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ChatViewModel(
    private val chatRepository: IChatRepository,
    private val socialRepository: ISocialRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val uuidProvider: () -> String = { UUID.randomUUID().toString() },
    private val conversationPollIntervalMillis: Long = ConversationPollIntervalMillis,
    private val roomPollIntervalMillis: Long = RoomPollIntervalMillis,
    private val realtimeClient: ChatRealtimeClient = NoOpChatRealtimeClient,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatState())
    val state: StateFlow<ChatState> = _state.asStateFlow()

    private val effectChannel = Channel<ChatEffect>(Channel.BUFFERED)
    val effects: Flow<ChatEffect> = effectChannel.receiveAsFlow()

    private var conversationPollingJob: Job? = null
    private var roomPollingJob: Job? = null
    private var roomInitialLoadJob: Job? = null
    private var realtimeSyncJob: Job? = null
    private var shareSearchJob: Job? = null
    private var groupSearchJob: Job? = null
    private var addMembersSearchJob: Job? = null
    private var conversationPollingRequested = false
    private var roomPollingConversationId: String? = null

    /**
     * 按会话记忆的输入草稿：离开会话再进入时恢复未发送内容。
     *
     * 主线程写入（UpdateDraft）与 IO 协程写入（sendText 成功后清空）并发，
     * 必须用并发容器。
     */
    private val roomDrafts = ConcurrentHashMap<String, String>()

    /**
     * 正在拉取详情的未知会话 id，防止同一会话的实时消息触发并发重复拉取。
     *
     * 主线程 add（实时事件收集在主线程）与 IO 协程 remove 并发，必须用并发容器。
     */
    private val conversationsBeingFetched = ConcurrentHashMap.newKeySet<String>()

    @Volatile
    private var hasLoadedConversations = false
    private val seenRealtimeMessageIds = LinkedHashSet<String>()

    init {
        viewModelScope.launch {
            realtimeClient.connectionState.collect(::handleRealtimeConnectionState)
        }
        viewModelScope.launch {
            realtimeClient.events.collect { event ->
                if (event is ChatRealtimeEvent.MessageCreated) {
                    handleRealtimeMessage(event)
                }
            }
        }
    }

    fun dispatch(intent: ChatIntent) {
        when (intent) {
            is ChatIntent.BindCurrentUser -> bindCurrentUser(intent.userId)
            ChatIntent.StartConversationPolling -> startConversationPolling()
            ChatIntent.StopConversationPolling -> stopConversationPolling()
            ChatIntent.RefreshConversations -> refreshConversations()
            ChatIntent.LoadMoreConversations -> loadMoreConversations()
            is ChatIntent.ResolveDirectConversation -> resolveDirectConversation(intent.userId)
            is ChatIntent.StartRoom -> startRoom(intent.conversationId)
            is ChatIntent.StopRoom -> stopRoom(intent.conversationId)
            ChatIntent.RefreshRoom -> refreshRoom()
            ChatIntent.LoadOlderMessages -> loadOlderMessages()
            is ChatIntent.UpdateDraft -> {
                // 草稿按会话记忆：离开会话再进入时恢复未发送的内容。
                _state.value.activeConversationId?.let { roomDrafts[it] = intent.text }
                _state.update { it.copy(draft = intent.text) }
            }
            ChatIntent.SendText -> sendText(retry = false)
            ChatIntent.RetryText -> sendText(retry = true)
            is ChatIntent.OpenShareComposer -> openShareComposer(intent.video)
            is ChatIntent.UpdateShareQuery -> updateShareQuery(intent.query)
            is ChatIntent.SelectShareRecipient -> selectShareRecipient(intent.userId)
            ChatIntent.SendVideoShare -> sendVideoShare()
            ChatIntent.DismissShareComposer -> dismissShareComposer()
            ChatIntent.OpenGroupComposer -> openGroupComposer()
            is ChatIntent.UpdateGroupName -> updateGroupName(intent.name)
            is ChatIntent.UpdateGroupQuery -> updateGroupQuery(intent.query)
            is ChatIntent.ToggleGroupMember -> toggleGroupMember(intent.userId)
            ChatIntent.CreateGroup -> createGroup()
            ChatIntent.DismissGroupComposer -> dismissGroupComposer()
            is ChatIntent.LoadGroupInfo -> loadGroupInfo(intent.conversationId)
            is ChatIntent.UpdateRenameDraft ->
                _state.update { it.copy(renameDraft = intent.name, groupActionError = null) }
            ChatIntent.RenameGroup -> renameGroup()
            is ChatIntent.PrepareAddMembers -> prepareAddMembers(intent.conversationId)
            is ChatIntent.UpdateAddMembersQuery -> updateAddMembersQuery(intent.query)
            is ChatIntent.ToggleAddMember -> toggleAddMember(intent.userId)
            ChatIntent.AddSelectedMembers -> addSelectedMembers()
            ChatIntent.DismissAddMembers -> dismissAddMembers()
            is ChatIntent.RemoveMember -> removeMember(intent.userId)
            is ChatIntent.TransferGroupOwner -> transferGroupOwner(intent.userId)
            ChatIntent.DissolveGroup -> dissolveGroup()
            ChatIntent.LeaveConversation -> leaveConversation()
            ChatIntent.DismissError -> dismissErrors()
        }
    }

    private fun bindCurrentUser(userId: String) {
        if (userId == _state.value.currentUserId) return
        stopConversationPolling()
        roomPollingConversationId = null
        roomPollingJob?.cancel()
        roomPollingJob = null
        roomInitialLoadJob?.cancel()
        roomInitialLoadJob = null
        realtimeSyncJob?.cancel()
        realtimeSyncJob = null
        realtimeClient.disconnect()
        seenRealtimeMessageIds.clear()
        hasLoadedConversations = false
        _state.value = ChatState(currentUserId = userId)
        if (userId.isNotBlank()) {
            realtimeClient.connect()
        }
    }

    private fun startConversationPolling() {
        conversationPollingRequested = true
        if (isRealtimeConnected()) {
            if (
                !hasLoadedConversations &&
                realtimeSyncJob?.isActive != true &&
                conversationPollingJob?.isActive != true
            ) {
                conversationPollingJob = viewModelScope.launch(ioDispatcher) {
                    loadConversationPage(
                        reset = true,
                        showLoading = true,
                        mergeExisting = false,
                    )
                }
            }
            return
        }
        ensureConversationPolling()
    }

    private fun ensureConversationPolling() {
        if (
            !conversationPollingRequested ||
            _state.value.currentUserId.isBlank() ||
            isRealtimeConnected() ||
            conversationPollingJob?.isActive == true
        ) {
            return
        }
        if (conversationPollingJob?.isActive == true) return
        conversationPollingJob = viewModelScope.launch(ioDispatcher) {
            var firstLoad = _state.value.conversations.isEmpty()
            while (isActive) {
                loadConversationPage(reset = true, showLoading = firstLoad, mergeExisting = !firstLoad)
                firstLoad = false
                // REST 轮询请求已通过 authenticator 完成 token 刷新；
                // 若 realtime 此前因认证失败（HTTP 401 / 关闭码 4001）停止重连，
                // 这里按轮询周期低频重试一次；网络类错误仍由客户端自己的指数退避处理
                // （connect() 在 connectionRequested 为 true 时是 no-op）。
                if (!isRealtimeConnected()) {
                    realtimeClient.connect()
                }
                delay(conversationPollIntervalMillis)
            }
        }
    }

    private fun stopConversationPolling() {
        conversationPollingRequested = false
        cancelConversationPollingJob()
    }

    private fun cancelConversationPollingJob() {
        conversationPollingJob?.cancel()
        conversationPollingJob = null
    }

    private fun refreshConversations() {
        viewModelScope.launch(ioDispatcher) {
            loadConversationPage(reset = true, showLoading = true, mergeExisting = false)
        }
    }

    private fun loadMoreConversations() {
        val snapshot = _state.value
        if (
            snapshot.isLoadingMoreConversations ||
            !snapshot.conversationsHasMore ||
            snapshot.conversationsNextCursor.isNullOrBlank()
        ) {
            return
        }
        viewModelScope.launch(ioDispatcher) {
            loadConversationPage(reset = false, showLoading = false, mergeExisting = true)
        }
    }

    private suspend fun loadConversationPage(
        reset: Boolean,
        showLoading: Boolean,
        mergeExisting: Boolean,
    ) {
        val snapshot = _state.value
        // 会话分页请求互斥：轮询(reset=true)与 loadMore(reset=false)不能并发，
        // 否则晚返回的一方会把另一方刚推进的游标覆盖回去，导致列表倒退/重复。
        if (snapshot.isLoadingConversations || snapshot.isLoadingMoreConversations) return
        _state.update {
            it.copy(
                isLoadingConversations = showLoading,
                isLoadingMoreConversations = !reset,
                conversationError = null,
            )
        }
        val cursor = if (reset) null else snapshot.conversationsNextCursor
        chatRepository.listConversations(cursor = cursor).fold(
            onSuccess = { page ->
                hasLoadedConversations = true
                _state.update { current ->
                    val combined = when {
                        reset && !mergeExisting -> page.items
                        reset -> mergeConversationSummaries(page.items, current.conversations)
                        else -> current.conversations + page.items
                    }.distinctBy { it.conversationId }
                    current.copy(
                        conversations = combined,
                        conversationsNextCursor = page.nextCursor,
                        conversationsHasMore = page.hasMore,
                        isLoadingConversations = false,
                        isLoadingMoreConversations = false,
                    )
                }
            },
            onFailure = { cause ->
                _state.update {
                    it.copy(
                        isLoadingConversations = false,
                        isLoadingMoreConversations = false,
                        conversationError = cause.userMessage("会话加载失败"),
                    )
                }
            },
        )
    }

    private fun resolveDirectConversation(userId: String) {
        if (userId.isBlank() || _state.value.isResolvingDirectConversation) return
        _state.update { it.copy(isResolvingDirectConversation = true, actionError = null) }
        viewModelScope.launch(ioDispatcher) {
            chatRepository.createOrResolveDirectConversation(userId).fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            isResolvingDirectConversation = false,
                            activeConversationId = detail.conversationId,
                            activeConversation = detail,
                        )
                    }
                    effectChannel.send(ChatEffect.NavigateToConversation(detail.conversationId))
                },
                onFailure = { cause ->
                    val message = cause.userMessage("无法发起会话")
                    _state.update {
                        it.copy(isResolvingDirectConversation = false, actionError = message)
                    }
                    effectChannel.send(ChatEffect.ShowError(message))
                },
            )
        }
    }

    private fun startRoom(conversationId: String) {
        if (conversationId.isBlank()) return
        if (
            _state.value.activeConversationId == conversationId &&
            roomPollingConversationId == conversationId
        ) {
            return
        }
        roomPollingConversationId = conversationId
        roomPollingJob?.cancel()
        roomPollingJob = null
        roomInitialLoadJob?.cancel()
        _state.update {
            it.copy(
                activeConversationId = conversationId,
                activeConversation = null,
                messages = emptyList(),
                messagesNextCursor = null,
                messagesHasMore = true,
                isLoadingRoom = true,
                isLoadingOlderMessages = false,
                roomError = null,
                draft = roomDrafts[conversationId].orEmpty(),
                outgoingText = null,
                groupActionError = null,
            )
        }
        roomInitialLoadJob = viewModelScope.launch(ioDispatcher) {
            loadRoomSnapshot(conversationId, initial = true)
            roomInitialLoadJob = null
            ensureRoomPolling()
        }
    }

    private fun stopRoom(conversationId: String) {
        if (_state.value.activeConversationId != conversationId) return
        roomPollingConversationId = null
        roomInitialLoadJob?.cancel()
        roomInitialLoadJob = null
        roomPollingJob?.cancel()
        roomPollingJob = null
    }

    private fun ensureRoomPolling() {
        val conversationId = roomPollingConversationId ?: return
        if (
            _state.value.currentUserId.isBlank() ||
            _state.value.activeConversationId != conversationId ||
            isRealtimeConnected() ||
            roomInitialLoadJob?.isActive == true ||
            roomPollingJob?.isActive == true
        ) {
            return
        }
        roomPollingJob = viewModelScope.launch(ioDispatcher) {
            while (isActive) {
                delay(roomPollIntervalMillis)
                loadRoomSnapshot(conversationId, initial = false)
            }
        }
    }

    private fun refreshRoom() {
        val conversationId = _state.value.activeConversationId ?: return
        viewModelScope.launch(ioDispatcher) {
            loadRoomSnapshot(conversationId, initial = _state.value.messages.isEmpty())
        }
    }

    private suspend fun loadRoomSnapshot(conversationId: String, initial: Boolean) {
        if (_state.value.activeConversationId != conversationId) return
        if (initial) {
            _state.update { it.copy(isLoadingRoom = true, roomError = null) }
        }
        val detailResult = chatRepository.getConversation(conversationId)
        val messagesResult = chatRepository.listMessages(conversationId)
        if (_state.value.activeConversationId != conversationId) return

        detailResult.onSuccess { detail ->
            _state.update { current ->
                if (current.activeConversationId != conversationId) current
                else current.copy(
                    activeConversation = detail,
                    renameDraft = detail.name.orEmpty(),
                )
            }
        }
        messagesResult.fold(
            onSuccess = { page ->
                val chronological = page.items.asReversed()
                val previousIds = _state.value.messages.mapTo(mutableSetOf()) { it.messageId }
                _state.update { current ->
                    if (current.activeConversationId != conversationId) {
                        current
                    } else {
                        val merged = if (initial) {
                            mergeLatestMessages(chronological, current.messages)
                        } else {
                            mergeLatestMessages(current.messages, chronological)
                        }
                        current.copy(
                            messages = merged,
                            messagesNextCursor =
                                if (initial) page.nextCursor else current.messagesNextCursor,
                            messagesHasMore =
                                if (initial) page.hasMore else current.messagesHasMore,
                            isLoadingRoom = false,
                            roomError = detailResult.exceptionOrNull()?.userMessage(
                                "会话信息加载失败",
                            ),
                        )
                    }
                }
                val receivedNewMessage = chronological.any { it.messageId !in previousIds }
                if (initial || receivedNewMessage) {
                    markRead(conversationId, _state.value.messages.lastOrNull()?.messageId)
                }
            },
            onFailure = { cause ->
                _state.update { current ->
                    if (current.activeConversationId != conversationId) current
                    else current.copy(
                        isLoadingRoom = false,
                        roomError = cause.userMessage("消息加载失败"),
                    )
                }
            },
        )
        if (messagesResult.isFailure && detailResult.isFailure) {
            _state.update { current ->
                if (current.activeConversationId != conversationId) current
                else current.copy(
                    isLoadingRoom = false,
                    roomError = messagesResult.exceptionOrNull()?.userMessage("会话加载失败"),
                )
            }
        }
    }

    private fun loadOlderMessages() {
        val snapshot = _state.value
        val conversationId = snapshot.activeConversationId ?: return
        val cursor = snapshot.messagesNextCursor
        if (
            snapshot.isLoadingOlderMessages ||
            !snapshot.messagesHasMore ||
            cursor.isNullOrBlank()
        ) {
            return
        }
        _state.update { it.copy(isLoadingOlderMessages = true, roomError = null) }
        viewModelScope.launch(ioDispatcher) {
            chatRepository.listMessages(conversationId, cursor).fold(
                onSuccess = { page ->
                    val olderChronological = page.items.asReversed()
                    _state.update { current ->
                        if (current.activeConversationId != conversationId) {
                            current
                        } else {
                            current.copy(
                                messages = (olderChronological + current.messages)
                                    .distinctBy(RemoteChatMessage::messageId),
                                messagesNextCursor = page.nextCursor,
                                messagesHasMore = page.hasMore,
                                isLoadingOlderMessages = false,
                            )
                        }
                    }
                },
                onFailure = { cause ->
                    _state.update { current ->
                        if (current.activeConversationId != conversationId) current
                        else current.copy(
                            isLoadingOlderMessages = false,
                            roomError = cause.userMessage("更早消息加载失败"),
                        )
                    }
                },
            )
        }
    }

    private fun sendText(retry: Boolean) {
        val snapshot = _state.value
        val conversationId = snapshot.activeConversationId ?: return
        if (!snapshot.canSendMessage) return
        val outgoing = if (retry) {
            snapshot.outgoingText?.takeIf { !it.isSending && it.error != null } ?: return
        } else {
            if (snapshot.outgoingText != null) return
            val text = snapshot.draft.trim()
            if (text.isBlank()) return
            OutgoingTextState(
                conversationId = conversationId,
                text = text,
                clientMessageId = uuidProvider(),
                isSending = true,
            )
        }
        val sending = outgoing.copy(isSending = true, error = null)
        _state.update { it.copy(outgoingText = sending, roomError = null) }
        viewModelScope.launch(ioDispatcher) {
            chatRepository.sendText(
                conversationId = sending.conversationId,
                text = sending.text,
                clientMessageId = sending.clientMessageId,
            ).fold(
                onSuccess = { message ->
                    roomDrafts[conversationId] = ""
                    _state.update { current ->
                        if (
                            current.outgoingText?.clientMessageId != sending.clientMessageId
                        ) {
                            current
                        } else {
                            current.copy(
                                messages = appendServerMessage(current.messages, message),
                                draft = "",
                                outgoingText = null,
                            )
                        }
                    }
                    markRead(conversationId, message.messageId)
                },
                onFailure = { cause ->
                    _state.update { current ->
                        if (
                            current.outgoingText?.clientMessageId != sending.clientMessageId
                        ) {
                            current
                        } else {
                            current.copy(
                                outgoingText = sending.copy(
                                    isSending = false,
                                    error = cause.userMessage("发送失败"),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }

    private fun openShareComposer(video: com.example.flower_show.model.ProfileVideo) {
        shareSearchJob?.cancel()
        _state.update {
            it.copy(
                shareComposer = ChatShareComposerState(
                    video = video,
                    isLoadingCandidates = true,
                ),
                actionError = null,
            )
        }
        searchShareCandidates(query = "")
    }

    private fun updateShareQuery(query: String) {
        _state.update { current ->
            val composer = current.shareComposer ?: return@update current
            current.copy(
                shareComposer = composer.copy(
                    query = query,
                    isLoadingCandidates = true,
                    error = null,
                ),
            )
        }
        shareSearchJob?.cancel()
        shareSearchJob = viewModelScope.launch(ioDispatcher) {
            delay(ContactSearchDebounceMillis)
            loadMutualContacts(query) { candidates, error ->
                _state.update { current ->
                    val composer = current.shareComposer
                    if (composer == null || composer.query != query) current
                    else current.copy(
                        shareComposer = composer.copy(
                            candidates = candidates,
                            isLoadingCandidates = false,
                            error = error,
                        ),
                    )
                }
            }
        }
    }

    private fun searchShareCandidates(query: String) {
        shareSearchJob = viewModelScope.launch(ioDispatcher) {
            loadMutualContacts(query) { candidates, error ->
                _state.update { current ->
                    val composer = current.shareComposer
                    if (composer == null || composer.query != query) current
                    else current.copy(
                        shareComposer = composer.copy(
                            candidates = candidates,
                            isLoadingCandidates = false,
                            error = error,
                        ),
                    )
                }
            }
        }
    }

    private fun selectShareRecipient(userId: String) {
        _state.update { current ->
            val composer = current.shareComposer ?: return@update current
            if (composer.candidates.none { it.id == userId }) current
            else current.copy(
                shareComposer = composer.copy(
                    selectedUserId = userId,
                    clientMessageId =
                        if (composer.selectedUserId == userId) composer.clientMessageId else null,
                    error = null,
                ),
            )
        }
    }

    private fun sendVideoShare() {
        val composer = _state.value.shareComposer ?: return
        val userId = composer.selectedUserId ?: return
        if (composer.isSending) return
        val clientMessageId = composer.clientMessageId ?: uuidProvider()
        val sending = composer.copy(
            clientMessageId = clientMessageId,
            isSending = true,
            error = null,
        )
        _state.update { it.copy(shareComposer = sending) }
        viewModelScope.launch(ioDispatcher) {
            val directResult = chatRepository.createOrResolveDirectConversation(userId)
            val detail = directResult.getOrElse { cause ->
                setShareFailure(clientMessageId, cause.userMessage("无法创建私聊"))
                return@launch
            }
            chatRepository.sendVideoShare(
                conversationId = detail.conversationId,
                contentId = sending.video.id,
                clientMessageId = clientMessageId,
            ).fold(
                onSuccess = { message ->
                    _state.update { current ->
                        if (current.shareComposer?.clientMessageId != clientMessageId) current
                        else current.copy(
                            shareComposer = null,
                            messages = if (current.activeConversationId == detail.conversationId) {
                                appendServerMessage(current.messages, message)
                            } else {
                                current.messages
                            },
                        )
                    }
                    effectChannel.send(ChatEffect.VideoShared(detail.conversationId))
                },
                onFailure = { cause ->
                    setShareFailure(clientMessageId, cause.userMessage("视频分享失败"))
                },
            )
        }
    }

    private fun setShareFailure(clientMessageId: String, message: String) {
        _state.update { current ->
            val composer = current.shareComposer
            if (composer?.clientMessageId != clientMessageId) current
            else current.copy(
                shareComposer = composer.copy(isSending = false, error = message),
            )
        }
    }

    private fun dismissShareComposer() {
        shareSearchJob?.cancel()
        shareSearchJob = null
        _state.update { it.copy(shareComposer = null) }
    }

    private fun openGroupComposer() {
        groupSearchJob?.cancel()
        _state.update {
            it.copy(
                groupComposer = GroupComposerState(isLoadingCandidates = true),
                groupActionError = null,
            )
        }
        searchGroupCandidates("")
    }

    private fun updateGroupName(name: String) {
        _state.update { current ->
            val composer = current.groupComposer ?: return@update current
            current.copy(groupComposer = composer.copy(name = name, error = null))
        }
    }

    private fun updateGroupQuery(query: String) {
        _state.update { current ->
            val composer = current.groupComposer ?: return@update current
            current.copy(
                groupComposer = composer.copy(
                    query = query,
                    isLoadingCandidates = true,
                    error = null,
                ),
            )
        }
        groupSearchJob?.cancel()
        groupSearchJob = viewModelScope.launch(ioDispatcher) {
            delay(ContactSearchDebounceMillis)
            loadMutualContacts(query) { candidates, error ->
                _state.update { current ->
                    val composer = current.groupComposer
                    if (composer == null || composer.query != query) current
                    else current.copy(
                        groupComposer = composer.copy(
                            candidates = candidates,
                            isLoadingCandidates = false,
                            error = error,
                        ),
                    )
                }
            }
        }
    }

    private fun searchGroupCandidates(query: String) {
        groupSearchJob = viewModelScope.launch(ioDispatcher) {
            loadMutualContacts(query) { candidates, error ->
                _state.update { current ->
                    val composer = current.groupComposer
                    if (composer == null || composer.query != query) current
                    else current.copy(
                        groupComposer = composer.copy(
                            candidates = candidates,
                            isLoadingCandidates = false,
                            error = error,
                        ),
                    )
                }
            }
        }
    }

    private fun toggleGroupMember(userId: String) {
        _state.update { current ->
            val composer = current.groupComposer ?: return@update current
            if (composer.candidates.none { it.id == userId }) current
            else current.copy(
                groupComposer = composer.copy(
                    selectedUserIds = composer.selectedUserIds.toggle(userId),
                    error = null,
                ),
            )
        }
    }

    private fun createGroup() {
        val composer = _state.value.groupComposer ?: return
        val name = composer.name.trim()
        if (name.isBlank()) {
            _state.update { current ->
                current.copy(groupComposer = composer.copy(error = "请输入群名称"))
            }
            return
        }
        if (composer.selectedUserIds.isEmpty()) {
            _state.update { current ->
                current.copy(groupComposer = composer.copy(error = "请至少选择一位成员"))
            }
            return
        }
        if (composer.isCreating) return
        _state.update {
            it.copy(groupComposer = composer.copy(isCreating = true, error = null))
        }
        viewModelScope.launch(ioDispatcher) {
            chatRepository.createGroupConversation(
                name = name,
                memberUserIds = composer.selectedUserIds.toList(),
            ).fold(
                onSuccess = { detail ->
                    _state.update {
                        it.copy(
                            groupComposer = null,
                            activeConversationId = detail.conversationId,
                            activeConversation = detail,
                        )
                    }
                    effectChannel.send(ChatEffect.GroupCreated(detail.conversationId))
                },
                onFailure = { cause ->
                    _state.update { current ->
                        current.copy(
                            groupComposer = current.groupComposer?.copy(
                                isCreating = false,
                                error = cause.userMessage("创建群聊失败"),
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun dismissGroupComposer() {
        groupSearchJob?.cancel()
        groupSearchJob = null
        _state.update { it.copy(groupComposer = null) }
    }

    private fun loadGroupInfo(conversationId: String) {
        if (conversationId.isBlank()) return
        _state.update { current ->
            val activeDetail = current.activeConversation
                ?.takeIf { it.conversationId == conversationId }
            current.copy(
                activeConversationId = conversationId,
                activeConversation = activeDetail,
                renameDraft = activeDetail?.name.orEmpty(),
                isLoadingGroupInfo = true,
                groupActionError = null,
            )
        }
        viewModelScope.launch(ioDispatcher) {
            refreshGroupDetail(conversationId, showLoading = true)
        }
    }

    private fun renameGroup() {
        val detail = mutableGroupForOwner() ?: return
        val name = _state.value.renameDraft.trim()
        if (name.isBlank()) {
            setGroupError("群名称不能为空")
            return
        }
        runGroupAction(detail.conversationId) {
            chatRepository.renameGroup(detail.conversationId, name)
        }
    }

    private fun prepareAddMembers(conversationId: String) {
        val detail = _state.value.activeConversation
        if (
            detail?.conversationId != conversationId ||
            detail.type != ChatConversationType.GROUP ||
            !canManage(detail)
        ) {
            setGroupError("只有群主可以添加成员")
            return
        }
        _state.update {
            it.copy(
                addMembers = AddMembersState(
                    conversationId = conversationId,
                    isLoadingCandidates = true,
                ),
                groupActionError = null,
            )
        }
        searchAddMemberCandidates(conversationId, "")
    }

    private fun updateAddMembersQuery(query: String) {
        _state.update { current ->
            val composer = current.addMembers ?: return@update current
            current.copy(
                addMembers = composer.copy(
                    query = query,
                    isLoadingCandidates = true,
                    error = null,
                ),
            )
        }
        val conversationId = _state.value.addMembers?.conversationId ?: return
        addMembersSearchJob?.cancel()
        addMembersSearchJob = viewModelScope.launch(ioDispatcher) {
            delay(ContactSearchDebounceMillis)
            loadAddMemberCandidates(conversationId, query)
        }
    }

    private fun searchAddMemberCandidates(conversationId: String, query: String) {
        addMembersSearchJob?.cancel()
        addMembersSearchJob = viewModelScope.launch(ioDispatcher) {
            loadAddMemberCandidates(conversationId, query)
        }
    }

    private suspend fun loadAddMemberCandidates(conversationId: String, query: String) {
        loadMutualContacts(query) { candidates, error ->
            val existingIds = _state.value.activeConversation
                ?.takeIf { it.conversationId == conversationId }
                ?.members
                .orEmpty()
                .mapTo(mutableSetOf()) { it.userId }
            _state.update { current ->
                val composer = current.addMembers
                if (
                    composer == null ||
                    composer.conversationId != conversationId ||
                    composer.query != query
                ) {
                    current
                } else {
                    current.copy(
                        addMembers = composer.copy(
                            candidates = candidates.filterNot { it.id in existingIds },
                            selectedUserIds =
                                composer.selectedUserIds.filterNotTo(mutableSetOf()) {
                                    it in existingIds
                                },
                            isLoadingCandidates = false,
                            error = error,
                        ),
                    )
                }
            }
        }
    }

    private fun toggleAddMember(userId: String) {
        _state.update { current ->
            val composer = current.addMembers ?: return@update current
            if (composer.candidates.none { it.id == userId }) current
            else current.copy(
                addMembers = composer.copy(
                    selectedUserIds = composer.selectedUserIds.toggle(userId),
                    error = null,
                ),
            )
        }
    }

    private fun addSelectedMembers() {
        val detail = mutableGroupForOwner() ?: return
        val composer = _state.value.addMembers
            ?.takeIf { it.conversationId == detail.conversationId }
            ?: return
        if (composer.selectedUserIds.isEmpty()) {
            _state.update {
                it.copy(addMembers = composer.copy(error = "请至少选择一位成员"))
            }
            return
        }
        if (composer.isAdding) return
        _state.update { it.copy(addMembers = composer.copy(isAdding = true, error = null)) }
        viewModelScope.launch(ioDispatcher) {
            chatRepository.addMembers(
                detail.conversationId,
                composer.selectedUserIds.toList(),
            ).fold(
                onSuccess = { returned ->
                    _state.update { current ->
                        current.copy(
                            activeConversation = returned,
                            addMembers = null,
                        )
                    }
                    refreshGroupDetail(detail.conversationId, showLoading = false)
                    effectChannel.send(ChatEffect.MembersAdded(detail.conversationId))
                },
                onFailure = { cause ->
                    _state.update { current ->
                        current.copy(
                            addMembers = current.addMembers?.copy(
                                isAdding = false,
                                error = cause.userMessage("添加成员失败"),
                            ),
                        )
                    }
                },
            )
        }
    }

    private fun dismissAddMembers() {
        addMembersSearchJob?.cancel()
        addMembersSearchJob = null
        _state.update { it.copy(addMembers = null) }
    }

    private fun removeMember(userId: String) {
        val detail = mutableGroupForOwner() ?: return
        if (userId == _state.value.currentUserId || userId == detail.ownerUserId) {
            setGroupError("群主不能移除自己")
            return
        }
        runGroupAction(detail.conversationId) {
            chatRepository.removeMember(detail.conversationId, userId)
        }
    }

    private fun transferGroupOwner(userId: String) {
        val detail = mutableGroupForOwner() ?: return
        if (userId == _state.value.currentUserId || detail.members.none { it.userId == userId }) {
            setGroupError("请选择其他群成员")
            return
        }
        runGroupAction(detail.conversationId) {
            chatRepository.transferGroupOwner(detail.conversationId, userId)
        }
    }

    private fun dissolveGroup() {
        val detail = mutableGroupForOwner() ?: return
        runGroupAction(detail.conversationId) {
            chatRepository.dissolveGroup(detail.conversationId)
        }
    }

    private fun leaveConversation() {
        val detail = _state.value.activeConversation ?: return
        if (detail.type != ChatConversationType.GROUP) return
        if (detail.state == ChatConversationState.DISSOLVED) {
            setGroupError("群聊已解散，只能查看历史消息")
            return
        }
        if (detail.ownerUserId == _state.value.currentUserId) {
            setGroupError("群主需先转让群主或解散群聊")
            return
        }
        if (_state.value.isGroupActionInProgress) return
        _state.update { it.copy(isGroupActionInProgress = true, groupActionError = null) }
        viewModelScope.launch(ioDispatcher) {
            chatRepository.leaveConversation(detail.conversationId).fold(
                onSuccess = {
                    _state.update { it.copy(isGroupActionInProgress = false) }
                    effectChannel.send(ChatEffect.ConversationExited)
                },
                onFailure = { cause ->
                    _state.update {
                        it.copy(
                            isGroupActionInProgress = false,
                            groupActionError = cause.userMessage("退出群聊失败"),
                        )
                    }
                },
            )
        }
    }

    private fun runGroupAction(
        conversationId: String,
        action: suspend () -> kotlin.Result<ChatConversationDetail>,
    ) {
        if (_state.value.isGroupActionInProgress) return
        _state.update { it.copy(isGroupActionInProgress = true, groupActionError = null) }
        viewModelScope.launch(ioDispatcher) {
            action().fold(
                onSuccess = { returned ->
                    _state.update {
                        it.copy(
                            activeConversation = returned,
                            renameDraft = returned.name.orEmpty(),
                        )
                    }
                    refreshGroupDetail(conversationId, showLoading = false)
                },
                onFailure = { cause ->
                    _state.update {
                        it.copy(
                            isGroupActionInProgress = false,
                            groupActionError = cause.userMessage("群操作失败"),
                        )
                    }
                },
            )
        }
    }

    private suspend fun refreshGroupDetail(conversationId: String, showLoading: Boolean) {
        if (showLoading) {
            _state.update { it.copy(isLoadingGroupInfo = true, groupActionError = null) }
        }
        chatRepository.getConversation(conversationId).fold(
            onSuccess = { detail ->
                _state.update { current ->
                    if (current.activeConversationId != conversationId) current
                    else current.copy(
                        activeConversation = detail,
                        renameDraft = detail.name.orEmpty(),
                        isLoadingGroupInfo = false,
                        isGroupActionInProgress = false,
                        groupActionError = null,
                    )
                }
            },
            onFailure = { cause ->
                _state.update { current ->
                    if (current.activeConversationId != conversationId) current
                    else current.copy(
                        isLoadingGroupInfo = false,
                        isGroupActionInProgress = false,
                        groupActionError = cause.userMessage("群资料刷新失败"),
                    )
                }
            },
        )
    }

    private fun mutableGroupForOwner(): ChatConversationDetail? {
        val snapshot = _state.value
        val detail = snapshot.activeConversation
        if (detail?.conversationId != snapshot.activeConversationId) {
            setGroupError("群资料仍在加载")
            return null
        }
        if (detail?.type != ChatConversationType.GROUP) {
            setGroupError("当前会话不是群聊")
            return null
        }
        if (detail.state == ChatConversationState.DISSOLVED) {
            setGroupError("群聊已解散，只能查看历史消息")
            return null
        }
        if (!canManage(detail)) {
            setGroupError("只有群主可以管理群聊")
            return null
        }
        return detail
    }

    private fun canManage(detail: ChatConversationDetail): Boolean =
        detail.ownerUserId == _state.value.currentUserId

    private fun setGroupError(message: String) {
        _state.update { it.copy(groupActionError = message) }
    }

    private suspend fun loadMutualContacts(
        query: String,
        update: (List<SocialProfile>, String?) -> Unit,
    ) {
        val userId = _state.value.currentUserId
        if (userId.isBlank()) {
            update(emptyList(), "登录状态已失效")
            return
        }
        when (
            val result = socialRepository.loadRelations(
                userId = userId,
                relation = "following",
                keyword = query,
            )
        ) {
            is SocialResult.Success -> update(
                result.data.items.filter(SocialProfile::isMutual),
                null,
            )
            is SocialResult.Error -> update(emptyList(), result.message)
            SocialResult.Loading -> update(emptyList(), null)
        }
    }

    private fun handleRealtimeConnectionState(connectionState: ChatRealtimeConnectionState) {
        if (_state.value.currentUserId.isBlank()) return
        if (connectionState == ChatRealtimeConnectionState.Connected) {
            cancelConversationPollingJob()
            roomPollingJob?.cancel()
            roomPollingJob = null
            syncAfterRealtimeConnected()
        } else {
            ensureConversationPolling()
            ensureRoomPolling()
        }
    }

    private fun syncAfterRealtimeConnected() {
        realtimeSyncJob?.cancel()
        realtimeSyncJob = viewModelScope.launch(ioDispatcher) {
            loadConversationPage(
                reset = true,
                showLoading = _state.value.conversations.isEmpty(),
                mergeExisting = true,
            )
            val conversationId = _state.value.activeConversationId
            if (
                !conversationId.isNullOrBlank() &&
                roomPollingConversationId == conversationId &&
                roomInitialLoadJob?.isActive != true
            ) {
                loadRoomSnapshot(
                    conversationId = conversationId,
                    initial = _state.value.messages.isEmpty(),
                )
            }
        }
    }

    private fun handleRealtimeMessage(event: ChatRealtimeEvent.MessageCreated) {
        val message = event.message
        if (_state.value.currentUserId.isBlank() || !rememberRealtimeMessage(message.messageId)) {
            return
        }
        val isActiveRoom = _state.value.activeConversationId == message.conversationId
        val conversationKnown = _state.value.conversations.any {
            it.conversationId == message.conversationId
        }
        if (!isActiveRoom && !conversationKnown) {
            // 未知会话（如被陌生人发起私聊）：拉详情构造摘要插入列表顶部，
            // 而不是等下一轮列表轮询；事件 payload 不含会话摘要，需走详情接口。
            fetchConversationForRealtimeMessage(message)
        }
        var shouldMarkRead = false
        _state.update { current ->
            val isActiveRoom = current.activeConversationId == message.conversationId
            val roomAlreadyContainsMessage = current.messages.any {
                it.messageId == message.messageId
            }
            val summaryAlreadyContainsMessage = current.conversations.any {
                it.conversationId == message.conversationId &&
                    it.lastMessage?.messageId == message.messageId
            }
            val shouldIncreaseUnread =
                !isActiveRoom &&
                    !roomAlreadyContainsMessage &&
                    !summaryAlreadyContainsMessage &&
                    message.senderUserId != current.currentUserId
            shouldMarkRead = isActiveRoom && !roomAlreadyContainsMessage
            val conversations = current.conversations.updateForRealtimeMessage(
                event = event,
                isActiveRoom = isActiveRoom,
                increaseUnread = shouldIncreaseUnread,
            )
            current.copy(
                conversations = conversations,
                messages = if (isActiveRoom && !roomAlreadyContainsMessage) {
                    current.messages + message
                } else {
                    current.messages
                },
            )
        }
        if (shouldMarkRead) {
            markRead(message.conversationId, message.messageId)
        }
    }

    private fun rememberRealtimeMessage(messageId: String): Boolean {
        if (!seenRealtimeMessageIds.add(messageId)) {
            return false
        }
        while (seenRealtimeMessageIds.size > RealtimeMessageDeduplicationLimit) {
            val oldest = seenRealtimeMessageIds.iterator()
            if (oldest.hasNext()) {
                oldest.next()
                oldest.remove()
            }
        }
        return true
    }

    /**
     * 收到属于未知会话的实时消息时，拉会话详情构造摘要并插入列表顶部。
     *
     * 同一会话的并发拉取只进行一次；失败时下一轮列表轮询会全量对齐。
     */
    private fun fetchConversationForRealtimeMessage(message: RemoteChatMessage) {
        val conversationId = message.conversationId
        if (!conversationsBeingFetched.add(conversationId)) return
        viewModelScope.launch(ioDispatcher) {
            try {
                chatRepository.getConversation(conversationId).onSuccess { detail ->
                    val unread =
                        if (message.senderUserId != _state.value.currentUserId) 1L else 0L
                    val summary = detail.toRealtimeSummary(
                        myUserId = _state.value.currentUserId,
                        lastMessage = message,
                        unreadCount = unread,
                    )
                    _state.update { current ->
                        if (current.conversations.any { it.conversationId == conversationId }) {
                            current
                        } else {
                            current.copy(
                                conversations = listOf(summary) + current.conversations,
                            )
                        }
                    }
                }
            } finally {
                conversationsBeingFetched.remove(conversationId)
            }
        }
    }

    private fun ChatConversationDetail.toRealtimeSummary(
        myUserId: String,
        lastMessage: RemoteChatMessage,
        unreadCount: Long,
    ): ChatConversationSummary {
        val otherMember = members.firstOrNull { it.userId != myUserId }
            ?: members.firstOrNull()
        // 与服务端列表接口同款处理：owner 在前、按 joinedAt 排序、最多取前 4 个成员做头像组。
        val avatarMembers = members
            .sortedWith(
                compareByDescending<ChatMember> { it.role == ChatMemberRole.OWNER }
                    .thenBy { it.joinedAt },
            )
            .take(4)
            .map { ChatAvatarMember(it.userId, it.nickname, it.avatarUrl) }
        return ChatConversationSummary(
            conversationId = conversationId,
            type = type,
            name = name,
            displayName = if (type == ChatConversationType.DIRECT) {
                otherMember?.nickname ?: name
            } else {
                name
            },
            displayAvatarUrl = if (type == ChatConversationType.DIRECT) {
                otherMember?.avatarUrl
            } else {
                null
            },
            ownerUserId = ownerUserId,
            state = state,
            dissolvedAt = dissolvedAt,
            lastMessage = lastMessage,
            unreadCount = unreadCount,
            updatedAt = updatedAt,
            avatarMembers = avatarMembers,
        )
    }

    private fun isRealtimeConnected(): Boolean =
        realtimeClient.connectionState.value == ChatRealtimeConnectionState.Connected

    private fun markRead(conversationId: String, messageId: String?) {
        viewModelScope.launch(ioDispatcher) {
            chatRepository.markRead(conversationId, messageId).onSuccess { receipt ->
                _state.update { current ->
                    current.copy(
                        conversations = current.conversations.map { conversation ->
                            if (conversation.conversationId == conversationId) {
                                conversation.copy(unreadCount = receipt.unreadCount)
                            } else {
                                conversation
                            }
                        },
                    )
                }
            }
        }
    }

    private fun dismissErrors() {
        _state.update {
            it.copy(
                conversationError = null,
                roomError = null,
                actionError = null,
                groupActionError = null,
                outgoingText = null,
                shareComposer = it.shareComposer?.copy(error = null),
                groupComposer = it.groupComposer?.copy(error = null),
                addMembers = it.addMembers?.copy(error = null),
            )
        }
    }

    override fun onCleared() {
        stopConversationPolling()
        roomPollingConversationId = null
        roomInitialLoadJob?.cancel()
        roomPollingJob?.cancel()
        realtimeSyncJob?.cancel()
        realtimeClient.disconnect()
        shareSearchJob?.cancel()
        groupSearchJob?.cancel()
        addMembersSearchJob?.cancel()
        super.onCleared()
    }

    class Factory(
        private val chatRepository: IChatRepository,
        private val socialRepository: ISocialRepository,
        private val realtimeClient: ChatRealtimeClient = NoOpChatRealtimeClient,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ChatViewModel::class.java))
            return ChatViewModel(
                chatRepository = chatRepository,
                socialRepository = socialRepository,
                realtimeClient = realtimeClient,
            ) as T
        }
    }

    private companion object {
        const val ConversationPollIntervalMillis = 7_500L
        const val RoomPollIntervalMillis = 2_500L
        const val ContactSearchDebounceMillis = 300L
        const val RealtimeMessageDeduplicationLimit = 4_096
    }
}

private fun List<ChatConversationSummary>.updateForRealtimeMessage(
    event: ChatRealtimeEvent.MessageCreated,
    isActiveRoom: Boolean,
    increaseUnread: Boolean,
): List<ChatConversationSummary> {
    val index = indexOfFirst { it.conversationId == event.message.conversationId }
    if (index < 0) return this
    val existing = get(index)
    val updated = existing.copy(
        lastMessage = event.message,
        unreadCount = when {
            isActiveRoom -> 0L
            increaseUnread -> existing.unreadCount + 1L
            else -> existing.unreadCount
        },
        updatedAt = event.occurredAt,
    )
    return listOf(updated) + filterIndexed { candidateIndex, _ -> candidateIndex != index }
}

private fun mergeConversationSummaries(
    remote: List<ChatConversationSummary>,
    existing: List<ChatConversationSummary>,
): List<ChatConversationSummary> {
    val existingById = existing.associateBy(ChatConversationSummary::conversationId)
    val remoteIds = remote.mapTo(mutableSetOf(), ChatConversationSummary::conversationId)
    return remote.map { serverSummary ->
        existingById[serverSummary.conversationId]
            ?.takeIf { it.updatedAt > serverSummary.updatedAt }
            ?: serverSummary
    } + existing.filterNot { it.conversationId in remoteIds }
}

private fun mergeLatestMessages(
    existing: List<RemoteChatMessage>,
    latestChronological: List<RemoteChatMessage>,
): List<RemoteChatMessage> {
    val latestById = latestChronological.associateBy(RemoteChatMessage::messageId)
    val existingIds = existing.mapTo(mutableSetOf(), RemoteChatMessage::messageId)
    return existing.map { latestById[it.messageId] ?: it } +
        latestChronological.filterNot { it.messageId in existingIds }
}

private fun appendServerMessage(
    existing: List<RemoteChatMessage>,
    message: RemoteChatMessage,
): List<RemoteChatMessage> =
    existing.filterNot { it.messageId == message.messageId } + message

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

private fun Throwable.userMessage(fallback: String): String =
    message?.takeIf(String::isNotBlank) ?: fallback
