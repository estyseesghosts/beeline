package me.foxtails.palustris.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.absolutePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import me.foxtails.palustris.R
import me.foxtails.palustris.data.media.MediaImageLoader
import me.foxtails.palustris.domain.Account
import me.foxtails.palustris.ui.components.AccountAvatar
import me.foxtails.palustris.ui.emoji.InlineEmojiText
import me.foxtails.palustris.ui.motion.PopEffect

@Composable
internal fun ProfileHeader(
    account: Account,
    state: ProfileUiState,
    isSelf: Boolean,
    onRefresh: () -> Unit,
    onFollow: () -> Unit,
    onUnfollow: () -> Unit,
    onMessage: () -> Unit = {},
    onOpenProfile: (Account) -> Unit = {},
    onOpenProfileImage: (String) -> Unit = {},
    onEditProfile: (() -> Unit)? = null,
    largeSummary: Boolean = false,
    // Physical insets for everything except the banner, which spans the full header width.
    leftInset: Dp = 0.dp,
    rightInset: Dp = 0.dp,
) {
    val context = LocalContext.current
    val mediaImageLoader = remember(context) { MediaImageLoader.get(context) }
    val statusBarHeight = with(LocalDensity.current) {
        WindowInsets.statusBars.getTop(this).toDp()
    }.coerceAtLeast(1.dp)
    Column(Modifier.fillMaxWidth().testTag("profile_header")) {
        val movedTo = account.movedTo?.takeIf { it.hasUsableProfileIdentity() }
        movedTo?.let { destination ->
            Box(Modifier.absolutePadding(left = leftInset, right = rightInset)) {
                ProfileRedirectBanner(
                    account = account,
                    destination = destination,
                    onOpenProfile = { onOpenProfile(destination) },
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("profile_banner"),
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth().height(152.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
            ) {
                AsyncImage(
                    model = account.bannerUrl,
                    imageLoader = mediaImageLoader.imageLoader,
                    contentDescription = stringResource(R.string.profile_banner),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(152.dp),
                )
            }
            if (statusBarHeight > 0.dp) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(statusBarHeight)
                        .clip(RectangleShape)
                        .testTag("profile_banner_status_bar_blur"),
                ) {
                    AsyncImage(
                        model = account.bannerUrl,
                        imageLoader = mediaImageLoader.imageLoader,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(statusBarHeight).blur(24.dp),
                    )
                }
            }
            Surface(
                modifier = Modifier
                    .then(
                        if (largeSummary) {
                            Modifier.align(Alignment.BottomCenter).size(96.dp).testTag("profile_large_avatar")
                        } else {
                            Modifier.absolutePadding(left = 16.dp + leftInset, top = 104.dp).size(112.dp)
                        },
                    )
                    .clip(CircleShape)
                    .then(account.avatarUrl?.let { Modifier.clickable { onOpenProfileImage(it) } } ?: Modifier),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp,
            ) {
                PopEffect(account.id) { AccountAvatar(account, Modifier.padding(4.dp)) }
            }
            account.bannerUrl?.let { bannerUrl ->
                Box(
                    Modifier
                        .matchParentSize()
                        .clickable { onOpenProfileImage(bannerUrl) },
                )
            }
            ProfileActions(
                state = state,
                accountId = account.id,
                isSelf = isSelf,
                canMessage = movedTo == null,
                onFollow = onFollow,
                onUnfollow = onUnfollow,
                onMessage = onMessage,
                onEditProfile = onEditProfile.takeIf { !largeSummary },
                modifier = Modifier.align(Alignment.BottomEnd).absolutePadding(right = 16.dp + rightInset, bottom = 12.dp),
            )
        }

        Column(Modifier.fillMaxWidth().absolutePadding(left = 16.dp + leftInset, right = 16.dp + rightInset)) {
            ProfileDisplayName(
                account = account,
                style = if (largeSummary) MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                else MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.fillMaxWidth(),
            )
            ProfileHandle(
                handle = account.handle,
                style = if (largeSummary) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            if (account.locked || account.bot) {
                Row(
                    modifier = Modifier.padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (account.locked) ProfileBadge(stringResource(R.string.profile_locked))
                    if (account.bot) ProfileBadge(stringResource(R.string.profile_bot))
                }
            }
            InlineEmojiText(
                text = account.biography.ifBlank { stringResource(R.string.profile_no_biography) },
                emoji = account.emoji,
                modifier = Modifier.padding(top = 16.dp).testTag("profile_biography"),
                style = profileBiographyStyle(largeSummary),
            )
            ProfileStats(account)
            when {
                state.detailLoading -> LinearProfileProgress(stringResource(R.string.profile_loading_details))
                state.staleDetails && state.detailError != null -> ProfileStatus(
                    stringResource(R.string.profile_saved_details, state.detailError ?: ""),
                    stringResource(R.string.notifications_retry),
                    onRefresh,
                )
                state.detailError != null -> ProfileStatus(state.detailError, stringResource(R.string.notifications_retry), onRefresh)
            }
            if (!isSelf && state.relationshipError != null) {
                ProfileStatus(state.relationshipError, stringResource(R.string.notifications_retry), onRefresh)
            }
            Spacer(Modifier.height(20.dp))
        }
    }
}
