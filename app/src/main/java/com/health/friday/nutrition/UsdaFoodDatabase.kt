
package com.health.friday.nutrition

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import java.io.File

internal class UsdaFoodDatabase(
    context: Context
) {

    private val appContext = context.applicationContext

    @Volatile
    private var openedDatabase: SQLiteDatabase? = null

    fun database(): SQLiteDatabase {
        openedDatabase?.let {
            if (it.isOpen) return it
        }

        synchronized(this) {
            openedDatabase?.let {
                if (it.isOpen) return it
            }

            val file = ensureDatabaseFile()

            val database = SQLiteDatabase.openDatabase(
                file.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY
            )

            openedDatabase = database
            return database
        }
    }

    private fun ensureDatabaseFile(): File {
        val destination =
            appContext.getDatabasePath(DATABASE_NAME)

        if (destination.exists() && destination.length() > 0L) {
            return destination
        }

        destination.parentFile?.let { parent ->
            check(parent.exists() || parent.mkdirs()) {
                "Could not create the FRIDAY database directory."
            }
        }

        val temporaryFile = File(
            destination.parentFile,
            "$DATABASE_NAME.tmp"
        )

        try {
            appContext.assets.open(DATABASE_NAME).use { input ->
                temporaryFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            check(temporaryFile.length() > 0L) {
                "The USDA database asset is empty."
            }

            if (destination.exists()) {
                check(destination.delete()) {
                    "Could not replace the incomplete USDA database copy."
                }
            }

            if (!temporaryFile.renameTo(destination)) {
                temporaryFile.copyTo(
                    target = destination,
                    overwrite = true
                )
                check(temporaryFile.delete()) {
                    "Could not clean up the temporary USDA database file."
                }
            }

            return destination
        } catch (exception: Exception) {
            temporaryFile.delete()
            throw IllegalStateException(
                "Could not load assets/$DATABASE_NAME. " +
                        "Check that the database exists in app/src/main/assets.",
                exception
            )
        }
    }

    companion object {
        private const val DATABASE_NAME = "friday_food.db"
    }
}
