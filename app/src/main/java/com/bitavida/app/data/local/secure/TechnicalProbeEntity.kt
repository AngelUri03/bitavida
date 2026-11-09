package com.bitavida.app.data.local.secure

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "technical_probe")
data class TechnicalProbeEntity(
    @PrimaryKey val id: Long,
    val value: String,
)
