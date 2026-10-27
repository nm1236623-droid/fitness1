package com.example.fitness.ui.messaging

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitness.coach.messaging.*
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoachMessagingScreen(
    chatRoomId: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val repository = remember { CoachMessagingRepository() }
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    val currentUserName = FirebaseAuth.getInstance().currentUser?.displayName ?: "User"

    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var messageText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Image picker
    val imageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch {
                repository.sendImageMessage(chatRoomId, currentUserId, currentUserName, it)
            }
        }
    }

    // Load messages
    LaunchedEffect(chatRoomId) {
        repository.getMessages(chatRoomId).collect {
            messages = it
            if (it.isNotEmpty()) {
                listState.animateScrollToItem(it.size - 1)
            }
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("聊天室") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TechColors.DarkBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            ChatInputBar(
                text = messageText,
                onTextChange = { messageText = it },
                onSend = {
                    if (messageText.isNotBlank()) {
                        scope.launch {
                            repository.sendTextMessage(
                                chatRoomId,
                                currentUserId,
                                currentUserName,
                                messageText
                            )
                            messageText = ""
                        }
                    }
                },
                onAttachImage = { imageLauncher.launch("image/*") }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
                .padding(paddingValues)
        ) {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(messages) { message ->
                    MessageBubble(
                        message = message,
                        isCurrentUser = message.senderId == currentUserId
                    )
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    isCurrentUser: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isCurrentUser) {
            // Sender avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(TechColors.NeonBlue.copy(0.3f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    message.senderName.take(1).uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = TechColors.NeonBlue,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 280.dp),
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start
        ) {
            // Message content
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isCurrentUser) 16.dp else 4.dp,
                    bottomEnd = if (isCurrentUser) 4.dp else 16.dp
                ),
                color = if (isCurrentUser) TechColors.NeonBlue.copy(0.3f) else Color.White.copy(0.1f)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    if (!isCurrentUser) {
                        Text(
                            message.senderName,
                            style = MaterialTheme.typography.labelSmall,
                            color = TechColors.NeonBlue,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                    }

                    when (message.type) {
                        MessageType.TEXT -> {
                            Text(
                                message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        MessageType.IMAGE -> {
                            Text(
                                "[圖片]",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(0.7f)
                            )
                        }
                        MessageType.VOICE -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.Mic,
                                    null,
                                    tint = Color.White.copy(0.7f),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    message.content,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(0.7f)
                                )
                            }
                        }
                        else -> {
                            Text(
                                message.content,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(0.7f)
                            )
                        }
                    }
                }
            }

            // Timestamp
            Text(
                message.timestamp.atZone(ZoneId.systemDefault())
                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.5f),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }

        if (isCurrentUser) {
            Spacer(Modifier.width(8.dp))
            // Read status indicator
            Icon(
                if (message.read) Icons.Default.DoneAll else Icons.Default.Done,
                null,
                tint = if (message.read) TechColors.NeonBlue else Color.White.copy(0.5f),
                modifier = Modifier.size(16.dp).align(Alignment.Bottom)
            )
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachImage: () -> Unit
) {
    Surface(
        color = TechColors.DarkBlue.copy(0.95f),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onAttachImage) {
                Icon(
                    Icons.Default.Image,
                    null,
                    tint = TechColors.NeonBlue
                )
            }

            OutlinedTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                placeholder = { Text("輸入訊息...", color = Color.White.copy(0.5f)) },
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = TechColors.NeonBlue,
                    unfocusedBorderColor = Color.White.copy(0.3f),
                    cursorColor = TechColors.NeonBlue,
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent
                ),
                maxLines = 4
            )

            IconButton(
                onClick = onSend,
                enabled = text.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    null,
                    tint = if (text.isNotBlank()) TechColors.NeonBlue else Color.Gray
                )
            }
        }
    }
}

@Composable
fun ChatRoomListScreen(
    isCoach: Boolean,
    onChatRoomSelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val repository = remember { CoachMessagingRepository() }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: ""
    var chatRooms by remember { mutableStateOf<List<ChatRoom>>(emptyList()) }

    LaunchedEffect(Unit) {
        repository.getChatRooms(currentUserId, isCoach).collect {
            chatRooms = it
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("訊息") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TechColors.DarkBlue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
                .padding(paddingValues)
        ) {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(chatRooms) { chatRoom ->
                    ChatRoomCard(
                        chatRoom = chatRoom,
                        onClick = { onChatRoomSelected(chatRoom.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatRoomCard(
    chatRoom: ChatRoom,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(16.dp)
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TechColors.NeonBlue.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    chatRoom.traineeName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TechColors.NeonBlue
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    chatRoom.traineeName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                chatRoom.lastMessage?.let { lastMsg ->
                    Text(
                        lastMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.6f),
                        maxLines = 1
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                chatRoom.lastMessageTime?.let { time ->
                    Text(
                        time.atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm")),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(0.5f)
                    )
                }

                if (chatRoom.unreadCount > 0) {
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        shape = CircleShape,
                        color = TechColors.NeonBlue
                    ) {
                        Text(
                            "${chatRoom.unreadCount}",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
