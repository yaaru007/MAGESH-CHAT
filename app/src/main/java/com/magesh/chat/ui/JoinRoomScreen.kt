package com.magesh.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Casino
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.random.Random

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JoinRoomScreen(
    savedName: String,
    recentRooms: List<String>,
    onJoin: (name: String, roomCode: String) -> Unit
) {
    var displayName by remember(savedName) { mutableStateOf(savedName) }
    var roomCode by remember { mutableStateOf(recentRooms.firstOrNull() ?: "") }

    val gradient = Brush.linearGradient(
        colors = listOf(
            Color(0xFF833AB4),
            Color(0xFFE1306C),
            Color(0xFFF77737)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(gradient),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Forum,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp)
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "MAGESH CHAT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Join any room with a secret code — no phone number needed",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                OutlinedTextField(
                    value = displayName,
                    onValueChange = { displayName = it },
                    label = { Text("Your Name") },
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = roomCode,
                    onValueChange = { roomCode = it.uppercase() },
                    label = { Text("Room Code (e.g. MAGESH-777)") },
                    leadingIcon = { Icon(Icons.Rounded.Key, null) },
                    trailingIcon = {
                        IconButton(onClick = {
                            val num = Random.nextInt(100, 999)
                            roomCode = "ROOM-$num"
                        }) {
                            Icon(Icons.Rounded.Casino, contentDescription = "Random Code")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = { onJoin(displayName.ifBlank { "Friend" }, roomCode) },
                    enabled = roomCode.trim().length >= 3,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                ) {
                    Text("Join / Create Room", style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (recentRooms.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Text(
                text = "Recent Rooms",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                recentRooms.forEach { code ->
                    AssistChip(
                        onClick = { roomCode = code },
                        label = { Text(code) }
                    )
                }
            }
        }
    }
}
