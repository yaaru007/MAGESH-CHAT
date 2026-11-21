package com.magesh.chat.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [ChatMessageEntity::class],
    version = 2,
    exportSchema = true
)
abstract class ChatDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
}
