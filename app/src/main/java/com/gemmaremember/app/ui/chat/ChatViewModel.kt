package com.gemmaremember.app.ui.chat

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gemmaremember.app.ai.GemmaEngine
import com.gemmaremember.app.data.db.AppDatabase
import com.gemmaremember.app.data.model.*
import com.gemmaremember.app.voice.VoiceManager
import com.gemmaremember.app.voice.SpeechListener
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val isModelReady: Boolean = false,
    val isDownloading: Boolean = false,
    val downloadProgress: Int = 0,
    val patientName: String = "",
    val setupComplete: Boolean = false,
    val isVoiceMode: Boolean = false,
    val isSpeaking: Boolean = false,
    val isListening: Boolean = false,
    val peopleCount: Int = 0
)

class ChatViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.get(application)
    private val gemma = GemmaEngine(application)
    val voice = VoiceManager(application)
    val speech = SpeechListener(application)

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private val prefs = application.getSharedPreferences("gemma_prefs", 0)

    init {
        val name = prefs.getString("patientName", null)
        _uiState.update { it.copy(
            patientName = name ?: "",
            setupComplete = name != null
        ) }

        // Load existing messages
        viewModelScope.launch {
            db.chatDao().getAll().collect { messages ->
                _uiState.update { it.copy(messages = messages) }
            }
        }

        // Track people count
        viewModelScope.launch {
            db.personDao().getAll().collect { people ->
                _uiState.update { it.copy(peopleCount = people.size) }
            }
        }

        // Check model status
        viewModelScope.launch {
            _uiState.update { it.copy(isModelReady = gemma.isModelDownloaded()) }
            if (gemma.isModelDownloaded()) {
                try {
                    gemma.initialize()
                    _uiState.update { it.copy(isModelReady = true) }
                } catch (e: Exception) {
                    // Model file corrupted — will need re-download
                    _uiState.update { it.copy(isModelReady = false) }
                }
            }
        }
    }

    fun saveName(name: String) {
        prefs.edit().putString("patientName", name).apply()
        _uiState.update { it.copy(patientName = name, setupComplete = true) }

        // Send welcome message
        viewModelScope.launch {
            val welcome = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "gemma",
                text = "Hi $name! I'm Gemma, your memory companion. Tell me about the people in your life — their names, who they are to you, anything you want to remember. You can also send me photos or just talk to me. I'll remember everything for you."
            )
            db.chatDao().insert(welcome)
        }
    }

    fun downloadModel() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDownloading = true, downloadProgress = 0) }
            try {
                gemma.downloadModel { progress ->
                    _uiState.update { it.copy(downloadProgress = progress) }
                }
                gemma.initialize()
                _uiState.update { it.copy(isModelReady = true, isDownloading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isDownloading = false) }
            }
        }
    }

    fun sendMessage(text: String, photoBitmap: Bitmap? = null) {
        viewModelScope.launch {
            // Save photo if present
            var photoPath: String? = null
            if (photoBitmap != null) {
                photoPath = savePhoto(photoBitmap)
            }

            // Insert user message
            val userMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                role = "user",
                text = text,
                photoPath = photoPath
            )
            db.chatDao().insert(userMsg)

            _uiState.update { it.copy(isLoading = true) }

            try {
                val response = generateResponse(text, photoPath)

                // Insert Gemma response
                val gemmaMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "gemma",
                    text = response
                )
                db.chatDao().insert(gemmaMsg)

                // Speak response
                voice.speak(response)
                _uiState.update { it.copy(isSpeaking = true) }
            } catch (e: Exception) {
                val errorMsg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "gemma",
                    text = "I'm having a bit of trouble right now. Can you try again?"
                )
                db.chatDao().insert(errorMsg)
            } finally {
                _uiState.update { it.copy(isLoading = false) }
            }
        }
    }

    private suspend fun generateResponse(userText: String, photoPath: String?): String =
        withContext(Dispatchers.Default) {
            val name = _uiState.value.patientName
            val people = db.personDao().search("").ifEmpty {
                // Get all if search returns empty
                db.personDao().search("a") + db.personDao().search("e")
            }.distinctBy { it.id }

            val recentMemories = db.memoryDao().getRecent(20)
            val reminders = db.reminderDao().getUpcoming()
            val recentChat = db.chatDao().getRecent(10).reversed()

            val peopleCtx = if (people.isEmpty()) "No people saved yet."
            else people.joinToString("\n") { p ->
                "- ${p.name} (${p.relationship}): ${p.story}"
            }

            val memoriesCtx = if (recentMemories.isEmpty()) ""
            else "\nRECENT MEMORIES:\n" + recentMemories.joinToString("\n") { "- [${it.type}] ${it.content}" }

            val remindersCtx = if (reminders.isEmpty()) ""
            else "\nREMINDERS:\n" + reminders.joinToString("\n") { "- ${it.message} (${it.type})" }

            val chatCtx = recentChat.joinToString("\n") { msg ->
                "${if (msg.role == "user") name else "GEMMA"}: ${msg.text}"
            }

            val prompt = """You are Gemma, $name's memory companion. You are warm, brief, and real.

PEOPLE IN MEMORY:
$peopleCtx
$memoriesCtx
$remindersCtx

RECENT CHAT:
$chatCtx

$name: ${userText.ifBlank { "(sent a photo)" }}
${if (photoPath != null) "[Photo attached]" else ""}

INSTRUCTIONS:
- If $name introduces someone (e.g. "this is my daughter Sarah"), respond warmly and end with: [SAVE:name:relationship:details]
- If $name adds info about someone known, respond and end with: [UPDATE:name:new info]
- If $name mentions a reminder, confirm and end with: [REMIND:text:type]
- Otherwise respond naturally. 1-3 sentences max.
- ONLY use facts from memory or what $name just said. Never invent.
- Be warm, real, conversational. No robotic language.

GEMMA:"""

            val rawResponse = gemma.generate(prompt)

            // Parse and execute action tags
            var cleanResponse = rawResponse

            val saveMatch = Regex("""\[SAVE:([^:]*):([^:]*):([^\]]*)\]""").find(rawResponse)
            if (saveMatch != null) {
                val (pName, pRel, pStory) = saveMatch.destructured
                val personId = UUID.randomUUID().toString()
                db.personDao().upsert(Person(
                    id = personId,
                    name = pName.trim(),
                    relationship = pRel.trim(),
                    story = pStory.trim(),
                    photoPath = photoPath
                ))
                db.memoryDao().insert(Memory(
                    id = UUID.randomUUID().toString(),
                    type = "face",
                    content = "$name told me about ${pName.trim()} (${pRel.trim()}): ${pStory.trim()}",
                    personId = personId
                ))
                cleanResponse = rawResponse.replace(saveMatch.value, "").trim()
            }

            val updateMatch = Regex("""\[UPDATE:([^:]*):([^\]]*)\]""").find(rawResponse)
            if (updateMatch != null) {
                val (pName, newInfo) = updateMatch.destructured
                val person = db.personDao().search(pName.trim()).firstOrNull()
                if (person != null) {
                    db.personDao().upsert(person.copy(
                        story = "${person.story}. ${newInfo.trim()}",
                        lastSeen = System.currentTimeMillis()
                    ))
                    db.memoryDao().insert(Memory(
                        id = UUID.randomUUID().toString(),
                        type = "fact",
                        content = "New info about ${person.name}: ${newInfo.trim()}",
                        personId = person.id
                    ))
                }
                cleanResponse = rawResponse.replace(updateMatch.value, "").trim()
            }

            val remindMatch = Regex("""\[REMIND:([^:]*):([^\]]*)\]""").find(rawResponse)
            if (remindMatch != null) {
                val (rText, rType) = remindMatch.destructured
                db.reminderDao().insert(Reminder(
                    id = UUID.randomUUID().toString(),
                    time = System.currentTimeMillis() + 3600000, // default 1hr from now
                    message = rText.trim(),
                    type = rType.trim()
                ))
                cleanResponse = rawResponse.replace(remindMatch.value, "").trim()
            }

            cleanResponse.ifBlank { rawResponse }
        }

    private suspend fun savePhoto(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        val dir = File(getApplication<Application>().filesDir, "photos")
        dir.mkdirs()
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        file.absolutePath
    }

    fun toggleVoiceMode() {
        val entering = !_uiState.value.isVoiceMode
        _uiState.update { it.copy(isVoiceMode = entering) }
        if (entering) {
            startVoiceListening()
        } else {
            speech.stop()
        }
    }

    private fun startVoiceListening() {
        speech.onResult = { text ->
            // Auto-send voice input to Gemma
            sendMessage(text)
        }
        speech.onError = {
            _uiState.update { it.copy(isListening = false) }
        }
        speech.start(continuousMode = true)

        // Observe speech state
        viewModelScope.launch {
            speech.isListening.collect { listening ->
                _uiState.update { it.copy(isListening = listening) }
            }
        }
    }

    fun shareLocation(context: Context) {
        viewModelScope.launch {
            try {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
                @Suppress("MissingPermission")
                val location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)

                if (location != null) {
                    val lat = location.latitude
                    val lng = location.longitude
                    val mapsUrl = "https://www.google.com/maps?q=$lat,$lng"

                    // Insert user message
                    val userMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "user",
                        text = "I'm sharing my location"
                    )
                    db.chatDao().insert(userMsg)

                    _uiState.update { it.copy(isLoading = true) }

                    val prompt = """You are Gemma, a memory companion. The user just shared their GPS location: $lat, $lng.
Google Maps: $mapsUrl

If they seem lost or confused, be calm and reassuring. Share the maps link so a caregiver can find them. Suggest staying put and calling family. Keep it simple and warm. 2-3 sentences."""

                    val response = try {
                        gemma.generate(prompt)
                    } catch (e: Exception) {
                        "Here's your location so someone can find you: $mapsUrl. Stay right where you are — you're safe."
                    }

                    val gemmaMsg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "gemma",
                        text = "$response\n\n$mapsUrl"
                    )
                    db.chatDao().insert(gemmaMsg)
                    voice.speak(response)
                    _uiState.update { it.copy(isLoading = false) }
                } else {
                    val msg = ChatMessage(
                        id = UUID.randomUUID().toString(),
                        role = "gemma",
                        text = "I couldn't get your location right now. Make sure location services are turned on in your phone settings."
                    )
                    db.chatDao().insert(msg)
                }
            } catch (e: Exception) {
                val msg = ChatMessage(
                    id = UUID.randomUUID().toString(),
                    role = "gemma",
                    text = "I had trouble getting your location. Please try again."
                )
                db.chatDao().insert(msg)
            }
        }
    }

    override fun onCleared() {
        voice.shutdown()
        speech.stop()
        super.onCleared()
    }
}
