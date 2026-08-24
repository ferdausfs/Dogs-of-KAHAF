// GATE STUB — androidx.sqlite.db (referenced by Room migrations).
package androidx.sqlite.db

interface SupportSQLiteDatabase {
    fun execSQL(sql: String)
    fun execSQL(sql: String, bindArgs: Array<Any?>)
    fun query(query: String): android.database.Cursor
    val version: Int
    fun setVersion(version: Int)
    fun beginTransaction()
    fun setTransactionSuccessful()
    fun endTransaction()
    fun close()
}
