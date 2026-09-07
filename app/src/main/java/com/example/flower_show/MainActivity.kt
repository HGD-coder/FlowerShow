package com.example.flower_show

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.model.VideoItem
import com.example.flower_show.data.auth.AuthGraph
import com.example.flower_show.data.remote.chat.NoOpChatRealtimeClient
import com.example.flower_show.data.repository.RepositoryFactory
import com.example.flower_show.ui.screen.*
import com.example.flower_show.ui.theme.FlowerShowTheme
import com.example.flower_show.util.MetricsCollector
import com.example.flower_show.util.PerformanceDiagnostics
import com.example.flower_show.util.PerformanceExperimentConfig
import com.example.flower_show.util.PerformanceTrace
import com.example.flower_show.viewmodel.SocialViewModel
import com.example.flower_show.viewmodel.AuthIntent
import com.example.flower_show.viewmodel.AuthMode
import com.example.flower_show.viewmodel.AuthViewModel
import com.example.flower_show.viewmodel.ChatEffect
import com.example.flower_show.viewmodel.ChatIntent
import com.example.flower_show.viewmodel.ChatViewModel
import com.example.flower_show.viewmodel.RelationListTab
import com.example.flower_show.viewmodel.SocialIntent
import com.example.flower_show.viewmodel.VideoIntent
import com.example.flower_show.viewmodel.VideoViewModel
import java.util.concurrent.Executors

/**
 * App 的唯一 Activity。
 *
 * 这个项目采用“Single Activity + Compose 页面”的方式：
 * - Android 系统只启动 MainActivity。
 * - MainActivity 里通过 setContent 挂载 Compose UI。
 * - App 内部页面切换由 AppNavigation 里的 NavHost 控制。
 *
 * 当前 route 格式：
 * - "video"：默认首页视频流
 * - "search"：搜索页
 * - "result/{keyword}"：某个关键词的搜索结果页
 * - "video/{targetId}"：回到视频流，并跳到指定 id 的内容
 *
 * 你读视频主流程时，先看 "video" 和 "video/{targetId}" 两条分支。
 */
class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {}

    // 诊断报告是纯文件写入：放到后台线程执行，
    // 避免 onPause/onDestroy 里的主线程磁盘 IO（ANR/卡顿风险）。
    private val diagnosticsExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "perf-diagnostics").apply { isDaemon = true }
    }

    private fun flushDiagnosticsAsync() {
        val appContext = applicationContext
        diagnosticsExecutor.execute {
            PerformanceDiagnostics.flushToDisk(appContext, MetricsCollector.summary())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        PerformanceDiagnostics.markActivityOnCreate(this)
        PerformanceExperimentConfig.configureFromIntent(intent)
        PerformanceTrace.enableAppTracing()
        enableEdgeToEdge()

        // Edge-to-edge: content draws behind system bars
        WindowCompat.setDecorFitsSystemWindows(window, false)
        // Allow content to appear behind display cutout
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false

        setContent {
            FlowerShowTheme {
                AppNavigation()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        PerformanceDiagnostics.startFrameMonitoring()
    }

    override fun onPause() {
        PerformanceDiagnostics.stopFrameMonitoring()
        flushDiagnosticsAsync()
        super.onPause()
    }

    override fun onDestroy() {
        flushDiagnosticsAsync()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        PerformanceExperimentConfig.configureFromIntent(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@Composable
private fun AppNavigation() {
    /*
     * Navigation Compose 主入口。
     *
     * rememberNavController() 会创建并记住一个 NavController。
     * NavHost 根据当前目的地渲染对应页面，composable(...) 就是每个页面的声明。
     */
    val navController = rememberNavController()
    val context = LocalContext.current
    val authComponents = remember(context.applicationContext) {
        AuthGraph.get(context.applicationContext)
    }
    val authViewModel: AuthViewModel = viewModel(
        factory = remember(authComponents) {
            AuthViewModel.Factory(
                repository = authComponents.repository,
                sessionManager = authComponents.sessionManager,
            )
        },
    )
    val socialRepository = remember(authComponents, context.applicationContext) {
        authComponents.socialRepository
            ?: RepositoryFactory.getSocialRepository(context.applicationContext)
    }
    val socialViewModel: SocialViewModel = viewModel(
        factory = remember(socialRepository) {
            SocialViewModel.Factory(socialRepository)
        },
    )
    val chatRepository = remember(authComponents, context.applicationContext) {
        authComponents.chatRepository
            ?: RepositoryFactory.getChatRepository(context.applicationContext)
    }
    val chatViewModel: ChatViewModel = viewModel(
        factory = remember(chatRepository, socialRepository, authComponents) {
            ChatViewModel.Factory(
                chatRepository = chatRepository,
                socialRepository = socialRepository,
                realtimeClient = authComponents.chatRealtimeClient ?: NoOpChatRealtimeClient,
            )
        },
    )
    // Keep one video state machine for the whole Activity. Creating it inside each
    // NavHost destination starts a fresh feed session whenever routes change.
    val videoViewModel: VideoViewModel = viewModel()
    val authState by authViewModel.state.collectAsStateWithLifecycle()
    val socialState by socialViewModel.state.collectAsStateWithLifecycle()
    val chatState by chatViewModel.state.collectAsStateWithLifecycle()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    var pendingProtectedRoute by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(authState.isInitializing, authState.currentUser?.userId) {
        if (!authState.isInitializing) {
            videoViewModel.dispatch(
                VideoIntent.BindViewer(authState.currentUser?.userId),
            )
        }
    }

    val requestAuthentication: (String?) -> Unit = { destination ->
        pendingProtectedRoute = destination
        authViewModel.dispatch(AuthIntent.ShowLogin)
        navController.navigateAuth(clearProtectedRoutes = false)
    }
    val openProtectedRoute: (String) -> Unit = { destination ->
        if (authState.isAuthenticated) {
            navController.navigate(destination) { launchSingleTop = true }
        } else {
            requestAuthentication(destination)
        }
    }

    LaunchedEffect(authState.currentUser?.userId, currentRoute) {
        val user = authState.currentUser
        if (user == null) {
            // 登出后清掉内存中的社交状态（个人资料、关注、通知），
            // 避免下一个登录用户看到上一个用户的资料。
            socialViewModel.dispatch(SocialIntent.UnbindUser)
            chatViewModel.dispatch(ChatIntent.BindCurrentUser(""))
            return@LaunchedEffect
        }
        socialViewModel.dispatch(SocialIntent.BindAuthenticatedUser(user))
        chatViewModel.dispatch(ChatIntent.BindCurrentUser(user.userId))
        if (currentRoute == AppRoutes.Auth) {
            val destination = pendingProtectedRoute
            pendingProtectedRoute = null
            if (destination == null) {
                navController.navigateHome()
            } else {
                navController.navigate(destination) {
                    popUpTo(AppRoutes.Video) { inclusive = false }
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(chatViewModel, navController) {
        chatViewModel.effects.collect { effect ->
            when (effect) {
                is ChatEffect.NavigateToConversation ->
                    navController.navigateChat(effect.conversationId)
                is ChatEffect.GroupCreated ->
                    navController.navigateCreatedGroupChat(effect.conversationId)
                is ChatEffect.VideoShared ->
                    Toast.makeText(context, "视频已发送", Toast.LENGTH_SHORT).show()
                is ChatEffect.MembersAdded ->
                    navController.popBackStack()
                is ChatEffect.ShowError ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                ChatEffect.ConversationExited ->
                    navController.navigateMessages()
            }
        }
    }

    LaunchedEffect(authState.isInitializing, authState.isAuthenticated, currentRoute) {
        if (
            !authState.isInitializing &&
            !authState.isAuthenticated &&
            AppRoutes.isProtected(currentRoute)
        ) {
            pendingProtectedRoute = null
            authViewModel.dispatch(AuthIntent.ShowLogin)
            navController.navigateAuth(clearProtectedRoutes = true)
        }
    }

    // 保持原来的交互习惯：只要不在首页，系统返回键统一回到首页视频流。
    BackHandler(
        enabled = currentRoute != null &&
            currentRoute != AppRoutes.Video &&
            !(currentRoute == AppRoutes.Auth && authState.mode == AuthMode.Register),
    ) {
        if (currentRoute == AppRoutes.Auth) {
            // 用户手动放弃登录：清除保护路由残留，否则下次登录成功时
            // 可能被导航到陈旧的 protected 目标而不是首页。
            pendingProtectedRoute = null
            navController.navigateHome()
        } else {
            navController.popBackStack()
        }
    }

    NavHost(
        navController = navController,
        startDestination = AppRoutes.Video,
    ) {
        // App 默认入口：展示普通首页视频流。
        composable(AppRoutes.Video) {
            VideoScreen(
                targetVideoId = null,
                isAuthenticated = authState.isAuthenticated,
                viewModel = videoViewModel,
                onAuthenticationRequired = { requestAuthentication(null) },
                onSearchClick = { navController.navigateSearch() },
                onRecommendWordClick = { word -> navController.navigateResult(word) },
                onFriendsClick = {
                    socialViewModel.dispatch(SocialIntent.SelectRelationTab(RelationListTab.Following))
                    openProtectedRoute(AppRoutes.connections(RelationListTab.Following))
                },
                onMessagesClick = { openProtectedRoute(AppRoutes.Messages) },
                onProfileClick = { openProtectedRoute(AppRoutes.Profile) },
                onCreatorClick = { video ->
                    val authorId = video.authorUserId
                        ?: socialState.people
                            .filter { it.nickname == video.author }
                            .singleOrNull()
                            ?.id
                    if (!authorId.isNullOrBlank()) {
                        if (authorId == socialState.myProfile.id) {
                            openProtectedRoute(AppRoutes.Profile)
                        } else {
                            navController.navigateOtherProfile(authorId)
                        }
                    }
                },
                onShareClick = { video ->
                    if (authState.isAuthenticated) {
                        chatViewModel.dispatch(
                            ChatIntent.OpenShareComposer(video.asProfileVideo()),
                        )
                    } else {
                        requestAuthentication(null)
                    }
                },
            )
        }

        // 从搜索结果点进来：还是展示 VideoScreen，但多传一个 targetVideoId。
        // VideoScreen 会把这个 id 交给 VideoViewModel.JumpToVideo 处理。
        composable(AppRoutes.VideoTarget) { backStackEntry ->
            // Navigation Compose 解析路径参数时已完成一次 URL 解码，
            // 这里不能再 Uri::decode，否则含 "%" 的值会被二次解码破坏。
            val targetId = backStackEntry.arguments
                ?.getString(AppRoutes.ArgTargetId)

            VideoScreen(
                targetVideoId = targetId,
                isAuthenticated = authState.isAuthenticated,
                viewModel = videoViewModel,
                onAuthenticationRequired = { requestAuthentication(null) },
                onSearchClick = { navController.navigateSearch() },
                onRecommendWordClick = { word -> navController.navigateResult(word) },
                onFriendsClick = {
                    socialViewModel.dispatch(SocialIntent.SelectRelationTab(RelationListTab.Following))
                    openProtectedRoute(AppRoutes.connections(RelationListTab.Following))
                },
                onMessagesClick = { openProtectedRoute(AppRoutes.Messages) },
                onProfileClick = { openProtectedRoute(AppRoutes.Profile) },
                onCreatorClick = { video ->
                    val authorId = video.authorUserId
                        ?: socialState.people
                            .filter { it.nickname == video.author }
                            .singleOrNull()
                            ?.id
                    if (!authorId.isNullOrBlank()) {
                        if (authorId == socialState.myProfile.id) {
                            openProtectedRoute(AppRoutes.Profile)
                        } else {
                            navController.navigateOtherProfile(authorId)
                        }
                    }
                },
                onShareClick = { video ->
                    if (authState.isAuthenticated) {
                        chatViewModel.dispatch(
                            ChatIntent.OpenShareComposer(video.asProfileVideo()),
                        )
                    } else {
                        requestAuthentication(null)
                    }
                },
            )
        }

        // 搜索入口页。
        composable(AppRoutes.Search) {
            SearchScreen(
                onBack = { navController.navigateHome() },
                onSearch = { keyword -> navController.navigateResult(keyword) },
            )
        }

        // 搜索结果页。点击某个结果后，跳到 video/{targetId}。
        composable(AppRoutes.Result) { backStackEntry ->
            val keyword = backStackEntry.arguments
                ?.getString(AppRoutes.ArgKeyword)
                .orEmpty()

            SearchResultScreen(
                keyword = keyword,
                onBack = { navController.navigateHome() },
                onSearch = { nextKeyword -> navController.navigateResult(nextKeyword) },
                onResultClick = { targetId -> navController.navigateVideoTarget(targetId) },
            )
        }

        composable(AppRoutes.Auth) {
            when {
                authState.isInitializing -> AuthLoadingScreen()
                authState.mode == AuthMode.Login -> LoginScreen(
                    state = authState,
                    onIntent = authViewModel::dispatch,
                    onBack = navController::navigateHome,
                )
                else -> {
                    BackHandler { authViewModel.dispatch(AuthIntent.ShowLogin) }
                    RegisterScreen(
                        state = authState,
                        onIntent = authViewModel::dispatch,
                        onBack = { authViewModel.dispatch(AuthIntent.ShowLogin) },
                    )
                }
            }
        }

        composable(AppRoutes.Profile) {
            MyProfileScreen(
                profile = socialState.myProfile,
                selectedTab = socialState.selectedProfileTab,
                onTabSelected = { tab ->
                    socialViewModel.dispatch(SocialIntent.SelectProfileTab(tab))
                },
                onBack = navController::popBackStack,
                onFollowingClick = {
                    socialViewModel.dispatch(SocialIntent.SelectRelationTab(RelationListTab.Following))
                    navController.navigateConnections(RelationListTab.Following)
                },
                onFollowersClick = {
                    socialViewModel.dispatch(SocialIntent.SelectRelationTab(RelationListTab.Followers))
                    navController.navigateConnections(RelationListTab.Followers)
                },
                onVideoClick = navController::navigateVideoTarget,
                onWorkVisibilityChange = { videoId, visible ->
                    socialViewModel.dispatch(SocialIntent.SetWorkVisibility(videoId, visible))
                },
                onLogout = { authViewModel.dispatch(AuthIntent.Logout) },
                onLogoutAll = { authViewModel.dispatch(AuthIntent.LogoutAll) },
            )
        }

        composable(AppRoutes.OtherProfile) { backStackEntry ->
            val userId = backStackEntry.arguments
                ?.getString(AppRoutes.ArgUserId)
                .orEmpty()
            LaunchedEffect(userId) {
                socialViewModel.dispatch(SocialIntent.LoadProfile(userId))
            }
            val profile = socialState.profile(userId)
                ?: SocialProfile.Empty.copy(id = userId, nickname = "加载中…")
            OtherProfileScreen(
                profile = profile,
                onBack = navController::popBackStack,
                onFollowClick = {
                    if (authState.isAuthenticated) {
                        socialViewModel.dispatch(SocialIntent.ToggleFollow(profile.id))
                    } else {
                        requestAuthentication(null)
                    }
                },
                onMessageClick = {
                    if (authState.isAuthenticated) {
                        chatViewModel.dispatch(
                            ChatIntent.ResolveDirectConversation(profile.id),
                        )
                    } else {
                        requestAuthentication(AppRoutes.otherProfile(profile.id))
                    }
                },
                onVideoClick = navController::navigateVideoTarget,
                showMessageAction = profile.isMutual,
            )
        }

        composable(AppRoutes.Connections) {
            ConnectionsScreen(
                state = socialState,
                onBack = navController::popBackStack,
                onTabSelected = { tab ->
                    socialViewModel.dispatch(SocialIntent.SelectRelationTab(tab))
                },
                onQueryChange = { query ->
                    socialViewModel.dispatch(SocialIntent.UpdateRelationQuery(query))
                },
                onProfileClick = navController::navigateOtherProfile,
                onFollowClick = { userId ->
                    if (authState.isAuthenticated) {
                        socialViewModel.dispatch(SocialIntent.ToggleFollow(userId))
                    } else {
                        requestAuthentication(null)
                    }
                },
            )
        }

        composable(AppRoutes.Messages) {
            LaunchedEffect(Unit) {
                socialViewModel.dispatch(SocialIntent.LoadNotifications)
            }
            MessageListScreen(
                state = socialState,
                chatState = chatState,
                onBack = navController::popBackStack,
                onConversationClick = { conversationId ->
                    navController.navigateChat(conversationId)
                },
                onTabSelected = { tab ->
                    socialViewModel.dispatch(SocialIntent.SelectMessageTab(tab))
                },
                onNotificationClick = { notificationId ->
                    socialViewModel.dispatch(SocialIntent.MarkNotificationRead(notificationId))
                },
                onMarkAllNotificationsRead = {
                    socialViewModel.dispatch(SocialIntent.MarkAllNotificationsRead)
                },
                onLoadMoreNotifications = {
                    socialViewModel.dispatch(SocialIntent.LoadMoreNotifications)
                },
                onLoadMoreConversations = {
                    chatViewModel.dispatch(ChatIntent.LoadMoreConversations)
                },
                onRetryConversations = {
                    chatViewModel.dispatch(ChatIntent.RefreshConversations)
                },
                onCreateGroup = navController::navigateGroupCreate,
                onStartPolling = {
                    chatViewModel.dispatch(ChatIntent.StartConversationPolling)
                },
                onStopPolling = {
                    chatViewModel.dispatch(ChatIntent.StopConversationPolling)
                },
            )
        }

        composable(AppRoutes.Chat) { backStackEntry ->
            val conversationId = backStackEntry.arguments
                ?.getString(AppRoutes.ArgConversationId)
                .orEmpty()
            ChatScreen(
                conversationId = conversationId,
                state = chatState,
                onBack = navController::popBackStack,
                onDraftChange = { text ->
                    chatViewModel.dispatch(ChatIntent.UpdateDraft(text))
                },
                onSend = {
                    chatViewModel.dispatch(ChatIntent.SendText)
                },
                onRetrySend = { chatViewModel.dispatch(ChatIntent.RetryText) },
                onLoadOlder = { chatViewModel.dispatch(ChatIntent.LoadOlderMessages) },
                onRetryRoom = { chatViewModel.dispatch(ChatIntent.RefreshRoom) },
                onStartRoom = { chatViewModel.dispatch(ChatIntent.StartRoom(it)) },
                onStopRoom = { chatViewModel.dispatch(ChatIntent.StopRoom(it)) },
                onGroupInfoClick = navController::navigateGroupInfo,
                onVideoClick = navController::navigateVideoTarget,
            )
        }

        composable(AppRoutes.GroupCreate) {
            GroupCreateScreen(
                state = chatState,
                onBack = navController::popBackStack,
                onOpen = { chatViewModel.dispatch(ChatIntent.OpenGroupComposer) },
                onDismiss = { chatViewModel.dispatch(ChatIntent.DismissGroupComposer) },
                onNameChange = {
                    chatViewModel.dispatch(ChatIntent.UpdateGroupName(it))
                },
                onQueryChange = {
                    chatViewModel.dispatch(ChatIntent.UpdateGroupQuery(it))
                },
                onMemberToggle = {
                    chatViewModel.dispatch(ChatIntent.ToggleGroupMember(it))
                },
                onCreate = { chatViewModel.dispatch(ChatIntent.CreateGroup) },
            )
        }

        composable(AppRoutes.GroupInfo) { backStackEntry ->
            val conversationId = backStackEntry.arguments
                ?.getString(AppRoutes.ArgConversationId)
                .orEmpty()
            GroupInfoScreen(
                conversationId = conversationId,
                state = chatState,
                onBack = navController::popBackStack,
                onLoad = { chatViewModel.dispatch(ChatIntent.LoadGroupInfo(it)) },
                onRenameChange = {
                    chatViewModel.dispatch(ChatIntent.UpdateRenameDraft(it))
                },
                onRename = { chatViewModel.dispatch(ChatIntent.RenameGroup) },
                onAddMembers = navController::navigateGroupAddMembers,
                onRemoveMember = {
                    chatViewModel.dispatch(ChatIntent.RemoveMember(it))
                },
                onTransferOwner = {
                    chatViewModel.dispatch(ChatIntent.TransferGroupOwner(it))
                },
                onDissolve = { chatViewModel.dispatch(ChatIntent.DissolveGroup) },
                onLeave = { chatViewModel.dispatch(ChatIntent.LeaveConversation) },
            )
        }

        composable(AppRoutes.GroupAddMembers) { backStackEntry ->
            val conversationId = backStackEntry.arguments
                ?.getString(AppRoutes.ArgConversationId)
                .orEmpty()
            GroupAddMembersScreen(
                conversationId = conversationId,
                state = chatState,
                onBack = navController::popBackStack,
                onOpen = {
                    chatViewModel.dispatch(ChatIntent.PrepareAddMembers(it))
                },
                onDismiss = {
                    chatViewModel.dispatch(ChatIntent.DismissAddMembers)
                },
                onQueryChange = {
                    chatViewModel.dispatch(ChatIntent.UpdateAddMembersQuery(it))
                },
                onMemberToggle = {
                    chatViewModel.dispatch(ChatIntent.ToggleAddMember(it))
                },
                onAdd = { chatViewModel.dispatch(ChatIntent.AddSelectedMembers) },
            )
        }
    }

    chatState.shareComposer?.takeIf { authState.isAuthenticated }?.let { composer ->
        ShareToFriendsDialog(
            state = composer,
            onDismiss = {
                chatViewModel.dispatch(ChatIntent.DismissShareComposer)
            },
            onQueryChange = { query ->
                chatViewModel.dispatch(ChatIntent.UpdateShareQuery(query))
            },
            onFriendSelected = { userId ->
                chatViewModel.dispatch(ChatIntent.SelectShareRecipient(userId))
            },
            onSend = {
                chatViewModel.dispatch(ChatIntent.SendVideoShare)
            },
        )
    }
}

/**
 * App 内部所有导航 route。
 *
 * 把 route 字符串集中在这里，后续改路径时不用到处搜索硬编码。
 */
private object AppRoutes {
    const val ArgKeyword = "keyword"
    const val ArgTargetId = "targetId"
    const val ArgUserId = "userId"
    const val ArgConversationId = "conversationId"
    const val ArgRelationTab = "relationTab"

    const val Video = "video"
    const val Search = "search"
    const val Auth = "auth"
    const val Result = "result/{$ArgKeyword}"
    const val VideoTarget = "video/{$ArgTargetId}"
    const val Profile = "profile"
    const val OtherProfile = "profile/{$ArgUserId}"
    const val Connections = "connections/{$ArgRelationTab}"
    const val Messages = "messages"
    const val Chat = "chat/{$ArgConversationId}"
    const val GroupCreate = "chat/group/create"
    const val GroupInfo = "chat/{$ArgConversationId}/info"
    const val GroupAddMembers = "chat/{$ArgConversationId}/members/add"

    fun isProtected(route: String?): Boolean = route == Profile ||
        route == Connections ||
        route == Messages ||
        route == Chat ||
        route == GroupCreate ||
        route == GroupInfo ||
        route == GroupAddMembers

    fun result(keyword: String): String = "result/${Uri.encode(keyword.trim())}"

    fun videoTarget(targetId: String): String = "video/${Uri.encode(targetId)}"

    fun otherProfile(userId: String): String = "profile/${Uri.encode(userId)}"

    fun connections(tab: RelationListTab): String = "connections/${tab.name}"

    fun chat(conversationId: String): String = "chat/${Uri.encode(conversationId)}"

    fun groupInfo(conversationId: String): String =
        "chat/${Uri.encode(conversationId)}/info"

    fun groupAddMembers(conversationId: String): String =
        "chat/${Uri.encode(conversationId)}/members/add"
}

/**
 * 回到首页视频流。
 *
 * popUpTo(Video) 会把搜索页、结果页、目标视频页都弹掉；
 * launchSingleTop 避免已经在首页时重复创建一个首页目的地。
 */
private fun NavController.navigateHome() {
    navigate(AppRoutes.Video) {
        popUpTo(AppRoutes.Video) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private fun NavController.navigateAuth(clearProtectedRoutes: Boolean) {
    navigate(AppRoutes.Auth) {
        if (clearProtectedRoutes) {
            popUpTo(AppRoutes.Video) { inclusive = false }
        }
        launchSingleTop = true
    }
}

/**
 * 打开搜索页。
 *
 * 先 pop 回首页，再进入搜索页，保持导航栈简单：
 * 首页 -> 搜索。
 */
private fun NavController.navigateSearch() {
    navigate(AppRoutes.Search) {
        popUpTo(AppRoutes.Video) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

/**
 * 打开搜索结果页。
 *
 * 关键词里可能有空格、中文或特殊字符，所以放进 route 前要 encode。
 */
private fun NavController.navigateResult(keyword: String) {
    navigate(AppRoutes.result(keyword)) {
        popUpTo(AppRoutes.Video) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

/**
 * 打开视频流并定位到指定卡片。
 *
 * targetId 可能是：
 * - 视频裸 id
 * - "image:<id>"
 * - "album:<id>"
 *
 * 所以这里也要 encode，避免冒号、斜杠等字符破坏 route。
 */
private fun NavController.navigateVideoTarget(targetId: String) {
    navigate(AppRoutes.videoTarget(targetId)) {
        popUpTo(AppRoutes.Video) {
            inclusive = false
        }
        launchSingleTop = true
    }
}

private fun NavController.navigateProfile() {
    navigate(AppRoutes.Profile) {
        launchSingleTop = true
    }
}

private fun NavController.navigateOtherProfile(userId: String) {
    navigate(AppRoutes.otherProfile(userId)) {
        launchSingleTop = true
    }
}

private fun NavController.navigateConnections(tab: RelationListTab) {
    navigate(AppRoutes.connections(tab)) {
        launchSingleTop = true
    }
}

private fun NavController.navigateMessages() {
    if (popBackStack(AppRoutes.Messages, inclusive = false)) return
    navigate(AppRoutes.Messages) {
        launchSingleTop = true
    }
}

private fun NavController.navigateChat(conversationId: String) {
    navigate(AppRoutes.chat(conversationId)) {
        launchSingleTop = true
    }
}

private fun NavController.navigateCreatedGroupChat(conversationId: String) {
    navigate(AppRoutes.chat(conversationId)) {
        popUpTo(AppRoutes.GroupCreate) { inclusive = true }
        launchSingleTop = true
    }
}

private fun NavController.navigateGroupCreate() {
    navigate(AppRoutes.GroupCreate) {
        launchSingleTop = true
    }
}

private fun NavController.navigateGroupInfo(conversationId: String) {
    navigate(AppRoutes.groupInfo(conversationId)) {
        launchSingleTop = true
    }
}

private fun NavController.navigateGroupAddMembers(conversationId: String) {
    navigate(AppRoutes.groupAddMembers(conversationId)) {
        launchSingleTop = true
    }
}

private fun VideoItem.asProfileVideo(): ProfileVideo = ProfileVideo(
    id = id,
    title = title,
    author = author,
    coverUrl = coverUrl,
    authorUserId = authorUserId,
)
