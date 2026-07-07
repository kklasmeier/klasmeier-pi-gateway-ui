package com.klasmeier.internetgatewaypath.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface TransitionDao {
    @Insert
    suspend fun insert(entity: TransitionEntity)

    @Query("SELECT * FROM transitions ORDER BY occurredAtEpochMs DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<TransitionEntity>

    @Query("SELECT COUNT(*) FROM transitions")
    suspend fun count(): Int

    @Query(
        """
        DELETE FROM transitions WHERE id NOT IN (
            SELECT id FROM transitions ORDER BY occurredAtEpochMs DESC LIMIT :keep
        )
        """,
    )
    suspend fun trimTo(keep: Int)

    @Query("DELETE FROM transitions WHERE occurredAtEpochMs < :cutoffEpochMs")
    suspend fun deleteOlderThan(cutoffEpochMs: Long)
}
