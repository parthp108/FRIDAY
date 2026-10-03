package com.health.friday.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE meals ADD COLUMN isEstimated INTEGER NOT NULL DEFAULT 0"
        )
    }
}

@Database(
    entities = [
        Meal::class,
        WaterEntry::class,
        Reminder::class,
        TodoItem::class,
        Goal::class,
        JournalEntry::class
    ],
    version = 3,
    exportSchema = false
)
abstract class FridayDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao

    abstract fun waterDao(): WaterDao

    abstract fun reminderDao(): ReminderDao

    abstract fun todoDao(): TodoDao

    abstract fun goalDao(): GoalDao

    abstract fun journalDao(): JournalDao

    companion object {

        @Volatile
        private var INSTANCE: FridayDatabase? = null

        fun getDatabase(context: Context): FridayDatabase {

            return INSTANCE ?: synchronized(this) {

                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    FridayDatabase::class.java,
                    "friday_database"
                )
                    .addMigrations(MIGRATION_2_3)
                    .build()

                INSTANCE = instance

                instance
            }
        }
    }
}