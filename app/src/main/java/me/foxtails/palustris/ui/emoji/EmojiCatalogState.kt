package me.foxtails.palustris.ui.emoji

import me.foxtails.palustris.domain.AccountId
import me.foxtails.palustris.domain.CustomEmoji
import me.foxtails.palustris.domain.EmojiPickerPreferences

/**
 * Compose-friendly catalog state; only server-supplied picker-visible entries appear.
 *
 * [accountId] names the account that owns the catalog and the preferences. [pendingPins] holds
 * identities whose pin change is still being saved. [pinFailed] is true after a failed save.
 * Both are transient and belong to one catalog owner.
 */
data class EmojiCatalogState(
    val items: List<CustomEmoji> = emptyList(),
    val initialLoading: Boolean = false,
    val refreshing: Boolean = false,
    val error: String? = null,
    val empty: Boolean = false,
    val unsupported: Boolean = false,
    val hasSnapshot: Boolean = false,
    val preferences: EmojiPickerPreferences = EmojiPickerPreferences(),
    val accountId: AccountId? = null,
    val pendingPins: Set<String> = emptySet(),
    val pinFailed: Boolean = false,
)

/** Transient pin presentation for the picker grid: saving identities, failure, and account scope. */
data class EmojiPinState(
    val pendingPins: Set<String> = emptySet(),
    val failed: Boolean = false,
    val scopeKey: Any? = null,
)

/** Picker preference changes. Each callback is a no-op by default. */
class EmojiPreferenceActions(
    val toggleGroupCollapsed: (String) -> Unit = {},
    val toggleGroupPinned: (String) -> Unit = {},
    val togglePinnedEmoji: (String) -> Unit = {},
)

/** Pin presentation derived from the catalog owner state. */
fun EmojiCatalogState.pinState(): EmojiPinState = EmojiPinState(pendingPins, pinFailed, accountId)
