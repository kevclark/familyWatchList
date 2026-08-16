package org.seg7.familywatchlist.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/**
 * PLAN.md §2: TitleAttribute — one normalized table serving both display (cast chips) and the
 * recommender (PLAN.md §4 affinity vectors). attrType GENRE|CAST|CREW|KEYWORD; for CREW only
 * director/creator rows are stored, per the plan.
 */
@Entity(
    tableName = "title_attributes",
    primaryKeys = ["tmdbId", "mediaType", "attrType", "attrId"],
    indices = [Index(value = ["tmdbId", "mediaType"])],
)
data class TitleAttributeEntity(
    val tmdbId: Int,
    val mediaType: MediaType,
    val attrType: AttrType,
    val attrId: Int,
    val name: String,
    /** Cast billing order; null for genre/keyword/crew rows. */
    val ord: Int?,
)
