package com.vayunmathur.findfamily.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vayunmathur.library.util.DefaultConverters
import com.vayunmathur.library.util.TrueDao
import kotlinx.coroutines.flow.Flow


@Dao
interface LocationValueDao: TrueDao<LocationValue> {
    @Query("SELECT * FROM LocationValue WHERE (userid, timestamp) IN ( SELECT userid, MAX(timestamp) FROM LocationValue GROUP BY userid )")
    fun getLatest(): Flow<List<LocationValue>>

    @Query("SELECT * FROM LocationValue WHERE userid = :userid")
    fun getByUseridFlow(userid: Long): Flow<List<LocationValue>>

    @Query("DELETE FROM LocationValue WHERE timestamp < :cutoffEpochSeconds")
    suspend fun deleteOlderThan(cutoffEpochSeconds: Long)
}

@Dao
interface WaypointDao: TrueDao<Waypoint> {
    @Query("SELECT * FROM Waypoint")
    fun getAllFlow(): Flow<List<Waypoint>>

    @Query("SELECT * FROM Waypoint WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<Waypoint?>
}

@Dao
interface UserDao: TrueDao<User> {
    @Query("SELECT * FROM User")
    fun getAllFlow(): Flow<List<User>>

    @Query("SELECT * FROM User WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<User?>
}

@Dao
interface TemporaryLinkDao: TrueDao<TemporaryLink> {
    @Query("SELECT * FROM TemporaryLink")
    fun getAllFlow(): Flow<List<TemporaryLink>>

    @Query("SELECT * FROM TemporaryLink WHERE id = :id")
    fun getByIdFlow(id: Long): Flow<TemporaryLink?>
}

@Database(entities = [User::class, Waypoint::class, LocationValue::class, TemporaryLink::class], version = 3)
@TypeConverters(DefaultConverters::class)
abstract class FFDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun waypointDao(): WaypointDao
    abstract fun locationValueDao(): LocationValueDao
    abstract fun temporaryLinkDao(): TemporaryLinkDao

    companion object : com.vayunmathur.library.util.DatabaseMigrations {
        override val migrations: List<androidx.room.migration.Migration> = listOf(
            androidx.room.migration.Migration(1, 2) {
                it.execSQL("CREATE INDEX IF NOT EXISTS index_LocationValue_timestamp ON LocationValue (timestamp)")
            },
            androidx.room.migration.Migration(2, 3) {
                it.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_LocationValue_userid_timestamp` " +
                        "ON `LocationValue` (`userid`, `timestamp`)"
                )
            }
        )
    }
}
