package org.seg7.familywatchlist.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.entity.RefreshLogEntity

@Dao
interface RefreshLogDao {
    @Insert
    suspend fun insert(row: RefreshLogEntity): Long

    @Update
    suspend fun update(row: RefreshLogEntity)

    /** Newest first. The Activity screen's source (offline-first: a plain Room read). */
    @Query("SELECT * FROM refresh_log ORDER BY startedAt DESC, id DESC")
    fun observeAll(): Flow<List<RefreshLogEntity>>

    @Query("SELECT * FROM refresh_log ORDER BY startedAt DESC, id DESC")
    suspend fun getAll(): List<RefreshLogEntity>

    /**
     * The most recent run that genuinely refreshed something (SUCCESS or PARTIAL) — what
     * catch-up detection compares against the last scheduled slot. Returns its finish time (a
     * PARTIAL run still rewrote shortlists, so it counts).
     */
    @Query(
        "SELECT MAX(finishedAt) FROM refresh_log WHERE outcome IN ('SUCCESS', 'PARTIAL') AND finishedAt IS NOT NULL"
    )
    suspend fun lastSuccessfulFinishedAt(): Long?

    @Query("SELECT MAX(startedAt) FROM refresh_log")
    suspend fun lastStartedAt(): Long?

    @Query("SELECT * FROM refresh_log WHERE id = :id")
    suspend fun getById(id: Long): RefreshLogEntity?

    @Query("SELECT MAX(finishedAt) FROM refresh_log WHERE finishedAt IS NOT NULL")
    fun observeLastFinishedAt(): Flow<Long?>

    /** Rewrites runs left RUNNING by a dead process. Only safe while holding the refresh guard. */
    @Query(
        "UPDATE refresh_log SET outcome = 'FAILED', reason = 'Interrupted before finishing', finishedAt = startedAt " +
            "WHERE outcome = 'RUNNING'"
    )
    suspend fun failInterrupted()

    /** Keeps only the newest [keep] rows. */
    @Query(
        "DELETE FROM refresh_log WHERE id NOT IN (SELECT id FROM refresh_log ORDER BY startedAt DESC, id DESC LIMIT :keep)"
    )
    suspend fun pruneToLatest(keep: Int)

    companion object {
        const val KEEP_LATEST: Int = 20
    }
}
