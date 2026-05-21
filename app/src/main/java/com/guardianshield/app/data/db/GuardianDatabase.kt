package com.guardianshield.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.guardianshield.app.data.model.ActivityLog
import com.guardianshield.app.data.model.AppRule
import com.guardianshield.app.data.model.KeywordFilter
import com.guardianshield.app.data.model.Schedule

@Database(
    entities = [AppRule::class, ActivityLog::class, KeywordFilter::class, Schedule::class],
    version = 2,
    exportSchema = false
)
abstract class GuardianDatabase : RoomDatabase() {
    abstract fun appRuleDao(): AppRuleDao
    abstract fun activityLogDao(): ActivityLogDao
    abstract fun keywordDao(): KeywordDao
    abstract fun scheduleDao(): ScheduleDao

    companion object {
        @Volatile private var INSTANCE: GuardianDatabase? = null

        fun getInstance(ctx: Context): GuardianDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    ctx.applicationContext,
                    GuardianDatabase::class.java,
                    "guardian.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
