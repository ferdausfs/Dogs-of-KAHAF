package com.kahaf.guardian.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.kahaf.guardian.data.local.db.dao.AppDao
import com.kahaf.guardian.data.local.db.dao.BlockLogDao
import com.kahaf.guardian.data.local.db.entity.AppEntity
import com.kahaf.guardian.data.local.db.entity.BlockLogEntity

@Database(entities = [AppEntity::class, BlockLogEntity::class], version = 1, exportSchema = false)
abstract class KahafDatabase : RoomDatabase() {
    abstract fun appDao(): AppDao
    abstract fun blockLogDao(): BlockLogDao
}
