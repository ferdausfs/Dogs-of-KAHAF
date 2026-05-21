package com.guardianshield.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "keywords")
data class KeywordFilter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val keyword: String,
    val enabled: Boolean = true
)
