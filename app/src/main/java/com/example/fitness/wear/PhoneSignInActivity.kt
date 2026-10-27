package com.example.fitness

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.fitness.util.setupEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.example.fitness.coach.UserRole
import com.example.fitness.localization.LocaleManager
import com.example.fitness.ui.theme.FitnessTheme
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.example.fitness.user.UserRoleProfileRepository
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class PhoneSignInActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    // UI State
    private var isLoading by mutableStateOf(false)
    private var uiState by mutableStateOf(PhoneSignInStage.INPUT_PHONE)
    private var errorMessage by mutableStateOf<String?>(null)

    // ★★★ 新增：記錄使用者選擇的角色 (預設為學員) ★★★
    private var selectedRole by mutableStateOf(UserRole.TRAINEE)

    // Firebase Data
    private var storedVerificationId by mutableStateOf<String?>(null)
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null

    enum class PhoneSignInStage {
        INPUT_PHONE,
        INPUT_CODE
    }

    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager.getInstance(newBase)
        val currentLanguage = localeManager.getCurrentLanguageSync()
        val context = localeManager.applyLanguage(newBase, currentLanguage)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupEdgeToEdge()
        super.onCreate(savedInstanceState)
        auth = FirebaseAuth.getInstance()

        setContent {
            FitnessTheme {
                PhoneSignInScreen(
                    stage = uiState,
                    loading = isLoading,
                    error = errorMessage,
                    // ★ 傳入當前選擇的角色
                    currentRole = selectedRole,
                    // ★ 當使用者點擊切換角色時更新
                    onRoleSelected = { role -> selectedRole = role },
                    onSendCode = { phone -> startPhoneNumberVerification(phone) },
                    onVerifyCode = { code ->
                        if (storedVerificationId != null) {
                            verifyPhoneNumberWithCode(storedVerificationId!!, code)
                        }
                    },
                    onBack = {
                        if (uiState == PhoneSignInStage.INPUT_CODE) {
                            uiState = PhoneSignInStage.INPUT_PHONE
                            errorMessage = null
                        } else {
                            finish()
                        }
                    },
                    onGoogleClick = {
                        Toast.makeText(this, "請返回主畫面使用 Google 登入", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                )
            }
        }
    }

    // --- Firebase Logic ---

    private fun startPhoneNumberVerification(rawPhone: String) {
        val formattedPhone = formatPhoneNumber(rawPhone)
        isLoading = true
        errorMessage = null

        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(formattedPhone)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(this)
            .setCallbacks(callbacks)
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    private val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
        override fun onVerificationCompleted(credential: PhoneAuthCredential) {
            Log.d("PhoneAuth", "onVerificationCompleted:$credential")
            isLoading = false
            signInWithPhoneAuthCredential(credential)
        }

        override fun onVerificationFailed(e: FirebaseException) {
            Log.w("PhoneAuth", "onVerificationFailed", e)
            isLoading = false
            errorMessage = if (e.message?.contains("format") == true) {
                "號碼格式錯誤，請檢查 (例如: 0912345678)"
            } else {
                "驗證失敗: ${e.message}"
            }
        }

        override fun onCodeSent(verificationId: String, token: PhoneAuthProvider.ForceResendingToken) {
            Log.d("PhoneAuth", "onCodeSent:$verificationId")
            storedVerificationId = verificationId
            resendToken = token
            isLoading = false
            uiState = PhoneSignInStage.INPUT_CODE
        }
    }

    private fun verifyPhoneNumberWithCode(verificationId: String, code: String) {
        isLoading = true
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        signInWithPhoneAuthCredential(credential)
    }

    private fun signInWithPhoneAuthCredential(credential: PhoneAuthCredential) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // ★★★ 登入成功後，儲存角色資料 ★★★
                    lifecycleScope.launch {
                        try {
                            val roleRepo = UserRoleProfileRepository()
                            // 這裡 nickname 暫時傳 null 或空字串，因為電話登入沒有輸入暱稱的欄位
                            // 如果是舊用戶，upsert通常不會覆蓋既有資料(視您的Repo實作而定)
                            // 如果是新用戶，則會建立該角色
                            roleRepo.upsertMyRole(role = selectedRole, nickname = null)

                            Toast.makeText(this@PhoneSignInActivity, "登入成功 (${selectedRole.name})", Toast.LENGTH_SHORT).show()

                            val intent = Intent(this@PhoneSignInActivity, MainActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                            finish()
                        } catch (e: Exception) {
                            isLoading = false
                            errorMessage = "儲存資料失敗: ${e.message}"
                        }
                    }
                } else {
                    isLoading = false
                    errorMessage = "登入錯誤: ${task.exception?.message}"
                }
            }
    }

    private fun formatPhoneNumber(phone: String): String {
        var p = phone.replace(" ", "").replace("-", "")
        if (p.startsWith("+")) return p
        if (p.startsWith("09")) {
            p = p.substring(1)
            return "+886$p"
        }
        if (p.startsWith("886")) return "+$p"
        return "+886$p"
    }
}

// --- Compose UI ---

@Composable
fun PhoneSignInScreen(
    stage: PhoneSignInActivity.PhoneSignInStage,
    loading: Boolean,
    error: String?,
    currentRole: UserRole,          // ★ 接收當前角色
    onRoleSelected: (UserRole) -> Unit, // ★ 接收選擇事件
    onSendCode: (String) -> Unit,
    onVerifyCode: (String) -> Unit,
    onBack: () -> Unit,
    onGoogleClick: () -> Unit
) {
    var phoneNumber by remember { mutableStateOf("") }
    var verificationCode by remember { mutableStateOf("") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(TechColors.DarkBlue, TechColors.DeepPurple)
                )
            )
            .systemBarsPadding()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(cornerRadius = 24.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TechColors.NeonBlue)
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Icon(
                imageVector = if (stage == PhoneSignInActivity.PhoneSignInStage.INPUT_PHONE) Icons.Default.Phone else Icons.Default.Sms,
                contentDescription = null,
                tint = TechColors.NeonBlue,
                modifier = Modifier.size(64.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = if (stage == PhoneSignInActivity.PhoneSignInStage.INPUT_PHONE) "MOBILE ACCESS" else "VERIFICATION",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TechColors.NeonBlue,
                letterSpacing = 2.sp
            )

            Text(
                text = if (stage == PhoneSignInActivity.PhoneSignInStage.INPUT_PHONE) "Select role & enter number" else "Enter the code sent to you",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.6f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 24.dp)
            )

            // --- 輸入電話階段 ---
            AnimatedVisibility(
                visible = stage == PhoneSignInActivity.PhoneSignInStage.INPUT_PHONE,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column {
                    // ★★★ 新增：角色選擇 UI ★★★
                    Text(
                        text = "I AM A...",
                        style = MaterialTheme.typography.labelSmall,
                        color = TechColors.NeonBlue,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NeonPhoneRoleOption(
                            text = "Trainee",
                            selected = currentRole == UserRole.TRAINEE,
                            onClick = { onRoleSelected(UserRole.TRAINEE) },
                            modifier = Modifier.weight(1f)
                        )
                        NeonPhoneRoleOption(
                            text = "Coach",
                            selected = currentRole == UserRole.COACH,
                            onClick = { onRoleSelected(UserRole.COACH) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    NeonPhoneInput(
                        value = phoneNumber,
                        onValueChange = { phoneNumber = it },
                        label = "Phone Number (09xx...)",
                        icon = Icons.Default.Phone,
                        keyboardType = KeyboardType.Phone
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    NeonPhoneButton(
                        text = "SEND CODE",
                        loading = loading,
                        onClick = { onSendCode(phoneNumber) }
                    )
                }
            }

            // --- 輸入驗證碼階段 ---
            AnimatedVisibility(
                visible = stage == PhoneSignInActivity.PhoneSignInStage.INPUT_CODE,
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut() + slideOutVertically()
            ) {
                Column {
                    NeonPhoneInput(
                        value = verificationCode,
                        onValueChange = { verificationCode = it },
                        label = "6-Digit Code",
                        icon = Icons.Default.Dialpad,
                        keyboardType = KeyboardType.NumberPassword
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    NeonPhoneButton(
                        text = "VERIFY & LOGIN",
                        loading = loading,
                        onClick = { onVerifyCode(verificationCode) }
                    )
                }
            }

            if (error != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = error,
                    color = Color(0xFFFF4D4D),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(32.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.2f))
                Text(
                    text = "OR",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = Color.White.copy(alpha = 0.2f))
            }

            Spacer(modifier = Modifier.height(24.dp))

            GoogleStyleButton(onClick = onGoogleClick)
        }
    }
}

// --- Local Components ---

@Composable
fun NeonPhoneInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: ImageVector,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, tint = TechColors.NeonBlue) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Color.White,
            unfocusedTextColor = Color.White,
            focusedBorderColor = TechColors.NeonBlue,
            unfocusedBorderColor = TechColors.NeonBlue.copy(alpha = 0.3f),
            focusedLabelColor = TechColors.NeonBlue,
            unfocusedLabelColor = Color.White.copy(alpha = 0.7f),
            cursorColor = TechColors.NeonBlue,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent
        )
    )
}

@Composable
fun NeonPhoneButton(
    text: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !loading,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .neonGlowBorder(cornerRadius = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = TechColors.NeonBlue.copy(alpha = 0.2f),
            contentColor = TechColors.NeonBlue,
            disabledContainerColor = Color.Gray.copy(alpha = 0.1f)
        )
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TechColors.NeonBlue)
        } else {
            Text(text, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
    }
}

// ★★★ 新增：角色選擇按鈕元件 (與 SignUpScreen 風格一致) ★★★
@Composable
fun NeonPhoneRoleOption(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) TechColors.NeonBlue.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (selected) TechColors.NeonBlue else Color.Transparent,
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) TechColors.NeonBlue else Color.White.copy(alpha = 0.6f),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun GoogleStyleButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp),
        shape = RoundedCornerShape(12.dp),
        color = Color.White,
        shadowElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxSize()
        ) {
            Text(
                text = "G",
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                modifier = Modifier.padding(end = 12.dp)
            )

            Text(
                text = "Sign in with Google",
                color = Color.Black.copy(alpha = 0.8f),
                fontWeight = FontWeight.Medium,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}