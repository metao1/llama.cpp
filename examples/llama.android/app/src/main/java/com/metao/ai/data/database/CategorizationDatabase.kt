package com.metao.ai.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import android.content.Context
import com.metao.ai.data.database.dao.CategorizationDao
import com.metao.ai.data.database.entities.CategorizationResultEntity
import com.metao.ai.data.database.entities.CategorizationSessionEntity
import com.metao.ai.data.database.entities.MoveOperationEntity
import com.metao.ai.data.database.entities.CategorizationResultConverters

@Database(
    entities = [
        CategorizationResultEntity::class,
        CategorizationSessionEntity::class,
        MoveOperationEntity::class
    ],
    version = 1,
    exportSchema = false
)
@TypeConverters(CategorizationResultConverters::class)
abstract class CategorizationDatabase : RoomDatabase() {
    
    abstract fun categorizationDao(): CategorizationDao
    
    companion object {
        @Volatile
        private var INSTANCE: CategorizationDatabase? = null
        
        fun getDatabase(context: Context): CategorizationDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CategorizationDatabase::class.java,
                    "categorization_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
