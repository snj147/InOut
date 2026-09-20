package com.inout.vault.data

import androidx.room.TypeConverter

class AppTypeConverters {

    @TypeConverter
    fun fromMovementNature(nature: MovementNature?): String? {
        return nature?.name
    }

    @TypeConverter
    fun toMovementNature(value: String?): MovementNature? {
        return value?.let { enumValueOf<MovementNature>(it) }
    }

    @TypeConverter
    fun fromCadenceType(cadence: CadenceType?): String? {
        return cadence?.name
    }

    @TypeConverter
    fun toCadenceType(value: String?): CadenceType? {
        return value?.let { enumValueOf<CadenceType>(it) }
    }
}
