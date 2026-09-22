package com.smartguard.prototype.profile

import androidx.room.TypeConverter

/**
 * Room TypeConverter for [List<String>] — used to persist [Profile.restrictedPackages].
 *
 * Serialisation: pipe-delimited string ("com.foo|com.bar").
 * Pipe is chosen over comma to avoid conflicts with package names (which contain dots only).
 */
class StringListConverter {
    @TypeConverter
    fun fromList(list: List<String>?): String? {
        return list?.joinToString("|")
    }

    @TypeConverter
    fun toList(value: String?): List<String> {
        if (value.isNullOrBlank()) return emptyList()
        return value.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
