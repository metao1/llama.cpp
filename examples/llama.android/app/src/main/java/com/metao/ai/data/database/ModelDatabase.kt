package com.metao.ai.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import android.content.Context

@Database(
    entities = [ModelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class ModelDatabase : RoomDatabase() {
    
    abstract fun modelDao(): ModelDao
    
    companion object {
        @Volatile
        private var INSTANCE: ModelDatabase? = null
        
        fun getDatabase(context: Context): ModelDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ModelDatabase::class.java,
                    "model_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
