// GATE STUB — androidx.room.
package androidx.room

import android.content.Context
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlin.reflect.KClass

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Database(
    val entities: Array<KClass<*>>,
    val version: Int,
    val exportSchema: Boolean = true
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Entity(
    val tableName: String = "",
    val primaryKeys: Array<String> = [],
    val ignoredColumns: Array<String> = []
)

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Dao

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Query(val value: String)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Insert(val onConflict: Int = OnConflictStrategy.ABORT)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Delete

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Update(val onConflict: Int = OnConflictStrategy.ABORT)

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
annotation class Upsert

@Target(AnnotationTarget.FUNCTION, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Transaction

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class PrimaryKey(val autoGenerate: Boolean = false)

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.BINARY)
annotation class ColumnInfo(val name: String = "")

@Target(AnnotationTarget.FIELD, AnnotationTarget.PROPERTY, AnnotationTarget.CONSTRUCTOR)
@Retention(AnnotationRetention.BINARY)
annotation class Ignore

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class OnConflictStrategy {
    companion object {
        const val ROLLBACK: Int = 1
        const val ABORT: Int = 3
        const val FAIL: Int = 4
        const val IGNORE: Int = 5
        const val REPLACE: Int = 6
    }
}

abstract class RoomDatabase {
    open class Callback {
        open fun onCreate(db: SupportSQLiteDatabase) {}
        open fun onOpen(db: SupportSQLiteDatabase) {}
    }

    open class Builder<T : RoomDatabase> {
        open fun addMigrations(vararg migrations: Migration): Builder<T> = this
        open fun addCallback(callback: Callback): Builder<T> = this
        open fun fallbackToDestructiveMigration(): Builder<T> = this
        open fun allowMainThreadQueries(): Builder<T> = this
        open fun build(): T = throw RuntimeException("stub")
    }
}

class Room {
    companion object {
        fun <T : RoomDatabase> databaseBuilder(
            context: Context,
            klass: Class<T>,
            name: String?
        ): RoomDatabase.Builder<T> = RoomDatabase.Builder()

        fun <T : RoomDatabase> inMemoryDatabaseBuilder(
            context: Context,
            klass: Class<T>
        ): RoomDatabase.Builder<T> = RoomDatabase.Builder()
    }
}
