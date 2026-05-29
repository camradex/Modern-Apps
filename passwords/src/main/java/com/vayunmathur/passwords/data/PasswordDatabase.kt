package com.vayunmathur.passwords.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import com.vayunmathur.library.util.TrueDao
import kotlinx.coroutines.flow.Flow

@Dao
interface PasswordDao : TrueDao<Password> {
    @Query("SELECT * FROM Password")
    fun getAllFlow(): Flow<List<Password>>

    @Query("SELECT * FROM Password WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Password?>

    @Upsert
    override suspend fun upsert(value: Password): Long

    @Delete
    override suspend fun delete(value: Password): Int
}

@Database(entities = [Password::class], version = 1)
@TypeConverters(Converters::class)
abstract class PasswordDatabase : RoomDatabase() {
    abstract fun passwordDao(): PasswordDao
}
