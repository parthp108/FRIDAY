
package com.health.friday.tasks

import com.health.friday.data.local.Goal
import com.health.friday.data.local.GoalDao
import kotlinx.coroutines.flow.Flow

class GoalRepository(
    private val goalDao: GoalDao
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

        val id = goalDao.insert(goal)

        return goal.copy(id = id)
    }

    suspend fun setDone(
        goal: Goal,
        done: Boolean
    ) {
        goalDao.update(
            goal.copy(isDone = done)
        )
    }

    suspend fun deleteGoal(
        goal: Goal
    ) {
        goalDao.delete(goal)
    }
}

