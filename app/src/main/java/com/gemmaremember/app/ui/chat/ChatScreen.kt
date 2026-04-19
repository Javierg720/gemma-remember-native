package com.gemmaremember.app.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.gemmaremember.app.PermissionHelper
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gemmaremember.app.data.model.ChatMessage
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    when {
        !state.isModelReady && !state.setupComplete -> SetupScreen(viewModel, state)
        !state.setupComplete -> NameScreen(viewModel)
        state.isVoiceMode -> VoiceModeScreen(viewModel, state)
        else -> ChatContent(viewModel, state)
    }
}

@Composable
private fun SetupScreen(viewModel: ChatViewModel, state: ChatUiState) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF0F5FF)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            GemmaLogo(size = 120)
            Spacer(Modifier.height(24.dp))
            Text("Gemma Remember", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D3268))
            Spacer(Modifier.height(8.dp))
            Text("Helping you remember the people who love you", fontSize = 16.sp, color = Color(0xFF5088C3), lineHeight = 24.sp)
            Spacer(Modifier.height(40.dp))

            if (state.isDownloading) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                )
                Spacer(Modifier.height(8.dp))
                Text("${state.downloadProgress}%", fontSize = 14.sp, color = Color(0xFF5088C3))
            } else {
                Button(
                    onClick = { viewModel.downloadModel() },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A6DD4)),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("Download Gemma 4 (2.6 GB)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Text("One-time download. Works offline after.", fontSize = 13.sp, color = Color(0xFF8B8A99))
            }
        }
    }
}

@Composable
private fun NameScreen(viewModel: ChatViewModel) {
    var name by remember { mutableStateOf("") }

    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFFF0F5FF)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(40.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White,
                shadowElevation = 4.dp,
                modifier = Modifier.size(80.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = Color(0xFF4C9BFF), modifier = Modifier.size(40.dp))
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("What's your name?", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0D3268))
            Spacer(Modifier.height(8.dp))
            Text("I'll use this to make our conversations personal", fontSize = 15.sp, color = Color(0xFF5088C3))
            Spacer(Modifier.height(32.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("Enter your name") },
                shape = RoundedCornerShape(14.dp),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { if (name.isNotBlank()) viewModel.saveName(name.trim()) },
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A6DD4)),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                enabled = name.isNotBlank()
            ) {
                Text("Continue", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ChatContent(viewModel: ChatViewModel, state: ChatUiState) {
    val context = LocalContext.current
    var inputText by remember { mutableStateOf("") }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        pendingPhoto = uri
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        if (bitmap != null) {
            viewModel.sendMessage(inputText.ifBlank { "" }, bitmap)
            inputText = ""
        }
    }

    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraLauncher.launch(null)
    }

    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.toggleVoiceMode()
    }

    val locationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        if (perms.values.any { it }) viewModel.shareLocation(context)
    }

    // Auto-scroll to bottom
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(Color(0xFFE4EFFE))) {
        // Header
        Surface(shadowElevation = 2.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GemmaLogo(size = 36)
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Gemma", fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = Color(0xFF0D3268))
                    Text(
                        if (state.peopleCount > 0) "Remembering ${state.peopleCount} people"
                        else "Your memory companion",
                        fontSize = 12.sp, color = Color(0xFF5088C3)
                    )
                }
                // Voice mode button
                IconButton(onClick = {
                    if (PermissionHelper.hasMic(context)) viewModel.toggleVoiceMode()
                    else micPermLauncher.launch(PermissionHelper.RECORD_AUDIO)
                }) {
                    Icon(Icons.Filled.Mic, contentDescription = "Voice mode", tint = Color(0xFF1A6DD4))
                }
            }
        }

        // Messages
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(state.messages, key = { it.id }) { message ->
                MessageBubble(message)
            }

            if (state.isLoading) {
                item { TypingIndicator() }
            }
        }

        // Photo preview
        if (pendingPhoto != null) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = pendingPhoto,
                    contentDescription = "Selected photo",
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { pendingPhoto = null }) {
                    Icon(Icons.Filled.Close, contentDescription = "Remove photo", tint = Color(0xFFE53935))
                }
            }
        }

        // Input bar
        Surface(
            shadowElevation = 8.dp,
            color = Color.White
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Camera
                IconButton(onClick = {
                    if (PermissionHelper.hasCamera(context)) cameraLauncher.launch(null)
                    else cameraPermLauncher.launch(PermissionHelper.CAMERA)
                }) {
                    Icon(Icons.Outlined.CameraAlt, contentDescription = "Camera", tint = Color(0xFF666666))
                }
                // Gallery
                IconButton(onClick = { photoLauncher.launch("image/*") }) {
                    Icon(Icons.Outlined.Image, contentDescription = "Gallery", tint = Color(0xFF666666))
                }
                // Location
                IconButton(onClick = {
                    if (PermissionHelper.hasLocation(context)) viewModel.shareLocation(context)
                    else locationPermLauncher.launch(arrayOf(PermissionHelper.FINE_LOCATION, PermissionHelper.COARSE_LOCATION))
                }) {
                    Icon(Icons.Outlined.LocationOn, contentDescription = "Location", tint = Color(0xFF666666))
                }
                // Text input
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Tell me anything...", fontSize = 15.sp) },
                    shape = RoundedCornerShape(24.dp),
                    singleLine = true,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color(0xFF1A6DD4),
                        unfocusedBorderColor = Color(0xFFE4EAF2)
                    )
                )
                Spacer(Modifier.width(4.dp))
                // Send
                FilledIconButton(
                    onClick = {
                        if (inputText.isNotBlank() || pendingPhoto != null) {
                            if (pendingPhoto != null) {
                                val bitmap = context.contentResolver.openInputStream(pendingPhoto!!)?.use {
                                    BitmapFactory.decodeStream(it)
                                }
                                viewModel.sendMessage(inputText, bitmap)
                                pendingPhoto = null
                            } else {
                                viewModel.sendMessage(inputText)
                            }
                            inputText = ""
                        }
                    },
                    shape = CircleShape,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF1A6DD4))
                ) {
                    Icon(Icons.Filled.Send, contentDescription = "Send", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val isUser = message.role == "user"

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Photo if present
        if (message.photoPath != null) {
            AsyncImage(
                model = File(message.photoPath),
                contentDescription = "Photo",
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .clip(RoundedCornerShape(16.dp)),
                contentScale = ContentScale.FillWidth
            )
            if (message.text.isNotBlank()) Spacer(Modifier.height(4.dp))
        }

        if (message.text.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 18.dp, topEnd = 18.dp,
                    bottomStart = if (isUser) 18.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 18.dp
                ),
                color = if (isUser) Color(0xFF1A6DD4) else Color.White,
                shadowElevation = if (isUser) 0.dp else 2.dp
            ) {
                Text(
                    text = message.text,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = if (isUser) Color.White else Color(0xFF1A2940),
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Surface(
        shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp),
        color = Color.White,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            repeat(3) { i ->
                val alpha by rememberInfiniteTransition(label = "dot$i").animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = i * 200),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot$i"
                )
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFC0BFCC).copy(alpha = alpha))
                )
            }
        }
    }
}

@Composable
fun GemmaLogo(size: Int) {
    // Simple Gemma star shape
    androidx.compose.foundation.Canvas(modifier = Modifier.size(size.dp)) {
        val cx = this.size.width / 2
        val cy = this.size.height / 2
        val r = this.size.minDimension / 2 * 0.85f

        // Draw the 4-point star
        val path = androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, cy - r)           // top
            quadraticBezierTo(cx + r * 0.15f, cy - r * 0.15f, cx + r, cy) // top to right
            quadraticBezierTo(cx + r * 0.15f, cy + r * 0.15f, cx, cy + r) // right to bottom
            quadraticBezierTo(cx - r * 0.15f, cy + r * 0.15f, cx - r, cy) // bottom to left
            quadraticBezierTo(cx - r * 0.15f, cy - r * 0.15f, cx, cy - r) // left to top
            close()
        }

        drawPath(path, color = Color(0xFF4285F4))
    }
}

@Composable
private fun VoiceModeScreen(viewModel: ChatViewModel, state: ChatUiState) {
    val amplitude by viewModel.speech.amplitude.collectAsStateWithLifecycle()
    val partialText by viewModel.speech.partialText.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(32.dp)
        ) {
            Spacer(Modifier.weight(1f))

            // Gemma star visualizer with real audio amplitude
            GemmaStarVisualizer(
                isSpeaking = state.isSpeaking,
                isListening = state.isListening,
                audioAmplitude = amplitude,
                modifier = Modifier.size(280.dp)
            )

            Spacer(Modifier.height(32.dp))

            // Partial speech text
            if (partialText.isNotBlank()) {
                Text(
                    text = partialText,
                    color = Color.White.copy(alpha = 0.9f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                when {
                    state.isLoading -> "Thinking..."
                    state.isSpeaking -> "Speaking..."
                    state.isListening -> "Listening..."
                    else -> "Tap to talk"
                },
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 14.sp
            )

            Spacer(Modifier.weight(1f))

            // Close button
            FilledIconButton(
                onClick = { viewModel.toggleVoiceMode() },
                shape = CircleShape,
                modifier = Modifier.size(64.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color(0xFF222222))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(28.dp))
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
