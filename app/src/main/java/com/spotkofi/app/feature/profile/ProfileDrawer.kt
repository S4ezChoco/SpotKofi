package com.spotkofi.app.feature.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.spotkofi.app.R
import com.spotkofi.app.data.model.Conversation
import com.spotkofi.app.data.model.FriendActivity
import com.spotkofi.app.data.repository.previewConversations
import com.spotkofi.app.data.repository.previewFriends
import com.spotkofi.app.ui.components.Artwork
import com.spotkofi.app.ui.theme.SpotKofiTheme

/**
 * The account drawer that slides in from the leading edge.
 *
 * Also the home of Messages, which matters for the roadmap: the DM list and the
 * friend-activity strip here are the surfaces Phase 4 will back with Supabase
 * Realtime, gated so a thread only exists between mutual follows.
 */
@Composable
fun ProfileDrawer(
    userName: String,
    friends: List<FriendActivity>,
    conversations: List<Conversation>,
    onSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Column(
        modifier = modifier
            .background(colors.elevated)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = dimens.spaceXl),
    ) {
        // ---- Identity ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Phase 3: public profile */ }
                .padding(dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(id = userName, size = 52.dp, shape = CircleShape)
            Spacer(Modifier.width(dimens.spaceMd))
            Column {
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineMedium,
                    color = colors.textPrimary,
                )
                Text(
                    text = "View profile",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        HorizontalDivider(color = colors.divider)

        Spacer(Modifier.height(dimens.spaceSm))

        // ---- Account menu ----
        DrawerRow(Icons.Filled.Add, "Add account") { }
        // No subscription tier shown: there is no account system yet, so a plan
        // badge here would be inventing state the app does not have.
        DrawerRow(Icons.Filled.WorkspacePremium, "Subscription") { }
        DrawerRow(Icons.Filled.ShowChart, "Listening stats") { }
        DrawerRow(Icons.Filled.History, "Recents") { }
        DrawerRow(Icons.Filled.Campaign, "Your Updates") { }
        DrawerRow(Icons.Filled.Settings, "Settings and privacy", onClick = onSettings)

        Spacer(Modifier.height(dimens.spaceLg))

        // ---- Friend activity ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg),
            horizontalArrangement = Arrangement.spacedBy(dimens.spaceLg),
        ) {
            friends.forEach { friend -> FriendBubble(friend) }
            InviteBubble()
        }

        Spacer(Modifier.height(dimens.spaceXl))

        // ---- Messages ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = dimens.spaceLg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { /* Phase 4: full message list */ },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.headlineLarge,
                    color = colors.textPrimary,
                )
                Icon(
                    imageVector = Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
            IconButton(onClick = { /* Phase 4: compose */ }) {
                Icon(
                    imageVector = Icons.Filled.Create,
                    contentDescription = stringResource(R.string.cd_new_message),
                    tint = colors.textPrimary,
                    modifier = Modifier.size(dimens.iconMd),
                )
            }
        }

        Spacer(Modifier.height(dimens.spaceSm))

        conversations.forEach { conversation ->
            ConversationRow(conversation)
        }

        // ---- New message ----
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { /* Phase 4: compose */ }
                .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(colors.iconWell, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Create,
                    contentDescription = null,
                    tint = colors.textSecondary,
                    modifier = Modifier.size(dimens.iconSm),
                )
            }
            Spacer(Modifier.width(dimens.spaceMd))
            Text(
                text = "New message",
                style = MaterialTheme.typography.titleLarge,
                color = colors.textPrimary,
            )
        }
    }
}

@Composable
private fun DrawerRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = colors.textPrimary,
            modifier = Modifier.size(dimens.iconMd),
        )
        Spacer(Modifier.width(dimens.spaceLg))
        Text(
            text = label,
            style = MaterialTheme.typography.titleLarge,
            color = colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Friend avatar with the small now-playing artwork tucked into the corner. */
@Composable
private fun FriendBubble(friend: FriendActivity) {
    val colors = SpotKofiTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(56.dp),
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            Artwork(
                id = friend.id,
                size = 44.dp,
                shape = CircleShape,
                modifier = if (friend.isOnline) {
                    Modifier.border(2.dp, colors.accent, CircleShape)
                } else {
                    Modifier
                },
            )
            if (friend.nowPlaying != null) {
                Artwork(
                    id = friend.id + "_np",
                    size = 20.dp,
                    shape = RoundedCornerShape(3.dp),
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        Text(
            text = friend.name,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = friend.nowPlaying ?: if (friend.isOnline) "On" else "",
            style = MaterialTheme.typography.bodySmall,
            // "On" is the presence indicator, so it takes the accent.
            color = if (friend.nowPlaying == null && friend.isOnline) {
                colors.accent
            } else {
                colors.textSecondary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun InviteBubble() {
    val colors = SpotKofiTheme.colors

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(56.dp)
            .clickable { /* Phase 4: invite flow */ },
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(colors.iconWell, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(SpotKofiTheme.dimens.iconMd),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Invite friends",
            style = MaterialTheme.typography.labelMedium,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ConversationRow(conversation: Conversation) {
    val colors = SpotKofiTheme.colors
    val dimens = SpotKofiTheme.dimens

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* Phase 4: open the thread */ }
            .padding(horizontal = dimens.spaceLg, vertical = dimens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(id = conversation.id, size = 44.dp, shape = CircleShape)
        Spacer(Modifier.width(dimens.spaceMd))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = conversation.personName,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (conversation.isSharedTrack) {
                    Icon(
                        imageVector = Icons.Filled.Schedule,
                        contentDescription = null,
                        tint = colors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(3.dp))
                }
                Text(
                    text = conversation.preview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (!conversation.isSharedTrack) {
                    Text(
                        text = " \u2022 ${conversation.timestamp}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Preview(backgroundColor = 0xFF121212, showBackground = true, heightDp = 1000, widthDp = 340)
@Composable
private fun ProfileDrawerPreview() {
    SpotKofiTheme {
        ProfileDrawer(
            userName = "CHOCO",
            friends = previewFriends(),
            conversations = previewConversations(),
            onSettings = {},
            onDismiss = {},
        )
    }
}
