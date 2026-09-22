package com.smartguard.prototype.profile

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [Profile::class],
    version = 2,          // bumped from 1 → 2 for restrictedPackages + schema cleanup
    exportSchema = false
)
@TypeConverters(
    RoleConverter::class,
    BiometricConverters::class,
    StringListConverter::class
)
abstract class ProfileDatabase : RoomDatabase() {
    abstract fun profileDao(): ProfileDao

    companion object {
        const val DATABASE_NAME = "smartguard_db"
    }
}
