package com.health.friday.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.health.friday.alarms.Alarm
import com.health.friday.alarms.AlarmDao
import com.health.friday.memory.MemoryFactDao
import com.health.friday.memory.MemoryFactEntity
import com.health.friday.nutrition.calibration.FoodCalibrationDao
import com.health.friday.nutrition.calibration.FoodCalibrationEntity
import com.health.friday.proactive.NudgeHistoryEntity
import com.health.friday.proactive.ProactiveDao
import com.health.friday.proactive.ProtocolEntity

/*
 * DATABASE VERSION RULES
 *
 * 1. The version number only ever goes UP. Never lower it.
 * 2. Every time an @Entity changes (new column, new table), bump the version
 *    AND add a Migration below AND register it in addMigrations(...).
 * 3. Migrations here are written to be safe to run on a database that already
 *    has the change (IF NOT EXISTS / column check), because the phone may hold
 *    a database created by any recent build.
 */

private fun columnExists(
    db: SupportSQLiteDatabase,
    table: String,
    column: String
): Boolean {
    db.query("PRAGMA table_info($table)").use { cursor ->
        val nameIndex = cursor.getColumnIndex("name")

        while (cursor.moveToNext()) {
            if (cursor.getString(nameIndex) == column) {
                return true
            }
        }
    }

    return false
}

private fun addColumnIfMissing(
    db: SupportSQLiteDatabase,
    table: String,
    column: String,
    definition: String
) {
    if (!columnExists(db, table, column)) {
        db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
}

private val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS nudge_history (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                type TEXT NOT NULL,
                timestamp INTEGER NOT NULL,
                interacted INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS protocols (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                title TEXT NOT NULL,
                triggerConditionJson TEXT NOT NULL,
                actionTool TEXT NOT NULL,
                actionPayloadJson TEXT NOT NULL,
                autonomyLevel TEXT NOT NULL,
                isEnabled INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_facts (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                fact TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent()
        )
    }
}

// Adds the semantic-search embedding column used by the journal / todo / goal RAG.
private val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfMissing(db, "todo_items", "embeddingJson", "TEXT")
        addColumnIfMissing(db, "goals", "embeddingJson", "TEXT")
        addColumnIfMissing(db, "journal_entries", "embeddingJson", "TEXT")
    }
}

private val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        addColumnIfMissing(db, "todo_items", "embeddingJson", "TEXT")
        addColumnIfMissing(db, "goals", "embeddingJson", "TEXT")
        addColumnIfMissing(db, "journal_entries", "embeddingJson", "TEXT")
        addColumnIfMissing(db, "daily_signals", "activeCaloriesKcal", "INTEGER")
        addColumnIfMissing(db, "daily_signals", "distanceMeters", "REAL")
        addColumnIfMissing(db, "daily_signals", "hrvMs", "REAL")
        addColumnIfMissing(db, "daily_signals", "spo2Percent", "REAL")
    }
}

private val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cloud_sync_outbox (
                operationId TEXT NOT NULL PRIMARY KEY,
                originDeviceId TEXT NOT NULL,
                entityType TEXT NOT NULL,
                entityId TEXT NOT NULL,
                operationType TEXT NOT NULL,
                hlc TEXT NOT NULL,
                createdAtMs INTEGER NOT NULL,
                schemaVersion INTEGER NOT NULL,
                payloadJson TEXT,
                status TEXT NOT NULL DEFAULT 'PENDING',
                batchId TEXT,
                uploadedAtMs INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_cloud_sync_outbox_status_createdAtMs
            ON cloud_sync_outbox(status, createdAtMs)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cloud_sync_inbox (
                operationId TEXT NOT NULL PRIMARY KEY,
                batchId TEXT NOT NULL,
                originDeviceId TEXT NOT NULL,
                processedAtMs INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS cloud_sync_cursor (
                id INTEGER NOT NULL PRIMARY KEY,
                drivePageToken TEXT,
                lastSuccessfulSyncAtMs INTEGER
            )
            """.trimIndent()
        )
    }
}

private val MIGRATION_15_16 = object : Migration(15, 16) {
    override fun migrate(db: SupportSQLiteDatabase) {

        val tables = listOf(
            "meals",
            "water_entries",
            "todo_items",
            "goals",
            "journal_entries"
        )

        for (table in tables) {
            db.execSQL(
                "ALTER TABLE $table ADD COLUMN syncId TEXT NOT NULL DEFAULT ''"
            )

            db.execSQL(
                "ALTER TABLE $table ADD COLUMN syncHlc TEXT"
            )

            // Give every existing record its own cloud identity.
            db.execSQL(
                """
                UPDATE $table
                SET syncId = lower(hex(randomblob(16)))
                WHERE syncId = ''
                """.trimIndent()
            )

            db.execSQL(
                """
                CREATE UNIQUE INDEX IF NOT EXISTS
                index_${table}_syncId
                ON $table(syncId)
                """.trimIndent()
            )
        }
    }
}

@Database(
    entities = [
        Meal::class, WaterEntry::class, Reminder::class, Alarm::class,
        TodoItem::class, Goal::class, JournalEntry::class, ChatConversation::class,
        ChatMessage::class, ActionLogEntity::class, DailySignalsEntity::class,
        FoodCalibrationEntity::class, NudgeHistoryEntity::class, ProtocolEntity::class,
        MemoryFactEntity::class, CloudSyncOutboxEntity::class, CloudSyncInboxEntity::class,
        CloudSyncCursorEntity::class
    ],
    version = 16,
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
    abstract fun actionLogDao(): ActionLogDao
    abstract fun dailySignalsDao(): DailySignalsDao
    abstract fun foodCalibrationDao(): FoodCalibrationDao
    abstract fun proactiveDao(): ProactiveDao
    abstract fun memoryFactDao(): MemoryFactDao
    abstract fun cloudSyncDao(): CloudSyncDao

    companion object {
        @Volatile
        private var INSTANCE: FridayDatabase? = null

        fun getDatabase(context: Context): FridayDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    FridayDatabase::class.java,
                    "friday_database"
                )
                    .addMigrations(
                        MIGRATION_10_11,
                        MIGRATION_11_12,
                        MIGRATION_12_13,
                        MIGRATION_13_14,
                        MIGRATION_14_15,
                        MIGRATION_15_16
                    )
                    .fallbackToDestructiveMigration(true)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
