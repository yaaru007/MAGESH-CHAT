package com.magesh.chat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

enum class MessageType {
    TEXT,
    AUDIO
}

@Entity(
    tableName = "chat_messages",
    indices = [
        Index(value = ["remoteId"], unique = true),
        Index(value = ["roomCode", "timestampMs"])
    ]
)
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val remoteId: String,
    val roomCode: String,
    val senderId: String,
    val senderName: String,
    val type: MessageType,
    val text: String = "",
    val audioUrl: String? = null,
    val localAudioPath: String? = null,
    val audioDurationMs: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis()
)
