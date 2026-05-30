package com.vayunmathur.weather.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [SavedLocation::class, WeatherCache::class],
    version = 1,
    exportSchema = true,
)
abstract class WeatherDatabase : RoomDatabase() {
    abstract fun weatherDao(): WeatherDao
}
