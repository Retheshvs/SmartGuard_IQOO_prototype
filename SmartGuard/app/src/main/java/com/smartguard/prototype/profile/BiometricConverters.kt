package com.smartguard.prototype.profile

import androidx.room.TypeConverter

/** Room TypeConverters for biometric data types. */
class BiometricConverters {
    @TypeConverter
    fun fromFloatArray(array: FloatArray?): String? {
        return array?.joinToString(",")
    }

    @TypeConverter
    fun toFloatArray(value: String?): FloatArray? {
        return value?.split(",")?.mapNotNull { it.toFloatOrNull() }?.toFloatArray()
    }
}
