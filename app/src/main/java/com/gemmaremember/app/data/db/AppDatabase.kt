package com.gemmaremember.app.data.db

import android.content.Context
import androidx.room.*
import com.gemmaremember.app.data.model.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM people ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<Person>>

    @Query("SELECT * FROM people WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' OR LOWER(relationship) LIKE '%' || LOWER(:query) || '%'")
    suspend fun search(query: String): List<Person>

    @Query("SELECT * FROM people WHERE id = :id")
    suspend fun getById(id: String): Person?

    @Upsert
    suspend fun upsert(person: Person)

    @Delete
    suspend fun delete(person: Person)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memories WHERE personId = :personId ORDER BY timestamp DESC")
    suspend fun getForPerson(personId: String): List<Memory>

    @Query("SELECT * FROM memories ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 50): List<Memory>

    @Query("SELECT * FROM memories WHERE LOWER(content) LIKE '%' || LOWER(:query) || '%'")
    suspend fun search(query: String): List<Memory>

    @Insert
    suspend fun insert(memory: Memory)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY time ASC")
    fun getAll(): Flow<List<Reminder>>

    @Query("SELECT * FROM reminders WHERE time > :now ORDER BY time ASC")
    suspend fun getUpcoming(now: Long = System.currentTimeMillis()): List<Reminder>

    @Insert
    suspend fun insert(reminder: Reminder)

    @Delete
    suspend fun delete(reminder: Reminder)
}

@Dao
interface ChatDao {
    @Query("SELECT * FROM chat_messages ORDER BY timestamp ASC")
    fun getAll(): Flow<List<ChatMessage>>

    @Query("SELECT * FROM chat_messages ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecent(limit: Int = 20): List<ChatMessage>

    @Insert
    suspend fun insert(message: ChatMessage)
}

@Database(
    entities = [Person::class, Memory::class, Reminder::class, ChatMessage::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun personDao(): PersonDao
    abstract fun memoryDao(): MemoryDao
    abstract fun reminderDao(): ReminderDao
    abstract fun chatDao(): ChatDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(context, AppDatabase::class.java, "gemma_remember.db")
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
