package com.example.fitness.ui

import android.util.Patterns
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.auth.RememberMeRepository
import com.example.fitness.coach.UserRole
import com.example.fitness.firebase.AuthRepository
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.user.UserRoleProfileRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignInScreen(
    modifier: Modifier = Modifier,
    authRepository: AuthRepository = AuthRepository(),
    onSignedIn: () -> Unit,
    onGoogleSignIn: (UserRole?) -> Unit = {},
    onFacebookSignIn: (UserRole?) -> Unit = {},
    onPhoneSignIn: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var showSignUp by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    val ctx = LocalContext.current
    val appCtx = remember(ctx) { ctx.applicationContext }
    val rememberMeRepo = remember(appCtx) { RememberMeRepository(appCtx) }

    val roleRepo = remember { UserRoleProfileRepository() }

    var rememberMeEnabled by rememberSaveable { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        rememberMeEnabled = rememberMeRepo.isRememberMeEnabled()
    }

    var showForgotPassword by rememberSaveable { mutableStateOf(false) }
    var resetEmail by rememberSaveable { mutableStateOf("") }
    var sendingReset by rememberSaveable { mutableStateOf(false) }

    val cs = MaterialTheme.colorScheme

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TechColors.DarkBlue, TechColors.DeepPurple)
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .widthIn(max = 480.dp)
                .glassEffect(cornerRadius = 24.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = cs.primary,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Welcome Back",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = cs.onBackground
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Sign in to access your fitness journey",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onBackground.copy(alpha = 0.75f)
            )

            Spacer(Modifier.height(28.dp))

            var email by rememberSaveable { mutableStateOf("") }
            var password by rememberSaveable { mutableStateOf("") }
            var passwordVisible by rememberSaveable { mutableStateOf(false) }
            var loading by rememberSaveable { mutableStateOf(false) }

            NeonSignInTextField(
                value = email,
                onValueChange = { email = it },
                label = "Email",
                icon = { Icon(Icons.Default.Email, contentDescription = null, tint = cs.primary) }
            )

            Spacer(Modifier.height(16.dp))

            NeonSignInTextField(
                value = password,
                onValueChange = { password = it },
                label = "Password",
                icon = { Icon(Icons.Default.Lock, contentDescription = null, tint = cs.primary) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passwordVisible) "Hide password" else "Show password",
                            tint = cs.primary.copy(alpha = 0.7f)
                        )
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = {
                        resetEmail = email.trim()
                        showForgotPassword = true
                    },
                    enabled = !loading && !sendingReset
                ) {
                    Text("Forgot password?", color = cs.primary.copy(alpha = 0.8f))
                }
            }

            Spacer(Modifier.height(4.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        val newValue = !rememberMeEnabled
                        rememberMeEnabled = newValue
                        scope.launch { rememberMeRepo.setRememberMeEnabled(newValue) }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMeEnabled,
                    onCheckedChange = { checked ->
                        rememberMeEnabled = checked
                        scope.launch { rememberMeRepo.setRememberMeEnabled(checked) }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = cs.primary,
                        uncheckedColor = cs.onBackground.copy(alpha = 0.6f),
                        checkmarkColor = cs.onPrimary
                    )
                )
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Remember me",
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onBackground
                    )
                    Text(
                        text = "保持登入狀態（30 天未活躍自動登出）",
                        style = MaterialTheme.typography.labelSmall,
                        color = cs.onBackground.copy(alpha = 0.5f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        val result = authRepository.signInWithEmail(email.trim(), password)

                        if (result.isSuccess && result.getOrNull() != null) {
                            roleRepo.getMyRole()
                            if (!rememberMeEnabled) {
                                authRepository.signOut()
                            }
                            loading = false
                            onSignedIn()
                        } else {
                            loading = false
                            snackbarHostState.showSnackbar(result.exceptionOrNull()?.localizedMessage ?: "Sign in failed")
                        }
                    }
                },
                enabled = !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                    disabledContainerColor = cs.primary.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(14.dp)
            ) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = cs.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Signing In...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = cs.onPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign In", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = cs.onBackground.copy(alpha = 0.2f))
                Text(
                    text = "OR CONTINUE WITH",
                    style = MaterialTheme.typography.labelSmall,
                    color = cs.onBackground.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = cs.onBackground.copy(alpha = 0.2f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Google Button
                NeonSocialButton(
                    text = "Google",
                    icon = null,
                    onClick = { onGoogleSignIn(null) },
                    modifier = Modifier.weight(1f)
                )

                // Facebook Button
                NeonSocialButton(
                    text = "Facebook",
                    icon = null,
                    onClick = { onFacebookSignIn(null) },
                    modifier = Modifier.weight(1f)
                )

                // Phone Button
                NeonSocialButton(
                    text = "Phone",
                    icon = Icons.Default.Smartphone,
                    onClick = onPhoneSignIn,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(24.dp))

            OutlinedButton(
                onClick = { showSignUp = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                border = BorderStroke(
                    1.dp,
                    Brush.linearGradient(
                        listOf(
                            cs.primary.copy(alpha = 0.5f),
                            cs.primary.copy(alpha = 0.1f)
                        )
                    )
                ),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = cs.onBackground),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = cs.primary)
                Spacer(Modifier.width(8.dp))
                Text("Create Account")
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) { data ->
            Snackbar(
                containerColor = cs.primary,
                contentColor = cs.onPrimary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(data.visuals.message, fontWeight = FontWeight.Bold)
            }
        }

        if (showForgotPassword) {
            AlertDialog(
                onDismissRequest = { if (!sendingReset) showForgotPassword = false },
                containerColor = cs.surface,
                titleContentColor = cs.primary,
                textContentColor = cs.onBackground,
                title = { Text("Reset password", fontWeight = FontWeight.Bold, color = cs.onBackground) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Enter your email. We'll send you a password reset link.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onBackground.copy(alpha = 0.8f)
                        )
                        NeonSignInTextField(
                            value = resetEmail,
                            onValueChange = { resetEmail = it },
                            label = "Email",
                            icon = { Icon(Icons.Default.Email, contentDescription = null, tint = cs.primary) },
                            enabled = !sendingReset
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = !sendingReset,
                        onClick = {
                            val emailToSend = resetEmail.trim()
                            if (emailToSend.isBlank() || !Patterns.EMAIL_ADDRESS.matcher(emailToSend).matches()) {
                                scope.launch { snackbarHostState.showSnackbar("Please enter a valid email") }
                                return@TextButton
                            }
                            sendingReset = true
                            scope.launch {
                                val result = authRepository.sendPasswordResetEmail(emailToSend)
                                sendingReset = false
                                if (result.isSuccess) {
                                    showForgotPassword = false
                                    snackbarHostState.showSnackbar("Reset email sent")
                                } else {
                                    snackbarHostState.showSnackbar(
                                        result.exceptionOrNull()?.localizedMessage ?: "Failed to send reset email"
                                    )
                                }
                            }
                        }
                    ) {
                        if (sendingReset) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = cs.primary)
                        } else {
                            Text("Send", color = cs.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                dismissButton = {
                    TextButton(
                        enabled = !sendingReset,
                        onClick = { showForgotPassword = false }
                    ) {
                        Text("Cancel", color = cs.onBackground.copy(alpha = 0.7f))
                    }
                }
            )
        }

        if (showSignUp) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f))
                    .clickable { showSignUp = false },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .wrapContentHeight()
                        .clickable(enabled = false) {}
                ) {
                    SignUpScreen(
                        authRepository = authRepository,
                        onSignedUp = {},
                        onNavigateToSignIn = { showSignUp = false },
                        modifier = Modifier.fillMaxWidth(),
                        // 雖然 SignUpScreen 可能回傳 role，但我們在此直接轉發 onGoogleSignIn(role)
                        // 如果你也希望 SignUpScreen 內的 Google 也不要選角色，那就需要去修改 SignUpScreen
                        onGoogleSignUp = { role -> onGoogleSignIn(role) },
                        onFacebookSignUp = { role -> onFacebookSignIn(role) },
                        onPhoneSignUp = { onPhoneSignIn() }
                    )
                }
            }
        }
    }
}

// --- Local Components ---

@Composable
private fun NeonSocialButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(50.dp)
            .border(1.dp, cs.outline.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        color = cs.surfaceVariant.copy(alpha = 0.3f)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = cs.onBackground,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            } else if (text == "Google") {
                Text(
                    text = "G",
                    color = cs.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            } else if (text == "Facebook") {
                Text(
                    text = "f",
                    color = Color(0xFF1877F2),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 22.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
            }

            Text(
                text = text,
                color = cs.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun NeonSignInTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        label = { Text(label, fontWeight = FontWeight.Medium) },
        leadingIcon = icon,
        trailingIcon = trailingIcon,
        visualTransformation = visualTransformation,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = cs.onBackground,
            unfocusedTextColor = cs.onBackground,
            focusedBorderColor = cs.primary,
            unfocusedBorderColor = cs.outline.copy(alpha = 0.6f),
            focusedLabelColor = cs.primary,
            unfocusedLabelColor = cs.onBackground.copy(alpha = 0.75f),
            cursorColor = cs.primary,
            focusedContainerColor = cs.surfaceVariant.copy(alpha = 0.25f),
            unfocusedContainerColor = cs.surfaceVariant.copy(alpha = 0.12f),
            disabledBorderColor = cs.outline.copy(alpha = 0.3f),
            disabledTextColor = cs.onBackground.copy(alpha = 0.5f)
        )
    )
}