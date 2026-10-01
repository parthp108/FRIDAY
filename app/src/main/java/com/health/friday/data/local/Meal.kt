package com.health.friday.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "meals")
data class Meal(

    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val name: String,

    val mealType: String,

    val calories: Int = 0,

    val protein: Double = 0.0,

    val carbohydrates: Double = 0.0,

    val fat: Double = 0.0,

    val timestamp: Long = System.currentTimeMillis()
)