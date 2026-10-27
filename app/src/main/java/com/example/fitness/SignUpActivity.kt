package com.example.fitness

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.Toast
import com.example.fitness.util.setupEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.fitness.firebase.AuthRepository
import com.example.fitness.localization.LocaleManager
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {
    private val authRepository = AuthRepository()

    override fun attachBaseContext(newBase: Context) {
        val localeManager = LocaleManager.getInstance(newBase)
        val currentLanguage = localeManager.getCurrentLanguageSync()
        val context = localeManager.applyLanguage(newBase, currentLanguage)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        setupEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sign_up)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Simple form: email, password, confirm
        val inputEmail = findViewById<EditText>(R.id.inputEmailSignUp)
        val inputPassword = findViewById<EditText>(R.id.inputPasswordSignUp)
        val inputConfirm = findViewById<EditText>(R.id.inputConfirmSignUp)
        val btnSignUp = findViewById<Button>(R.id.btnSignUp)
        val progress = findViewById<ProgressBar>(R.id.progressSignUp)

        btnSignUp.setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val pwd = inputPassword.text.toString()
            val conf = inputConfirm.text.toString()
            if (email.isEmpty() || pwd.isEmpty() || conf.isEmpty()) {
                Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (pwd != conf) {
                Toast.makeText(this, "Passwords do not match", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            btnSignUp.isEnabled = false
            progress.visibility = View.VISIBLE

            lifecycleScope.launch {
                val result = authRepository.signUpWithEmail(email, pwd)
                progress.visibility = View.GONE
                btnSignUp.isEnabled = true
                result.fold(onSuccess = { user ->
                    if (user != null) {
                        // After sign up we want user to sign in explicitly.
                        // Firebase automatically signs in after createUserWithEmailAndPassword,
                        // so sign out first to avoid jumping into the main app.
                        authRepository.signOut()

                        Toast.makeText(this@SignUpActivity, "Account created. Please sign in.", Toast.LENGTH_SHORT).show()

                        // Navigate back to MainActivity (which hosts the single SignIn UI) and clear back stack
                        val intent = Intent(this@SignUpActivity, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        Toast.makeText(this@SignUpActivity, "Sign up succeeded but no user", Toast.LENGTH_SHORT).show()
                    }
                }, onFailure = { t ->
                    Toast.makeText(this@SignUpActivity, "Sign up failed: ${t.message}", Toast.LENGTH_LONG).show()
                })
            }
        }
    }
}