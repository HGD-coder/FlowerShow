package com.example.flower_show.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.flower_show.data.auth.AuthUser
import com.example.flower_show.data.repository.FollowResult
import com.example.flower_show.data.repository.ISocialRepository
import com.example.flower_show.model.CursorPage
import com.example.flower_show.model.Result
import com.example.flower_show.model.SocialNotification
import com.example.flower_show.model.SocialProfile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class SocialViewModel(
    private val repository: ISocialRepository = UnavailableSocialRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    initialState: SocialState = SocialState(),
) : ViewModel() {
    private val _state = MutableStateFlow(initialState)
    val state: StateFlow<SocialState> = _state.asStateFlow()

    private var boundUserId: String? = null
    private var relationSearchJob: Job? = null

    // followJobs 在主线程写入（toggleFollow）、在 IO 协程结束时移除，
    // 必须用并发容器，否则 HashMap 跨线程读写是未定义行为。
    private val followJobs = ConcurrentHashMap<String, Job>()

    // 个人资料相关请求的代数计数：慢请求的错误/加载态不应覆盖更新的请求状态。
    @Volatile private var profileRequestGeneration = 0

    fun dispatch(intent: SocialIntent) {
        when (intent) {
            is SocialIntent.BindAuthenticatedUser -> bindAuthenticatedUser(intent.user)
            is SocialIntent.LoadProfile -> loadOtherProfile(intent.userId)
            is SocialIntent.SelectProfileTab -> selectProfileTab(intent.tab)
            is SocialIntent.SelectRelationTab -> selectRelationTab(intent.tab)
            is SocialIntent.UpdateRelationQuery -> updateRelationQuery(intent.query)
            is SocialIntent.ToggleFollow -> toggleFollow(intent.userId)
            is SocialIntent.SetWorkVisibility -> setWorkVisibility(intent.videoId, intent.visible)
            is SocialIntent.SelectMessageTab ->
                _state.update { it.copy(selectedMessageTab = intent.tab) }
            SocialIntent.LoadNotifications -> loadNotifications(reset = true)
            SocialIntent.LoadMoreNotifications -> loadNotifications(reset = false)
            is SocialIntent.MarkNotificationRead -> markNotificationRead(intent.notificationId)
            SocialIntent.MarkAllNotificationsRead -> markAllNotificationsRead()
            SocialIntent.DismissError -> _state.update { it.copy(error = null) }
            SocialIntent.UnbindUser -> unbindUser()
        }
    }

    private fun unbindUser() {
        boundUserId = null
        relationSearchJob?.cancel()
        relationSearchJob = null
        followJobs.values.forEach { it.cancel() }
        followJobs.clear()
        profileRequestGeneration++
        _state.value = SocialState()
    }

    private fun bindAuthenticatedUser(user: AuthUser) {
        if (boundUserId == user.userId) return
        boundUserId = user.userId
        _state.update { current ->
            current.copy(
                myProfile = SocialProfile.Empty.copy(
                    id = user.userId,
                    account = user.username,
                    nickname = user.nickname,
                    avatarUrl = user.avatarUrl.orEmpty(),
                ),
                people = emptyList(),
                relationProfiles = emptyList(),
                notifications = emptyList(),
                notificationUnreadCount = 0,
                notificationsNextCursor = null,
                notificationsHasMore = true,
            )
        }
        loadMyProfile(user.userId)
        loadProfileVideos(user.userId, ProfileContentTab.Works)
    }

    private fun loadMyProfile(userId: String) {
        val generation = ++profileRequestGeneration
        _state.update { it.copy(isProfileLoading = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.loadProfile(userId)) {
                is Result.Success -> _state.update { current ->
                    current.copy(
                        myProfile = result.data.copy(
                            works = current.myProfile.works,
                            likedVideos = current.myProfile.likedVideos,
                            collectedVideos = current.myProfile.collectedVideos,
                        ),
                        isProfileLoading = false,
                    )
                }
                is Result.Error -> _state.update {
                    // 慢请求的错误不应覆盖更新的请求（如已切换到他人资料页）。
                    if (generation != profileRequestGeneration) {
                        it
                    } else {
                        it.copy(isProfileLoading = false, error = result.message)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun loadOtherProfile(userId: String) {
        if (userId.isBlank() || userId == _state.value.myProfile.id) return
        val generation = ++profileRequestGeneration
        _state.update { it.copy(isProfileLoading = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            val profileResult = repository.loadProfile(userId)
            val videosResult = repository.loadProfileVideos(
                userId = userId,
                tab = ProfileContentTab.Works.apiName,
            )
            when (profileResult) {
                is Result.Success -> {
                    val videos = when (videosResult) {
                        is Result.Success -> videosResult.data.items
                        else -> emptyList()
                    }
                    val profile = profileResult.data.copy(works = videos)
                    _state.update {
                        it.copy(
                            people = it.people.mergeProfile(profile),
                            isProfileLoading = false,
                            error = (videosResult as? Result.Error)?.message,
                        )
                    }
                }
                is Result.Error -> _state.update {
                    if (generation != profileRequestGeneration) {
                        it
                    } else {
                        it.copy(isProfileLoading = false, error = profileResult.message)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun selectProfileTab(tab: ProfileContentTab) {
        _state.update { it.copy(selectedProfileTab = tab) }
        val userId = _state.value.myProfile.id
        if (userId.isNotBlank()) loadProfileVideos(userId, tab)
    }

    private fun loadProfileVideos(userId: String, tab: ProfileContentTab) {
        val generation = ++profileRequestGeneration
        _state.update { it.copy(isProfileLoading = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (
                val result = repository.loadProfileVideos(
                    userId = userId,
                    tab = tab.apiName,
                )
            ) {
                is Result.Success -> _state.update { current ->
                    val profile = current.myProfile
                    val videos = result.data.items
                    current.copy(
                        myProfile = when (tab) {
                            ProfileContentTab.Works -> profile.copy(works = videos)
                            ProfileContentTab.Likes -> profile.copy(likedVideos = videos)
                            ProfileContentTab.Collections -> profile.copy(collectedVideos = videos)
                        },
                        isProfileLoading = false,
                    )
                }
                is Result.Error -> _state.update {
                    if (generation != profileRequestGeneration) {
                        it
                    } else {
                        it.copy(isProfileLoading = false, error = result.message)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun selectRelationTab(tab: RelationListTab) {
        relationSearchJob?.cancel()
        _state.update {
            it.copy(selectedRelationTab = tab, relationQuery = "", relationProfiles = emptyList())
        }
        loadRelations()
    }

    private fun updateRelationQuery(query: String) {
        _state.update { it.copy(relationQuery = query) }
        relationSearchJob?.cancel()
        relationSearchJob = viewModelScope.launch(ioDispatcher) {
            delay(RelationSearchDebounceMs)
            loadRelations()
        }
    }

    private fun loadRelations() {
        val snapshot = _state.value
        val userId = snapshot.myProfile.id
        if (userId.isBlank()) return
        val tab = snapshot.selectedRelationTab
        val query = snapshot.relationQuery
        _state.update { it.copy(isRelationLoading = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            when (
                val result = repository.loadRelations(
                    userId = userId,
                    relation = tab.apiName,
                    keyword = query,
                )
            ) {
                is Result.Success -> _state.update { current ->
                    if (current.selectedRelationTab != tab || current.relationQuery != query) {
                        current
                    } else {
                        current.copy(
                            relationProfiles = result.data.items,
                            people = current.people.mergeProfiles(result.data.items),
                            isRelationLoading = false,
                        )
                    }
                }
                is Result.Error -> _state.update { current ->
                    // 与成功分支相同的新鲜度检查：慢请求的错误不应盖到已切换的 tab 上。
                    if (current.selectedRelationTab != tab || current.relationQuery != query) {
                        current
                    } else {
                        current.copy(isRelationLoading = false, error = result.message)
                    }
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun toggleFollow(userId: String) {
        if (userId.isBlank() || followJobs[userId]?.isActive == true) return
        val before = _state.value.profile(userId)
            ?: _state.value.relationProfiles.firstOrNull { it.id == userId }
            ?: return
        val actorUserId = _state.value.myProfile.id
        if (actorUserId.isBlank() || actorUserId == userId) return

        val nowFollowing = !before.isFollowing
        applyOptimisticFollow(userId, nowFollowing)
        followJobs[userId] = viewModelScope.launch(ioDispatcher) {
            when (val result = repository.setFollowing(actorUserId, userId, nowFollowing)) {
                is Result.Success -> applyFollowResult(result.data)
                is Result.Error -> _state.update { current ->
                    // 回滚基于当前状态施加反向 delta，而不是回填操作发起时的旧快照：
                    // 若期间其他关注操作已成功，回填快照会把这些成功计数一并抹掉。
                    val rollbackDelta = if (nowFollowing) -1 else 1
                    current.copy(
                        myProfile = current.myProfile.copy(
                            followingCount =
                                (current.myProfile.followingCount + rollbackDelta).coerceAtLeast(0),
                        ),
                        people = current.people.map { profile ->
                            if (profile.id == userId) {
                                profile.copy(
                                    isFollowing = !nowFollowing,
                                    followersCount =
                                        (profile.followersCount + rollbackDelta).coerceAtLeast(0),
                                )
                            } else {
                                profile
                            }
                        },
                        relationProfiles = current.relationProfiles.map { profile ->
                            if (profile.id == userId) {
                                profile.copy(
                                    isFollowing = !nowFollowing,
                                    followersCount =
                                        (profile.followersCount + rollbackDelta).coerceAtLeast(0),
                                )
                            } else {
                                profile
                            }
                        },
                        error = result.message,
                    )
                }
                Result.Loading -> Unit
            }
            // 请求结束后移除记录，避免 Map 随操作次数无界增长。
            followJobs.remove(userId)
        }
    }

    private fun applyOptimisticFollow(userId: String, following: Boolean) {
        _state.update { current ->
            val delta = if (following) 1 else -1
            current.copy(
                myProfile = current.myProfile.copy(
                    followingCount = (current.myProfile.followingCount + delta).coerceAtLeast(0),
                ),
                people = current.people.updateProfile(userId) { profile ->
                    profile.copy(
                        isFollowing = following,
                        followersCount = (profile.followersCount + delta).coerceAtLeast(0),
                    )
                },
                relationProfiles = current.relationProfiles.updateProfile(userId) { profile ->
                    profile.copy(
                        isFollowing = following,
                        followersCount = (profile.followersCount + delta).coerceAtLeast(0),
                    )
                },
            )
        }
    }

    private fun applyFollowResult(result: FollowResult) {
        _state.update { current ->
            val update: (SocialProfile) -> SocialProfile = { profile ->
                profile.copy(
                    isFollowing = result.following,
                    followsMe = result.followedBy,
                    followersCount = result.followerCount,
                )
            }
            current.copy(
                myProfile = current.myProfile.copy(followingCount = result.actorFollowingCount),
                people = current.people.updateProfile(result.targetUserId, update),
                relationProfiles = current.relationProfiles.updateProfile(result.targetUserId, update),
            )
        }
    }

    private fun setWorkVisibility(videoId: String, visible: Boolean) {
        val userId = _state.value.myProfile.id
        if (userId.isBlank()) return
        val before = _state.value
        _state.update { current ->
            current.copy(
                myProfile = current.myProfile.copy(
                    works = current.myProfile.works.map { video ->
                        if (video.id == videoId) video.copy(isVisibleOnProfile = visible) else video
                    },
                ),
            )
        }
        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.setWorkVisible(userId, videoId, visible)) {
                is Result.Success -> _state.update { current ->
                    current.copy(
                        myProfile = current.myProfile.copy(
                            works = current.myProfile.works.map { video ->
                                if (video.id == videoId) {
                                    video.copy(isVisibleOnProfile = result.data)
                                } else {
                                    video
                                }
                            },
                        ),
                    )
                }
                is Result.Error -> _state.update { current ->
                    val oldVideo = before.myProfile.works.firstOrNull { it.id == videoId }
                    current.copy(
                        myProfile = current.myProfile.copy(
                            works = current.myProfile.works.map { video ->
                                if (video.id == videoId && oldVideo != null) oldVideo else video
                            },
                        ),
                        error = result.message,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun loadNotifications(reset: Boolean) {
        val current = _state.value
        if (current.isNotificationsLoading || (!reset && !current.notificationsHasMore)) return
        val cursor = if (reset) null else current.notificationsNextCursor
        _state.update { it.copy(isNotificationsLoading = true, error = null) }
        viewModelScope.launch(ioDispatcher) {
            val pageResult = repository.loadNotifications(cursor)
            val unreadResult = repository.loadNotificationUnreadCount()
            when (pageResult) {
                is Result.Success -> _state.update { latest ->
                    val combined = if (reset) {
                        pageResult.data.items
                    } else {
                        (latest.notifications + pageResult.data.items).distinctBy { it.id }
                    }
                    latest.copy(
                        notifications = combined,
                        notificationsNextCursor = pageResult.data.nextCursor,
                        notificationsHasMore = pageResult.data.hasMore,
                        notificationUnreadCount =
                            (unreadResult as? Result.Success)?.data
                                ?: combined.count { !it.isRead },
                        isNotificationsLoading = false,
                        error = (unreadResult as? Result.Error)?.message,
                    )
                }
                is Result.Error -> _state.update {
                    it.copy(isNotificationsLoading = false, error = pageResult.message)
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun markNotificationRead(notificationId: String) {
        val before = _state.value
        val notification = before.notifications.firstOrNull { it.id == notificationId } ?: return
        if (notification.isRead) return
        _state.update { current ->
            current.copy(
                notifications = current.notifications.map {
                    if (it.id == notificationId) it.copy(isRead = true) else it
                },
                notificationUnreadCount = (current.notificationUnreadCount - 1).coerceAtLeast(0),
            )
        }
        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.markNotificationRead(notificationId)) {
                is Result.Success ->
                    _state.update { it.copy(notificationUnreadCount = result.data) }
                is Result.Error -> _state.update { current ->
                    // 回滚用增量修正而不是回填旧快照：期间若其他已读操作成功，
                    // 回填会把它们的计数一并抹掉。本条乐观更新 -1，失败回滚 +1。
                    current.copy(
                        notifications = current.notifications.map {
                            if (it.id == notificationId) it.copy(isRead = false) else it
                        },
                        notificationUnreadCount = current.notificationUnreadCount + 1,
                        error = result.message,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    private fun markAllNotificationsRead() {
        val before = _state.value
        if (before.notificationUnreadCount == 0) return
        _state.update { current ->
            current.copy(
                notifications = current.notifications.map { it.copy(isRead = true) },
                notificationUnreadCount = 0,
            )
        }
        viewModelScope.launch(ioDispatcher) {
            when (val result = repository.markAllNotificationsRead()) {
                is Result.Success ->
                    _state.update { it.copy(notificationUnreadCount = result.data) }
                is Result.Error -> _state.update { current ->
                    // 增量回滚：把操作发起时的未读数加回来，而不是回填旧快照，
                    // 避免覆盖期间其他成功操作造成的计数变化。
                    // 只回滚“发起时已存在”的通知，期间新到达的通知保持原状态。
                    val rollbackIds = before.notifications.mapTo(mutableSetOf()) { it.id }
                    current.copy(
                        notifications = current.notifications.map {
                            if (it.id in rollbackIds) it.copy(isRead = false) else it
                        },
                        notificationUnreadCount = current.notificationUnreadCount +
                            before.notificationUnreadCount,
                        error = result.message,
                    )
                }
                Result.Loading -> Unit
            }
        }
    }

    class Factory(
        private val repository: ISocialRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SocialViewModel::class.java))
            return SocialViewModel(repository) as T
        }
    }

    companion object {
        private const val RelationSearchDebounceMs = 300L
    }
}

private val ProfileContentTab.apiName: String
    get() = when (this) {
        ProfileContentTab.Works -> "posts"
        ProfileContentTab.Likes -> "liked"
        ProfileContentTab.Collections -> "favorites"
    }

private val RelationListTab.apiName: String
    get() = when (this) {
        RelationListTab.Following -> "following"
        RelationListTab.Followers -> "followers"
    }

private fun List<SocialProfile>.mergeProfile(profile: SocialProfile): List<SocialProfile> =
    firstOrNull { it.id == profile.id }?.let { existing ->
        filterNot { it.id == profile.id } + profile.copy(
            works = profile.works.ifEmpty { existing.works },
            likedVideos = profile.likedVideos.ifEmpty { existing.likedVideos },
            collectedVideos = profile.collectedVideos.ifEmpty { existing.collectedVideos },
        )
    } ?: (this + profile)

private fun List<SocialProfile>.mergeProfiles(profiles: List<SocialProfile>): List<SocialProfile> =
    profiles.fold(this) { result, profile -> result.mergeProfile(profile) }

private fun List<SocialProfile>.updateProfile(
    userId: String,
    transform: (SocialProfile) -> SocialProfile,
): List<SocialProfile> = map { if (it.id == userId) transform(it) else it }

/**
 * Keeps the old no-arg constructor source-compatible for isolated UI/tests.
 * AppNavigation always supplies the AuthGraph-backed repository through Factory.
 */
private object UnavailableSocialRepository : ISocialRepository {
    private fun <T> unavailable(): Result<T> =
        Result.error("Social repository is not configured")

    override fun loadProfile(userId: String): Result<SocialProfile> = unavailable()

    override fun loadProfileVideos(
        userId: String,
        tab: String,
        page: Int,
        pageSize: Int,
    ) = unavailable<com.example.flower_show.data.repository.ProfileVideoPage>()

    override fun loadRelations(
        userId: String,
        relation: String,
        keyword: String,
        page: Int,
        pageSize: Int,
    ) = unavailable<com.example.flower_show.data.repository.SocialProfilePage>()

    override fun setFollowing(
        actorUserId: String,
        targetUserId: String,
        following: Boolean,
    ) = unavailable<FollowResult>()

    override fun setWorkVisible(
        userId: String,
        contentId: String,
        visible: Boolean,
    ) = unavailable<Boolean>()

    override fun loadNotifications(
        cursor: String?,
        pageSize: Int,
    ) = unavailable<CursorPage<SocialNotification>>()

    override fun loadNotificationUnreadCount() = unavailable<Int>()

    override fun markNotificationRead(notificationId: String) = unavailable<Int>()

    override fun markAllNotificationsRead() = unavailable<Int>()
}
