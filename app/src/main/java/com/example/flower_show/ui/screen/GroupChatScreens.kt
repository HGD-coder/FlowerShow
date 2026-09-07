package com.example.flower_show.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.flower_show.model.ChatConversationState
import com.example.flower_show.model.ChatMember
import com.example.flower_show.model.ChatMemberRole
import com.example.flower_show.model.SocialProfile
import com.example.flower_show.ui.component.SocialDimens
import com.example.flower_show.ui.component.SocialSearchField
import com.example.flower_show.ui.component.SocialTopBar
import com.example.flower_show.viewmodel.ChatState

@Composable
fun GroupCreateScreen(
    state: ChatState,
    onBack: () -> Unit,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    onMemberToggle: (String) -> Unit,
    onCreate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(Unit) {
        onOpen()
        onDispose(onDismiss)
    }
    val composer = state.groupComposer
    Scaffold(
        modifier = modifier.testTag("group_create_screen"),
        topBar = { SocialTopBar(title = "创建群聊", onBack = onBack) },
    ) { contentPadding ->
        if (composer == null) {
            StatusPane(
                text = "正在准备群聊…",
                loading = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(SocialDimens.spaceMd),
        ) {
            OutlinedTextField(
                value = composer.name,
                onValueChange = onNameChange,
                label = { Text("群名称") },
                placeholder = { Text("请输入群名称") },
                singleLine = true,
                enabled = !composer.isCreating,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_name_input"),
            )
            Spacer(Modifier.height(12.dp))
            SocialSearchField(
                value = composer.query,
                onValueChange = onQueryChange,
                placeholder = "搜索互关联系人",
                testTag = "group_member_search_input",
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "已选择 ${composer.selectedUserIds.size} 人",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            CandidateList(
                candidates = composer.candidates,
                selectedIds = composer.selectedUserIds,
                loading = composer.isLoadingCandidates,
                emptyText = composer.error ?: "没有找到互关联系人",
                onToggle = onMemberToggle,
                tagPrefix = "group_member",
                modifier = Modifier.weight(1f),
            )
            composer.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Button(
                onClick = onCreate,
                enabled = composer.name.isNotBlank() &&
                    composer.selectedUserIds.isNotEmpty() &&
                    !composer.isCreating,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("group_create_submit"),
            ) {
                if (composer.isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(if (composer.isCreating) "创建中…" else "创建群聊")
            }
        }
    }
}

@Composable
fun GroupInfoScreen(
    conversationId: String,
    state: ChatState,
    onBack: () -> Unit,
    onLoad: (String) -> Unit,
    onRenameChange: (String) -> Unit,
    onRename: () -> Unit,
    onAddMembers: (String) -> Unit,
    onRemoveMember: (String) -> Unit,
    onTransferOwner: (String) -> Unit,
    onDissolve: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(conversationId) {
        onLoad(conversationId)
        onDispose {}
    }
    val detail = state.activeConversation?.takeIf { it.conversationId == conversationId }
    var confirmation by remember { mutableStateOf<GroupConfirmation?>(null) }
    val dissolved = detail?.state == ChatConversationState.DISSOLVED
    val isOwner = detail?.ownerUserId == state.currentUserId
    val busy = state.isGroupActionInProgress

    Scaffold(
        modifier = modifier.testTag("group_info_screen"),
        topBar = { SocialTopBar(title = "群资料", onBack = onBack) },
    ) { contentPadding ->
        when {
            state.isLoadingGroupInfo && detail == null -> StatusPane(
                text = "群资料加载中…",
                loading = true,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            detail == null -> StatusPane(
                text = state.groupActionError ?: "群资料不可用",
                actionLabel = "重试",
                onAction = { onLoad(conversationId) },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = SocialDimens.spaceMd,
                    vertical = 12.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dissolved) {
                    item("dissolved") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.errorContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                "群聊已解散，资料与历史消息仅供查看",
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                }
                item("name") {
                    Text(
                        text = detail.name?.takeIf(String::isNotBlank) ?: "群聊",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${detail.members.size} 位成员",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (isOwner && !dissolved) {
                    item("rename") {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("修改群名称", fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = state.renameDraft,
                                        onValueChange = onRenameChange,
                                        singleLine = true,
                                        enabled = !busy,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("group_rename_input"),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = onRename,
                                        enabled = state.renameDraft.isNotBlank() && !busy,
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.testTag("group_rename_submit"),
                                    ) {
                                        Text("保存")
                                    }
                                }
                            }
                        }
                    }
                    item("add_members") {
                        OutlinedButton(
                            onClick = { onAddMembers(conversationId) },
                            enabled = !busy,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_add_members_button"),
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("添加成员")
                        }
                    }
                }
                item("members_heading") {
                    Text(
                        "群成员",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(detail.members, key = ChatMember::userId) { member ->
                    GroupMemberRow(
                        member = member,
                        currentUserId = state.currentUserId,
                        ownerActionsVisible = isOwner && !dissolved,
                        enabled = !busy,
                        onRemove = {
                            confirmation = GroupConfirmation.Remove(member.userId)
                        },
                        onTransfer = {
                            confirmation = GroupConfirmation.Transfer(member.userId)
                        },
                    )
                    HorizontalDivider()
                }
                state.groupActionError?.let { error ->
                    item("group_error") {
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("group_action_error"),
                        )
                    }
                }
                if (!dissolved) {
                    item("lifecycle_action") {
                        if (isOwner) {
                            OutlinedButton(
                                onClick = { confirmation = GroupConfirmation.Dissolve },
                                enabled = !busy,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("group_dissolve_button"),
                            ) {
                                Icon(Icons.Default.DeleteForever, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("解散群聊")
                            }
                        } else {
                            OutlinedButton(
                                onClick = { confirmation = GroupConfirmation.Leave },
                                enabled = !busy,
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("group_leave_button"),
                            ) {
                                Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("退出群聊")
                            }
                        }
                    }
                }
            }
        }
    }

    confirmation?.let { pending ->
        ConfirmationDialog(
            confirmation = pending,
            onDismiss = { confirmation = null },
            onConfirm = {
                confirmation = null
                when (pending) {
                    GroupConfirmation.Dissolve -> onDissolve()
                    GroupConfirmation.Leave -> onLeave()
                    is GroupConfirmation.Remove -> onRemoveMember(pending.userId)
                    is GroupConfirmation.Transfer -> onTransferOwner(pending.userId)
                }
            },
        )
    }
}

@Composable
fun GroupAddMembersScreen(
    conversationId: String,
    state: ChatState,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onDismiss: () -> Unit,
    onQueryChange: (String) -> Unit,
    onMemberToggle: (String) -> Unit,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DisposableEffect(conversationId) {
        onOpen(conversationId)
        onDispose(onDismiss)
    }
    val composer = state.addMembers?.takeIf { it.conversationId == conversationId }
    Scaffold(
        modifier = modifier.testTag("group_add_members_screen"),
        topBar = { SocialTopBar(title = "添加群成员", onBack = onBack) },
    ) { contentPadding ->
        if (composer == null) {
            StatusPane(
                text = state.groupActionError ?: "正在加载联系人…",
                loading = state.groupActionError == null,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            )
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(SocialDimens.spaceMd),
        ) {
            SocialSearchField(
                value = composer.query,
                onValueChange = onQueryChange,
                placeholder = "搜索可添加的互关联系人",
                testTag = "add_member_search_input",
            )
            Spacer(Modifier.height(10.dp))
            Text(
                "已选择 ${composer.selectedUserIds.size} 人",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Spacer(Modifier.height(6.dp))
            CandidateList(
                candidates = composer.candidates,
                selectedIds = composer.selectedUserIds,
                loading = composer.isLoadingCandidates,
                emptyText = composer.error ?: "没有可添加的互关联系人",
                onToggle = onMemberToggle,
                tagPrefix = "add_member",
                modifier = Modifier.weight(1f),
            )
            composer.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
            Button(
                onClick = onAdd,
                enabled = composer.selectedUserIds.isNotEmpty() && !composer.isAdding,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("add_members_submit"),
            ) {
                Text(if (composer.isAdding) "添加中…" else "添加成员")
            }
        }
    }
}

@Composable
private fun CandidateList(
    candidates: List<SocialProfile>,
    selectedIds: Set<String>,
    loading: Boolean,
    emptyText: String,
    onToggle: (String) -> Unit,
    tagPrefix: String,
    modifier: Modifier = Modifier,
) {
    when {
        loading -> StatusPane(
            text = "联系人加载中…",
            loading = true,
            modifier = modifier,
        )
        candidates.isEmpty() -> StatusPane(emptyText, modifier = modifier)
        else -> LazyColumn(modifier = modifier.fillMaxSize()) {
            items(candidates, key = SocialProfile::id) { profile ->
                ContactSelectionRow(
                    friend = profile,
                    selected = profile.id in selectedIds,
                    onClick = { onToggle(profile.id) },
                    testTag = "${tagPrefix}_${profile.id}",
                )
            }
        }
    }
}

@Composable
private fun GroupMemberRow(
    member: ChatMember,
    currentUserId: String,
    ownerActionsVisible: Boolean,
    enabled: Boolean,
    onRemove: () -> Unit,
    onTransfer: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .testTag("group_member_${member.userId}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MemberAvatar(member)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = member.nickname?.takeIf(String::isNotBlank) ?: "群成员",
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = when {
                    member.role == ChatMemberRole.OWNER -> "群主"
                    member.userId == currentUserId -> "我"
                    else -> "成员"
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (
            ownerActionsVisible &&
            member.userId != currentUserId &&
            member.role != ChatMemberRole.OWNER
        ) {
            TextButton(onClick = onTransfer, enabled = enabled) { Text("转让") }
            TextButton(onClick = onRemove, enabled = enabled) { Text("移除") }
        }
    }
}

@Composable
private fun MemberAvatar(member: ChatMember) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        if (!member.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = "${member.nickname.orEmpty()}头像",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Group, contentDescription = null)
            }
        }
    }
}

@Composable
private fun ConfirmationDialog(
    confirmation: GroupConfirmation,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    val (title, text, confirmText) = when (confirmation) {
        GroupConfirmation.Dissolve -> Triple(
            "解散群聊？",
            "解散后所有成员只能查看历史消息，不能继续发送或管理。",
            "确认解散",
        )
        GroupConfirmation.Leave -> Triple(
            "退出群聊？",
            "退出后该会话将从你的会话列表中移除。",
            "确认退出",
        )
        is GroupConfirmation.Remove -> Triple(
            "移除成员？",
            "该成员将无法继续参与群聊。",
            "确认移除",
        )
        is GroupConfirmation.Transfer -> Triple(
            "转让群主？",
            "转让后你将失去群管理权限。",
            "确认转让",
        )
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText, color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(8.dp),
    )
}

private sealed interface GroupConfirmation {
    data object Dissolve : GroupConfirmation
    data object Leave : GroupConfirmation
    data class Remove(val userId: String) : GroupConfirmation
    data class Transfer(val userId: String) : GroupConfirmation
}
