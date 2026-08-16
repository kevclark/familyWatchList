package org.seg7.familywatchlist.data.local

import androidx.room.TypeConverter
import java.time.LocalDate
import org.seg7.familywatchlist.data.local.entity.AttrType
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.data.local.entity.ProviderKind
import org.seg7.familywatchlist.data.local.entity.RatingValue
import org.seg7.familywatchlist.data.local.entity.ShortlistState
import org.seg7.familywatchlist.data.local.entity.WatchlistState

/**
 * Room stores every enum in PLAN.md §2 as its TEXT name (never an ordinal — safe to reorder
 * or extend enums without corrupting stored data), and every date-only column (no time-of-day)
 * as an ISO-8601 string via [LocalDate].
 */
class Converters {
    @TypeConverter
    fun mediaTypeToString(value: MediaType): String = value.name

    @TypeConverter
    fun stringToMediaType(value: String): MediaType = MediaType.valueOf(value)

    @TypeConverter
    fun attrTypeToString(value: AttrType): String = value.name

    @TypeConverter
    fun stringToAttrType(value: String): AttrType = AttrType.valueOf(value)

    @TypeConverter
    fun ratingValueToString(value: RatingValue): String = value.name

    @TypeConverter
    fun stringToRatingValue(value: String): RatingValue = RatingValue.valueOf(value)

    @TypeConverter
    fun watchlistStateToString(value: WatchlistState): String = value.name

    @TypeConverter
    fun stringToWatchlistState(value: String): WatchlistState = WatchlistState.valueOf(value)

    @TypeConverter
    fun providerKindToString(value: ProviderKind): String = value.name

    @TypeConverter
    fun stringToProviderKind(value: String): ProviderKind = ProviderKind.valueOf(value)

    @TypeConverter
    fun shortlistStateToString(value: ShortlistState): String = value.name

    @TypeConverter
    fun stringToShortlistState(value: String): ShortlistState = ShortlistState.valueOf(value)

    @TypeConverter
    fun localDateToString(value: LocalDate): String = value.toString()

    @TypeConverter
    fun stringToLocalDate(value: String): LocalDate = LocalDate.parse(value)
}
