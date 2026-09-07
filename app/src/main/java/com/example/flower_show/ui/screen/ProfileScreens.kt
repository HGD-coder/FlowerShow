package com.example.flower_show.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.flower_show.model.ProfileVideo
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.ui.component.FollowActionButton
import com.example.flower_show.ui.component.ProfileVideoTile
import com.example.flower_show.ui.component.RelationUserRow
import com.example.flower_show.ui.component.SocialAvatar
import com.example.flower_show.ui.component.SocialDimens
import com.example.flower_show.ui.component.SocialSearchField
import com.example.flower_show.ui.component.SocialTopBar
import com.example.flower_show.ui.component.formatSocialCount
import com.example.flower_show.viewmodel.ProfileContentTab
import com.example.flower_show.viewmodel.RelationListTab
import com.example.flower_show.viewmodel.SocialState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyProfileScreen(
    profile: SocialProfile,
    selectedTab: ProfileContentTab,
    onTabSelected: (ProfileContentTab) -> Unit,
    onBack: () -> Unit,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    onWorkVisibilityChange: (String, Boolean) -> Unit,
    onLogout: () -> Unit = {},
    onLogoutAll: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var accountMenuExpanded by remember { mutableStateOf(false) }
    val videos = when (selectedTab) {
        ProfileContentTab.Works -> profile.works
        ProfileContentTab.Likes -> profile.likedVideos
        ProfileContentTab.Collections -> profile.collectedVideos
    }

    Scaffold(
        modifier = modifier.testTag("my_profile_screen"),
        topBar = {
            SocialTopBar(
                title = "我的主页",
                onBack = onBack,
                trailing = {
                    Box {
                        IconButton(onClick = { accountMenuExpanded = true }) {
                            Icon(Icons.Default.MoreHoriz, contentDescription = "账号操作")
                        }
                        DropdownMenu(
                            expanded = accountMenuExpanded,
                            onDismissRequest = { accountMenuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("退出当前设备") },
                                onClick = {
                                    accountMenuExpanded = false
                                    onLogout()
                                },
                                modifier = Modifier.testTag("logout_current"),
                            )
                            DropdownMenuItem(
                                text = { Text("退出全部设备") },
                                onClick = {
                                    accountMenuExpanded = false
                                    onLogoutAll()
                                },
                                modifier = Modifier.testTag("logout_all"),
                            )
                        }
                    }
                },
            )
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(
                    profile = profile,
                    isMine = true,
                    onFollowingClick = onFollowingClick,
                    onFollowersClick = onFollowersClick,
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                PrimaryTabRow(selectedTabIndex = selectedTab.ordinal) {
                    ProfileContentTab.entries.forEachIndexed { index, item ->
                        Tab(
                            selected = item == selectedTab,
                            onClick = { onTabSelected(item) },
                            modifier = Modifier.testTag("profile_tab_${item.name.lowercase()}"),
                            text = { Text(item.title) },
                        )
                    }
                }
            }
            if (videos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyProfileContent(text = "这里还没有${selectedTab.title}")
                }
            } else {
                items(
                    items = videos,
                    key = { "${selectedTab.name}:${it.id}" },
                ) { video ->
                    ProfileVideoTile(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                        visibilityEditable = selectedTab == ProfileContentTab.Works,
                        onVisibilityChange = { visible ->
                            onWorkVisibilityChange(video.id, visible)
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun OtherProfileScreen(
    profile: SocialProfile,
    onBack: () -> Unit,
    onFollowClick: () -> Unit,
    onMessageClick: () -> Unit,
    onVideoClick: (String) -> Unit,
    modifier: Modifier = Modifier,
    showMessageAction: Boolean = profile.isMutual,
) {
    val publicVideos = profile.works.filter { it.isPublic && it.isVisibleOnProfile }
    Scaffold(
        modifier = modifier.testTag("other_profile_screen"),
        topBar = {
            SocialTopBar(title = profile.nickname, onBack = onBack)
        },
    ) { contentPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(
                    profile = profile,
                    isMine = false,
                    onFollowingClick = {},
                    onFollowersClick = {},
                )
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = SocialDimens.spaceMd, vertical = SocialDimens.spaceSm),
                    horizontalArrangement = Arrangement.spacedBy(SocialDimens.spaceSm),
                ) {
                    FollowActionButton(
                        isFollowing = profile.isFollowing,
                        onClick = onFollowClick,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("other_profile_follow_button"),
                    )
                    if (showMessageAction) {
                        Button(
                            onClick = onMessageClick,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("other_profile_message_button"),
                        ) {
                            Text("发消息")
                        }
                    }
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    Text(
                        text = "公开作品",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(SocialDimens.spaceMd)
                            .semantics { heading() },
                    )
                    HorizontalDivider()
                }
            }
            if (publicVideos.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    EmptyProfileContent("对方暂未公开作品")
                }
            } else {
                items(publicVideos, key = { "public:${it.id}" }) { video ->
                    ProfileVideoTile(
                        video = video,
                        onClick = { onVideoClick(video.id) },
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionsScreen(
    state: SocialState,
    onBack: () -> Unit,
    onTabSelected: (RelationListTab) -> Unit,
    onQueryChange: (String) -> Unit,
    onProfileClick: (String) -> Unit,
    onFollowClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val profiles = state.relationProfiles

    Scaffold(
        modifier = modifier.testTag("connections_screen"),
        topBar = { SocialTopBar(title = "关注与粉丝", onBack = onBack) },
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        ) {
            PrimaryTabRow(selectedTabIndex = state.selectedRelationTab.ordinal) {
                RelationListTab.entries.forEachIndexed { index, tab ->
                    Tab(
                        selected = tab == state.selectedRelationTab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.testTag("connections_tab_${tab.name.lowercase()}"),
                        text = { Text(tab.title) },
                    )
                }
            }
            SocialSearchField(
                value = state.relationQuery,
                onValueChange = onQueryChange,
                placeholder = "搜索昵称或账号",
                modifier = Modifier.padding(SocialDimens.spaceMd),
                testTag = "connections_search_input",
            )
            if (state.isRelationLoading && profiles.isEmpty()) {
                EmptyProfileContent("加载中…")
            } else if (profiles.isEmpty()) {
                EmptyProfileContent("没有找到相关用户")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(profiles, key = SocialProfile::id) { profile ->
                        RelationUserRow(
                            profile = profile,
                            onProfileClick = { onProfileClick(profile.id) },
                            onFollowClick = { onFollowClick(profile.id) },
                            modifier = Modifier.testTag("connection_user_${profile.id}"),
                        )
                        HorizontalDivider(modifier = Modifier.padding(start = 80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(
    profile: SocialProfile,
    isMine: Boolean,
    onFollowingClick: () -> Unit,
    onFollowersClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SocialDimens.spaceMd, vertical = SocialDimens.spaceSm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SocialAvatar(profile = profile, size = SocialDimens.avatarLarge)
        Spacer(Modifier.height(12.dp))
        Text(
            text = profile.nickname,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = "@${profile.account}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(SocialDimens.spaceSm))
        Text(
            text = profile.bio,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(SocialDimens.spaceSm))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.LocationOn,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(SocialDimens.spaceXs))
            Text(
                text = profile.region,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(SocialDimens.spaceMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            ProfileStat(formatSocialCount(profile.worksCount), "作品")
            ProfileStat(
                formatSocialCount(profile.followingCount),
                "关注",
                onClick = onFollowingClick.takeIf { isMine },
                testTag = "profile_following_stat",
            )
            ProfileStat(
                formatSocialCount(profile.followersCount),
                "粉丝",
                onClick = onFollowersClick.takeIf { isMine },
                testTag = "profile_followers_stat",
            )
            ProfileStat(formatSocialCount(profile.likesReceivedCount), "获赞")
        }
        Spacer(Modifier.height(SocialDimens.spaceMd))
    }
}

@Composable
private fun ProfileStat(
    value: String,
    label: String,
    onClick: (() -> Unit)? = null,
    testTag: String? = null,
) {
    Column(
        modifier = Modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .sizeIn(minWidth = 64.dp, minHeight = 56.dp)
            .clickable(
                enabled = onClick != null,
                onClick = onClick ?: {},
            ),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyProfileContent(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
    )
}

private val ProfileContentTab.title: String
    get() = when (this) {
        ProfileContentTab.Works -> "作品"
        ProfileContentTab.Likes -> "点赞"
        ProfileContentTab.Collections -> "收藏"
    }

private val RelationListTab.title: String
    get() = when (this) {
        RelationListTab.Following -> "关注"
        RelationListTab.Followers -> "粉丝"
    }
