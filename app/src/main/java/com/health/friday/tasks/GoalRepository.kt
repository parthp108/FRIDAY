
package com.health.friday.tasks

import com.health.friday.data.local.Goal
import com.health.friday.data.local.GoalDao
import com.health.friday.shared.sync.cloud.CloudSyncEntityTypes
import com.health.friday.sync.cloud.AndroidCloudSyncWriter
import com.health.friday.sync.cloud.CloudSyncMutation
import com.health.friday.sync.cloud.toCloudSyncPayload
import kotlinx.coroutines.flow.Flow

class GoalRepository(
    private val goalDao: GoalDao,
    private val cloudSyncWriter: AndroidCloudSyncWriter? = null
) {

    fun getGoals(): Flow<List<Goal>> {
        return goalDao.getAll()
    }

    // Returns the saved goal, or null if the title was blank.
    suspend fun addGoal(
        title: String,
        targetDate: Long? = null
    ): Goal? {
        val cleanTitle = title.trim()

        if (cleanTitle.isEmpty()) {
            return null
        }

        val goal = Goal(
            title = cleanTitle,
            targetDate = targetDate
        )

        val writer = cloudSyncWriter

        return if (writer == null) {
            val id = goalDao.insert(goal)
            goal.copy(id = id)
        } else {
            writer.upsert(
                entityType = CloudSyncEntityTypes.GOAL,
                entityId = goal.syncId
            ) { hlc ->
                val stamped = goal.copy(syncHlc = hlc)
                val id = goalDao.insert(stamped)

                CloudSyncMutation(
                    value = stamped.copy(id = id),
                    payload = stamped.toCloudSyncPayload()
                )
            }
        }
    }

    suspend fun setDone(
        goal: Goal,
        done: Boolean
    ) {
        val updated = goal.copy(
            isDone = done
        )

        val writer = cloudSyncWriter

        if (writer == null) {
            goalDao.update(updated)
        } else {
            writer.upsert(
                entityType = CloudSyncEntityTypes.GOAL,
                entityId = goal.syncId
            ) { hlc ->
                val stamped = updated.copy(syncHlc = hlc)
                goalDao.update(stamped)

                CloudSyncMutation(
                    value = Unit,
                    payload = stamped.toCloudSyncPayload()
                )
            }
        }
    }

    suspend fun deleteGoal(
        goal: Goal
    ) {
        val writer = cloudSyncWriter

        if (writer == null) {
            goalDao.delete(goal)
        } else {
            writer.delete(
                entityType = CloudSyncEntityTypes.GOAL,
                entityId = goal.syncId
            ) {
                goalDao.delete(goal)
            }
        }
    }
}

