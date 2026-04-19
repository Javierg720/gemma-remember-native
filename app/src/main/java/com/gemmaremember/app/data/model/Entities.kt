package com.gemmaremember.app.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "people")
data class Person(
    @PrimaryKey val id: String,
    val name: String,
    val relationship: String,
    val story: String = "",
    val photoPath: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

@Entity(tableName = "memories")
data class Memory(
    @PrimaryKey val id: String,
    val type: String,       // "medication", "routine", "story", "face", "fact"
    val content: String,
    val personId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val importance: Int = 3 // 1-5
)

@Entity(tableName = "reminders")
data class Reminder(
    @PrimaryKey val id: String,
    val time: Long,
    val message: String,
    val type: String = "other", // "meds", "meal", "activity", "appointment"
    val recurring: Boolean = false
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey val id: String,
    val role: String,       // "user" or "gemma"
    val text: String,
    val photoPath: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
