package org.seg7.familywatchlist.ui.avatar

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.seg7.familywatchlist.ui.theme.AvatarSwatches

/**
 * PLAN.md §5 calls avatars "preset emoji + colour combos"; §5a refines that after Kev's review:
 * "keep emoji as one option, not the default aesthetic — add neutral choices (initials on a
 * solid tile, single muted colour) and pull the 12-swatch palette in toward restraint, not
 * primary-colour brightness."
 *
 * So an avatar is now a **style** plus a **swatch**:
 *  - [AvatarStyle.INITIAL] — the profile's first letter on a muted tile. This is the default,
 *    and it is what makes the picker read as a streaming service rather than a kids' app.
 *  - [AvatarStyle.SOLID] — the tile alone, no glyph. The most restrained option.
 *  - [AvatarStyle.EMOJI] — the M2a behaviour, kept because a 7-year-old picking the fox is a
 *    real feature; it's just no longer the only thing on offer.
 *
 * The 12 swatches come from [AvatarSwatches] — desaturated mid-darks, not the M2a primaries.
 */
enum class AvatarStyle { INITIAL, SOLID, EMOJI }

data class AvatarOption(
    val style: AvatarStyle,
    val color: Color,
    /** Only meaningful for [AvatarStyle.EMOJI]; empty otherwise. */
    val emoji: String = "",
)

/** A restrained emoji set — no rainbow/balloon "primary school" glyphs from the M2a list. */
val AVATAR_EMOJI: List<String> = listOf("🍿", "🎬", "🎧", "🎮", "🦊", "🐼", "🐙", "🐳", "🚀", "⚡", "🌙", "🔭")

/**
 * The picker grid's contents: twelve neutral swatch tiles first (the §5a-preferred aesthetic),
 * then the emoji options. Order matters — the first entry is the default for a new profile, and
 * §5a wants that default to be neutral.
 */
val AVATAR_PRESETS: List<AvatarOption> =
    AvatarSwatches.map { AvatarOption(AvatarStyle.INITIAL, it) } +
        AVATAR_EMOJI.mapIndexed { index, emoji ->
            AvatarOption(AvatarStyle.EMOJI, AvatarSwatches[index % AvatarSwatches.size], emoji)
        }

/**
 * [org.seg7.familywatchlist.data.local.entity.ProfileEntity.avatarKey] is a free-form string
 * (PLAN.md §2: "preset emoji+colour").
 *
 * M2a encoded it as `"<emoji>|<RRGGBB>"`. M2b needs a style too, so the format grows a leading
 * style token: `"<STYLE>|<RRGGBB>|<emoji?>"`. [avatarKeyToOption] still parses the old
 * two-field form — a key with no recognised style token is read as the M2a emoji encoding — so
 * profiles created during M2a keep their avatars instead of silently resetting. That
 * backwards-compatible read is why this is a string format rather than a schema change.
 */
fun AvatarOption.toAvatarKey(): String = "${style.name}|${color.toHex()}|$emoji"

fun avatarKeyToOption(key: String): AvatarOption {
    val parts = key.split("|")
    val style = parts.getOrNull(0)?.let { token -> AvatarStyle.entries.find { it.name == token } }

    if (style == null) {
        // Legacy M2a key: "<emoji>|<RRGGBB>".
        val emoji = parts.getOrNull(0)?.takeIf { it.isNotBlank() }
        val color = parts.getOrNull(1).parseHexOrDefault()
        return if (emoji == null) {
            AvatarOption(AvatarStyle.INITIAL, color)
        } else {
            AvatarOption(AvatarStyle.EMOJI, color, emoji)
        }
    }

    return AvatarOption(
        style = style,
        color = parts.getOrNull(1).parseHexOrDefault(),
        emoji = parts.getOrNull(2).orEmpty(),
    )
}

/** Default for a brand-new profile: neutral initial tile on the first swatch. */
fun defaultAvatarOption(): AvatarOption = AVATAR_PRESETS.first()

private fun Color.toHex(): String =
    (toArgb() and 0xFFFFFF).toString(16).padStart(6, '0').uppercase()

private fun String?.parseHexOrDefault(): Color =
    this?.let { hex -> runCatching { Color(("FF$hex").toLong(16).toInt()) }.getOrNull() }
        ?: AvatarSwatches.first()
