package org.seg7.familywatchlist.ui.avatar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * PLAN.md §5: profile picker avatars are "preset emoji + colour combos" — the plan doesn't
 * pin an exact set, so this is a judgment call: a dozen kid-and-adult-friendly emoji, each
 * paired with a distinct colour from a streaming-app-y palette (not tied to the M0 brand
 * colours, so profiles are visually distinguishable from each other and from the app chrome).
 */
data class AvatarOption(val emoji: String, val color: Color)

val AVATAR_PRESETS: List<AvatarOption> = listOf(
    AvatarOption("🍿", Color(0xFFFFC24B)),
    AvatarOption("🎬", Color(0xFF3D5AFE)),
    AvatarOption("🦊", Color(0xFFEF6C00)),
    AvatarOption("🐼", Color(0xFF546E7A)),
    AvatarOption("🚀", Color(0xFF8E24AA)),
    AvatarOption("🌟", Color(0xFFFDD835)),
    AvatarOption("🐙", Color(0xFF00897B)),
    AvatarOption("🐝", Color(0xFFFFB300)),
    AvatarOption("🦁", Color(0xFFD84315)),
    AvatarOption("🐳", Color(0xFF1E88E5)),
    AvatarOption("🎈", Color(0xFFC2185B)),
    AvatarOption("🌈", Color(0xFF00ACC1)),
)

/**
 * [ProfileEntity.avatarKey] is a free-form string (PLAN.md §2 kdoc: "preset emoji+colour").
 * Encoded here as `"<emoji>|<RRGGBB>"` so it's self-describing and survives [AVATAR_PRESETS]
 * being reordered or extended later, rather than storing a fragile list index.
 */
fun AvatarOption.toAvatarKey(): String {
    val hex = (color.toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()
    return "$emoji|$hex"
}

fun avatarKeyToOption(key: String): AvatarOption {
    val parts = key.split("|", limit = 2)
    val emoji = parts.getOrNull(0)?.takeIf { it.isNotBlank() } ?: AVATAR_PRESETS.first().emoji
    val color = parts.getOrNull(1)?.let { hex ->
        runCatching { Color(("FF$hex").toLong(16).toInt()) }.getOrNull()
    } ?: AVATAR_PRESETS.first().color
    return AvatarOption(emoji, color)
}
