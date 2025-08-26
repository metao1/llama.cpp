package com.metao.ai.data.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.metao.ai.data.database.dao.CategorizationDao
import com.metao.ai.data.database.entities.CategorizationResultConverters
import com.metao.ai.data.database.entities.CategorizationResultEntity
import com.metao.ai.data.database.entities.CategorizationSessionEntity
import com.metao.ai.data.database.entities.MoveOperationEntity

@Database(
    entities = [
        CategorizationResultEntity::class,
        CategorizationSessionEntity::class,
        MoveOperationEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(CategorizationResultConverters::class)
abstract class CategorizationDatabase : RoomDatabase() {
    abstract fun categorizationDao(): CategorizationDao

    companion object {
        @Volatile
        private var INSTANCE: CategorizationDatabase? = null

        fun getDatabase(context: Context): CategorizationDatabase =
            INSTANCE ?: synchronized(this) {
                val instance =
                    Room
                        .databaseBuilder(
                            context.applicationContext,
                            CategorizationDatabase::class.java,
                            "categorization_database",
                        ).fallbackToDestructiveMigration()
                        .build()
                INSTANCE = instance
                instance
            }
    }
}
