package com.example.fitness.ui.settings

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.firebase.AuthRepository
import com.example.fitness.localization.LocaleManager
import com.example.fitness.localization.SupportedLanguage
import com.example.fitness.ui.theme.ColorScheme
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.ThemeManager
import com.example.fitness.ui.theme.glassEffect
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdvancedSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val themeManager = remember { ThemeManager.getInstance(context) }
    val localeManager = remember { LocaleManager.getInstance(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    val currentColorScheme by themeManager.colorScheme.collectAsState(initial = ColorScheme.NEON_BLUE)
    val currentLanguage by localeManager.currentLanguage.collectAsState(initial = SupportedLanguage.CHINESE_TRADITIONAL)

    var showColorSchemeDialog by remember { mutableStateOf(false) }
    var showLanguageDialog by remember { mutableStateOf(false) }
    var showDeleteAccountDialog by remember { mutableStateOf(false) }
    var deletePassword by remember { mutableStateOf("") }
    var deleteLoading by remember { mutableStateOf(false) }
    val authRepository = remember { AuthRepository() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        val cs = MaterialTheme.colorScheme
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 680.dp)
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Header - 與 BodyPhotoScreen 一樣的風格
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
                                tint = cs.primary
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        Column {
                            Text(
                                stringResource(R.string.advanced_settings),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = cs.onBackground
                            )
                            Text(
                                stringResource(R.string.appearance),
                                style = MaterialTheme.typography.bodySmall,
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Theme Section
                SettingSection(stringResource(R.string.appearance)) {

                    SettingItem(
                        icon = Icons.Default.ColorLens,
                        title = stringResource(R.string.color_style),
                        subtitle = currentColorScheme.toLocalizedDisplayName(),
                        onClick = { showColorSchemeDialog = true }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Language Section
                SettingSection(stringResource(R.string.language_settings)) {
                    SettingItem(
                        icon = Icons.Default.Language,
                        title = stringResource(R.string.app_language),
                        subtitle = currentLanguage.displayName,
                        onClick = { showLanguageDialog = true }
                    )
                }

                Spacer(Modifier.height(16.dp))

                // Danger Zone - Delete Account
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .border(
                            width = 1.dp,
                            color = cs.error.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(20.dp)
                        )
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    cs.error.copy(alpha = 0.05f),
                                    cs.error.copy(alpha = 0.02f)
                                )
                            )
                        )
                        .padding(16.dp)
                ) {
                    Column {
                        Text(
                            stringResource(R.string.danger_zone),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = cs.error
                        )
                        Spacer(Modifier.height(16.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showDeleteAccountDialog = true }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    stringResource(R.string.delete_account),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = cs.error,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    stringResource(R.string.delete_account_description),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = cs.onSurfaceVariant
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = cs.error.copy(alpha = 0.6f)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))
            }

            // Dialogs

            if (showColorSchemeDialog) {
                ColorSchemeSelectionDialog(
                    currentScheme = currentColorScheme,
                    onSchemeSelected = { scheme ->
                        scope.launch {
                            themeManager.setColorScheme(scheme)
                            showColorSchemeDialog = false
                        }
                    },
                    onDismiss = { showColorSchemeDialog = false }
                )
            }

            if (showLanguageDialog) {
                LanguageSelectionDialog(
                    currentLanguage = currentLanguage,
                    onLanguageSelected = { language ->
                        scope.launch {
                            localeManager.setLanguageSync(language)
                            showLanguageDialog = false

                            val activity = context as? Activity
                            activity?.let {
                                val intent = it.intent
                                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                                it.finish()
                                it.startActivity(intent)
                            }
                        }
                    },
                    onDismiss = { showLanguageDialog = false }
                )
            }

            // Delete Account Confirmation Dialog
            if (showDeleteAccountDialog) {
                val isEmailUser = remember {
                    FirebaseAuth.getInstance().currentUser?.providerData
                        ?.any { it.providerId == "password" } == true
                }

                AlertDialog(
                    onDismissRequest = {
                        if (!deleteLoading) {
                            showDeleteAccountDialog = false
                            deletePassword = ""
                        }
                    },
                    containerColor = cs.surface,
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Default.Warning,
                                contentDescription = null,
                                tint = cs.error,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(R.string.delete_account_confirm_title),
                                fontWeight = FontWeight.Bold,
                                color = cs.error
                            )
                        }
                    },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(
                                stringResource(R.string.delete_account_warning),
                                style = MaterialTheme.typography.bodyMedium,
                                color = cs.onSurface
                            )

                            if (isEmailUser) {
                                OutlinedTextField(
                                    value = deletePassword,
                                    onValueChange = { deletePassword = it },
                                    label = { Text(stringResource(R.string.enter_password_to_confirm)) },
                                    singleLine = true,
                                    enabled = !deleteLoading,
                                    visualTransformation = PasswordVisualTransformation(),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = cs.error,
                                        unfocusedBorderColor = cs.error.copy(alpha = 0.4f),
                                        focusedLabelColor = cs.error,
                                        cursorColor = cs.error
                                    )
                                )
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (isEmailUser && deletePassword.isBlank()) {
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(R.string.password_required)
                                        )
                                    }
                                    return@Button
                                }

                                deleteLoading = true
                                scope.launch {
                                    val result = authRepository.deleteAccount(
                                        password = if (isEmailUser) deletePassword else null
                                    )

                                    if (result.isSuccess) {
                                        showDeleteAccountDialog = false
                                        deletePassword = ""

                                        // Navigate to MainActivity (login screen)
                                        val activity = context as? Activity
                                        activity?.let {
                                            val intent = Intent(
                                                it,
                                                Class.forName("com.example.fitness.MainActivity")
                                            ).apply {
                                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                                                        Intent.FLAG_ACTIVITY_CLEAR_TASK
                                            }
                                            it.startActivity(intent)
                                            it.finish()
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                R.string.delete_account_failed,
                                                result.exceptionOrNull()?.localizedMessage ?: "Unknown error"
                                            )
                                        )
                                    }
                                    deleteLoading = false
                                }
                            },
                            enabled = !deleteLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = cs.error,
                                contentColor = cs.onError,
                                disabledContainerColor = cs.error.copy(alpha = 0.4f),
                                disabledContentColor = cs.onError.copy(alpha = 0.5f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(
                                defaultElevation = 0.dp,
                                pressedElevation = 0.dp,
                                disabledElevation = 0.dp
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            if (deleteLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    color = cs.onError,
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.deleting_account))
                            } else {
                                Icon(
                                    Icons.Default.DeleteForever,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    stringResource(R.string.confirm_delete),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    },
                    dismissButton = {
                        TextButton(
                            onClick = {
                                showDeleteAccountDialog = false
                                deletePassword = ""
                            },
                            enabled = !deleteLoading
                        ) {
                            Text(
                                stringResource(R.string.cancel),
                                color = cs.onSurfaceVariant
                            )
                        }
                    }
                )
            }
        }
    }
}


@Composable
private fun ColorScheme.toLocalizedDisplayName(): String = when (this) {
    ColorScheme.NEON_BLUE -> stringResource(R.string.neon_blue)
    ColorScheme.PURPLE -> stringResource(R.string.purple)
    ColorScheme.GREEN -> stringResource(R.string.green)
    ColorScheme.ORANGE -> stringResource(R.string.orange)
    ColorScheme.RED -> stringResource(R.string.red)
    ColorScheme.CUSTOM -> stringResource(R.string.custom)
}

@Composable
fun SettingSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(cornerRadius = 20.dp)
            .padding(16.dp)
    ) {
        Column {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
fun SettingItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = cs.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = cs.onBackground,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }

        Icon(
            Icons.Default.ChevronRight,
            contentDescription = null,
            tint = cs.onSurfaceVariant
        )
    }
}


@Composable
fun ColorSchemeSelectionDialog(
    currentScheme: ColorScheme,
    onSchemeSelected: (ColorScheme) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = {
            Text(
                stringResource(R.string.color_style),
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
        },
        text = {
            Column {
                ColorScheme.entries.forEach { scheme ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSchemeSelected(scheme) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Color preview
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(getColorForScheme(scheme))
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            scheme.toLocalizedDisplayName(),
                            color = cs.onBackground,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        if (currentScheme == scheme) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = cs.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant)
            }
        }
    )
}

@Composable
fun LanguageSelectionDialog(
    currentLanguage: SupportedLanguage,
    onLanguageSelected: (SupportedLanguage) -> Unit,
    onDismiss: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = {
            Text(
                stringResource(R.string.app_language),
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
        },
        text = {
            Column {
                SupportedLanguage.entries.forEach { language ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onLanguageSelected(language) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = currentLanguage == language,
                            onClick = { onLanguageSelected(language) },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = cs.primary,
                                unselectedColor = cs.onSurfaceVariant
                            )
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            language.displayName,
                            color = cs.onBackground,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant)
            }
        }
    )
}

fun getColorForScheme(scheme: ColorScheme): Color {
    return when (scheme) {
        ColorScheme.NEON_BLUE -> Color(0xFF00D4FF)
        ColorScheme.PURPLE -> Color(0xFF9C27B0)
        ColorScheme.GREEN -> Color(0xFF4CAF50)
        ColorScheme.ORANGE -> Color(0xFFFF9800)
        ColorScheme.RED -> Color(0xFFF44336)
        ColorScheme.CUSTOM -> Color(0xFF00D4FF)
    }
}
