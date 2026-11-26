package com.health.friday.sync.cloud

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.health.friday.data.local.CloudSyncBackfillStateEntity
import com.health.friday.data.local.FridayDatabase
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class CloudSyncBackfillStateTest {

    @Test
    fun completionStateSurvivesDatabaseRestart() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val databaseName = "cloud-backfill-${UUID.randomUUID()}.db"

        try {
            var database = Room.databaseBuilder(
                context,
                FridayDatabase::class.java,
                databaseName
            )
                .allowMainThreadQueries()
                .build()

            val dao = database.cloudSyncDao()

            assertFalse(dao.isBackfillStageComplete("meals_v1"))

            dao.saveBackfillStage(
                CloudSyncBackfillStateEntity(
                    stage = "meals_v1",
                    completedAtMs = 123456789L
                )
            )

            assertTrue(dao.isBackfillStageComplete("meals_v1"))

            database.close()

            database = Room.databaseBuilder(
                context,
                FridayDatabase::class.java,
                databaseName
            )
                .allowMainThreadQueries()
                .build()

            assertTrue(
                database.cloudSyncDao()
                    .isBackfillStageComplete("meals_v1")
            )

            database.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }

    @Test
    fun categoriesHaveIndependentCompletionState() = runBlocking {
        val context =
            InstrumentationRegistry.getInstrumentation().targetContext

        val databaseName = "cloud-backfill-${UUID.randomUUID()}.db"

        try {
            val database = Room.databaseBuilder(
                context,
                FridayDatabase::class.java,
                databaseName
            )
                .allowMainThreadQueries()
                .build()

            val dao = database.cloudSyncDao()

            dao.saveBackfillStage(
                CloudSyncBackfillStateEntity(
                    stage = "meals_v1",
                    completedAtMs = 123456789L
                )
            )

            assertTrue(dao.isBackfillStageComplete("meals_v1"))
            assertFalse(dao.isBackfillStageComplete("water_v1"))

            database.close()
        } finally {
            context.deleteDatabase(databaseName)
        }
    }
}
