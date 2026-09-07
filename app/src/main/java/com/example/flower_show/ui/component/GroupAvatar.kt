package com.example.flower_show.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.flower_show.model.ChatAvatarMember
import com.example.flower_show.ui.theme.FlowerShowTheme

@Composable
fun GroupAvatar(
    displayAvatarUrl: String?,
    avatarMembers: List<ChatAvatarMember>,
    displayName: String,
    modifier: Modifier = Modifier,
    size: Dp = SocialDimens.avatarSmall,
    testTag: String = "group_avatar",
) {
    val visibleMembers = avatarMembers.take(MaxVisibleAvatarMembers)
    val resolvedName = displayName.takeIf(String::isNotBlank) ?: "群聊"
    Box(
        modifier = Modifier
            .size(size)
            .then(modifier)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.outlineVariant)
            .semantics { contentDescription = "${resolvedName}头像" }
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        if (!displayAvatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = displayAvatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("${testTag}_custom"),
            )
        } else if (visibleMembers.isNotEmpty()) {
            AvatarMemberGrid(
                members = visibleMembers,
                testTag = testTag,
            )
        } else {
            AvatarInitial(
                nickname = resolvedName,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("${testTag}_fallback"),
            )
        }
    }
}

@Composable
private fun AvatarMemberGrid(
    members: List<ChatAvatarMember>,
    testTag: String,
) {
    when (members.size) {
        1 -> AvatarMemberCell(
            member = members[0],
            index = 0,
            testTag = testTag,
            modifier = Modifier.fillMaxSize(),
        )

        2 -> Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(AvatarCellGap),
        ) {
            members.forEachIndexed { index, member ->
                AvatarMemberCell(
                    member = member,
                    index = index,
                    testTag = testTag,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }

        else -> Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(AvatarCellGap),
        ) {
            AvatarMemberGridRow(
                first = members[0],
                second = members.getOrNull(1),
                firstIndex = 0,
                testTag = testTag,
                modifier = Modifier.weight(1f),
            )
            AvatarMemberGridRow(
                first = members[2],
                second = members.getOrNull(3),
                firstIndex = 2,
                testTag = testTag,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AvatarMemberGridRow(
    first: ChatAvatarMember,
    second: ChatAvatarMember?,
    firstIndex: Int,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(AvatarCellGap),
    ) {
        AvatarMemberCell(
            member = first,
            index = firstIndex,
            testTag = testTag,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
        )
        if (second == null) {
            Spacer(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        } else {
            AvatarMemberCell(
                member = second,
                index = firstIndex + 1,
                testTag = testTag,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}

@Composable
private fun AvatarMemberCell(
    member: ChatAvatarMember,
    index: Int,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .testTag("${testTag}_member_$index"),
        contentAlignment = Alignment.Center,
    ) {
        if (!member.avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = member.avatarUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("${testTag}_member_${index}_image"),
            )
        } else {
            AvatarInitial(
                nickname = member.nickname.orEmpty(),
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("${testTag}_member_${index}_initial"),
            )
        }
    }
}

@Composable
private fun AvatarInitial(
    nickname: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = nickname.trim().take(1).ifBlank { "群" },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun GroupAvatarPreview() {
    FlowerShowTheme {
        GroupAvatar(
            displayAvatarUrl = null,
            avatarMembers = listOf(
                ChatAvatarMember("owner", "花", null),
                ChatAvatarMember("member-2", "林", null),
                ChatAvatarMember("member-3", "叶", null),
                ChatAvatarMember("member-4", "雨", null),
            ),
            displayName = "花友群",
        )
    }
}

private val AvatarCellGap = 1.dp
private const val MaxVisibleAvatarMembers = 4
