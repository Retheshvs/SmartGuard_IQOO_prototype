package com.smartguard.prototype.profile

import androidx.room.TypeConverter

/** Room TypeConverter for the [Role] enum. */
class RoleConverter {
    @TypeConverter
    fun fromRole(role: Role): String = role.name

    @TypeConverter
    fun toRole(value: String): Role = Role.valueOf(value)
}
