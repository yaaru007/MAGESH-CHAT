package com.magesh.chat.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages WHERE roomCode = :roomCode ORDER BY timestampMs ASC")
    fun observeMessages(roomCode: String): Flow<List<ChatMessageEntity>>

    @Query("SELECT * FROM chat_messages WHERE remoteId = :remoteId LIMIT 1")
    suspend fun getByRemoteId(remoteId: String): ChatMessageEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET localAudioPath = :path WHERE remoteId = :remoteId")
    suspend fun updateLocalAudioPath(remoteId: String, path: String)

    @Query("DELETE FROM chat_messages WHERE roomCode = :roomCode")
    suspend fun clearRoom(roomCode: String)
}
