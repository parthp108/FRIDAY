
package com.health.friday.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.health.friday.alarms.Alarm
import com.health.friday.alarms.AlarmDao

private val MIGRATION_2_3 = object : Migration(2, 3) {

    override fun migrate(
        db: SupportSQLiteDatabase
    ) {
        db.execSQL(
            "ALTER TABLE meals ADD COLUMN isEstimated INTEGER NOT NULL DEFAULT 0"
        )
    }
}

private val MIGRATION_3_4 = object : Migration(3, 4) {

    override fun migrate(
        db: SupportSQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_conversations (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                updatedAt INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                conversationId INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                createdAt INTEGER NOT NULL,
                FOREIGN KEY(conversationId)
                    REFERENCES chat_conversations(id)
                    ON DELETE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS index_chat_messages_conversationId
            ON chat_messages(conversationId)
            """.trimIndent()
        )
    }
}

private val MIGRATION_4_5 = object : Migration(4, 5) {

    override fun migrate(
        db: SupportSQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reminders (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                timeMillis INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                repeatDaily INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_5_6 = object : Migration(5, 6) {

    override fun migrate(
        db: SupportSQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS alarms (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                timeMillis INTEGER NOT NULL,
                enabled INTEGER NOT NULL,
                repeatDaily INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

@Database(
    entities = [
        Meal::class,
        WaterEntry::class,
        Reminder::class,
        Alarm::class,
        TodoItem::class,
        Goal::class,
        JournalEntry::class,
        ChatConversation::class,
        ChatMessage::class
    ],
    version = 6,
    exportSchema = false
)
abstract class FridayDatabase : RoomDatabase() {

    abstract fun mealDao(): MealDao

    abstract fun waterDao(): WaterDao

    abstract fun reminderDao(): ReminderDao

    abstract fun alarmDao(): AlarmDao

    abstract fun todoDao(): TodoDao

    abstract fun goalDao(): GoalDao

    abstract fun journalDao(): JournalDao

    abstract fun chatConversationDao(): ChatConversationDao

    abstract fun chatMessageDao(): ChatMessageDao

    companion object {

        @Volatile
        private var INSTANCE: FridayDatabase? = null

        fun getDatabase(
            context: Context
        ): FridayDatabase {

            return INSTANCE
                ?: synchronized(this) {

                    val instance =
                        Room.databaseBuilder(
                            context.applicationContext,
                            FridayDatabase::class.java,
                            "friday_database"
                        )
                            .addMigrations(
                                MIGRATION_2_3,
                                MIGRATION_3_4,
                                MIGRATION_4_5,
                                MIGRATION_5_6
                            )
                            .build()

                    INSTANCE = instance

                    instance
                }
        }
    }
}

