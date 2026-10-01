package com.health.friday.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Meal::class,
        WaterEntry::class,
        Reminder::class
    ],
    version = 1,
    exportSchema = false
)
abstract class FridayDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao

    abstract fun waterDao(): WaterDao

    abstract fun reminderDao(): ReminderDao

    companion object {

        @Volatile
        private var INSTANCE: FridayDatabase? = null

        fun getDatabase(context: Context): FridayDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FridayDatabase::class.java,
                    "friday_database"
                ).build()

                INSTANCE = instance

                instance
            }
        }
    }
}