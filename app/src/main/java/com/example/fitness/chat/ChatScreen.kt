package com.example.fitness.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.example.fitness.BuildConfig
import com.example.fitness.R
import com.example.fitness.network.GeminiClient
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import kotlinx.coroutines.launch
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.delay

@Composable
fun ChatScreen(onBack: () -> Unit = {}) {
    val messages = remember { mutableStateListOf<Pair<String, String>>() }
    var input by remember { mutableStateOf(TextFieldValue("")) }
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var apiKeyMissing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // simple check for API key availability
    LaunchedEffect(Unit) {
        apiKeyMissing = BuildConfig.GEMINI_API_KEY.isBlank()
        if (!apiKeyMissing) {
            delay(120)
            focusRequester.requestFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
            .imePadding()
            .padding(16.dp)
    ) {
        // Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(cornerRadius = 20.dp)
                .padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back),
                        tint = TechColors.NeonBlue
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = stringResource(R.string.training_assistant),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TechColors.NeonBlue
                    )
                    Text(
                        text = stringResource(R.string.ai_fitness_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.7f)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Chat messages area with glass effect
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .glassEffect(cornerRadius = 20.dp)
                .padding(12.dp)
        ) {
            if (messages.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.no_chat_yet),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { (sender, text) ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (sender == "user") Arrangement.End else Arrangement.Start
                        ) {
                            val bg = if (sender == "user")
                                TechColors.NeonBlue.copy(alpha = 0.3f)
                            else
                                Color.White.copy(alpha = 0.1f)
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 280.dp)
                                    .background(bg, shape = RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = text,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (apiKeyMissing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassEffect(cornerRadius = 12.dp)
                    .padding(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.api_key_missing),
                    color = Color(0xFFFF6B6B)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Input area with glass effect
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(cornerRadius = 16.dp)
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // helper to send messages from button or keyboard
                fun sendMessage() {
                    val text = input.text.trim()
                    if (text.isEmpty() || loading || apiKeyMissing) return
                    messages.add("user" to text)
                    input = TextFieldValue("")
                    loading = true
                    scope.launch {
                        try {
                            val res = GeminiClient.ask(text)
                            if (res.isSuccess) {
                                val display = res.getOrNull() ?: "(無回應)"
                                messages.add("gemini" to display)
                            } else {
                                val msg = res.exceptionOrNull()?.message ?: "Unknown error"
                                messages.add("gemini" to "錯誤: $msg")
                            }
                        } catch (e: Exception) {
                            val msg = e.message ?: e.toString()
                            messages.add("gemini" to "錯誤: $msg")
                        } finally {
                            loading = false
                        }
                    }
                }

                OutlinedTextField(
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    value = input,
                    onValueChange = { input = it },
                    enabled = !loading && !apiKeyMissing,
                    placeholder = { Text(stringResource(R.string.input_message), color = Color.White.copy(alpha = 0.5f)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = {
                        sendMessage()
                        keyboardController?.hide()
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = TechColors.NeonBlue,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = TechColors.NeonBlue
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { sendMessage() },
                    enabled = !loading && !apiKeyMissing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechColors.NeonBlue.copy(alpha = 0.2f),
                        contentColor = TechColors.NeonBlue
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TechColors.NeonBlue
                        )
                    } else {
                        Text(stringResource(R.string.send))
                    }
                }
            }
        }
    }
}
