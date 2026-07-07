package com.klasmeier.internetgatewaypath.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transitions")
data class TransitionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fromPath: String,
    val toPath: String,
    val publicIp: String?,
    val occurredAtEpochMs: Long,
)
