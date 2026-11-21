package com.magesh.chat.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.chatPrefsStore: DataStore<Preferences> by preferencesDataStore(
    name = "magesh_chat_prefs"
)

data class UserProfile(
    val senderId: String = "",
    val displayName: String = "",
    val activeRoomCode: String = "",
    val recentRooms: List<String> = emptyList(),
    val darkMode: Boolean = true
)

class UserPreferences(private val context: Context) {
    private object Keys {
        val SENDER_ID = stringPreferencesKey("sender_id")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val ACTIVE_ROOM = stringPreferencesKey("active_room")
        val RECENT_ROOMS = stringPreferencesKey("recent_rooms")
        val DARK_MODE = booleanPreferencesKey("dark_mode")
    }

    val profileFlow: Flow<UserProfile> = context.chatPrefsStore.data.map { prefs ->
        val rawRecent = prefs[Keys.RECENT_ROOMS].orEmpty()
        UserProfile(
            senderId = prefs[Keys.SENDER_ID] ?: "",
            displayName = prefs[Keys.DISPLAY_NAME] ?: "",
            activeRoomCode = prefs[Keys.ACTIVE_ROOM] ?: "",
            recentRooms = if (rawRecent.isBlank()) emptyList() else rawRecent.split("\n"),
            darkMode = prefs[Keys.DARK_MODE] ?: true
        )
    }

    suspend fun ensureSenderId(): String {
        var current = ""
        context.chatPrefsStore.edit { prefs ->
            val existing = prefs[Keys.SENDER_ID]
            if (existing.isNullOrBlank()) {
                val created = UUID.randomUUID().toString()
                prefs[Keys.SENDER_ID] = created
                current = created
            } else {
                current = existing
            }
        }
        return current
    }

    suspend fun joinRoom(displayName: String, roomCode: String) {
        val cleanCode = roomCode.trim().uppercase()
        val cleanName = displayName.trim()
        context.chatPrefsStore.edit { prefs ->
            if (prefs[Keys.SENDER_ID].isNullOrBlank()) {
                prefs[Keys.SENDER_ID] = UUID.randomUUID().toString()
            }
            prefs[Keys.DISPLAY_NAME] = cleanName
            prefs[Keys.ACTIVE_ROOM] = cleanCode

            val existing = prefs[Keys.RECENT_ROOMS].orEmpty()
                .split("\n")
                .filter { it.isNotBlank() && it != cleanCode }
            val updated = (listOf(cleanCode) + existing).take(6)
            prefs[Keys.RECENT_ROOMS] = updated.joinToString("\n")
        }
    }

    suspend fun leaveRoom() {
        context.chatPrefsStore.edit { prefs ->
            prefs[Keys.ACTIVE_ROOM] = ""
        }
    }

    suspend fun setDarkMode(enabled: Boolean) {
        context.chatPrefsStore.edit { it[Keys.DARK_MODE] = enabled }
    }
}
