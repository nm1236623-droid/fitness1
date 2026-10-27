package com.example.fitness.firebase

import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.tasks.await

class AuthRepository(private val auth: FirebaseAuth = FirebaseAuth.getInstance()) {

    fun currentUser(): FirebaseUser? = auth.currentUser

    suspend fun signUpWithEmail(email: String, password: String): Result<FirebaseUser?> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            Result.success(result.user)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun signInWithEmail(email: String, password: String): Result<FirebaseUser?> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            Result.success(result.user)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ★ 新增這個函式來支援 Google 登入
    suspend fun firebaseSignInWithGoogle(idToken: String): Result<FirebaseUser?> {
        return try {
            // 1. 將 Google 傳來的 idToken 轉換成 Firebase 憑證
            val credential = GoogleAuthProvider.getCredential(idToken, null)

            // 2. 用這個憑證登入 Firebase
            val result = auth.signInWithCredential(credential).await()
            Result.success(result.user)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    // ★ 新增 Facebook 登入支援 (使用 Firebase Federated OAuth Provider)
    suspend fun firebaseSignInWithFacebook(activity: android.app.Activity): Result<FirebaseUser?> {
        return try {
            val provider = com.google.firebase.auth.OAuthProvider.newBuilder("facebook.com")
                .setScopes(listOf("email", "public_profile"))
                .build()

            val pendingTask = auth.pendingAuthResult
            val authResult = if (pendingTask != null) {
                pendingTask.await()
            } else {
                auth.startActivityForSignInWithProvider(activity, provider).await()
            }
            Result.success(authResult.user)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    /**
     * 刪除帳號：先清除 Firestore 使用者資料與 Storage 頭像，再刪除 Firebase Auth 帳號。
     * 若帳號是 Email/Password 登入，需要先 re-authenticate（傳入密碼）。
     */
    suspend fun deleteAccount(password: String? = null): Result<Unit> {
        val user = auth.currentUser
            ?: return Result.failure(IllegalStateException("Not signed in"))

        return try {
            // 1. Re-authenticate if needed
            if (password != null && user.email != null) {
                val credential = EmailAuthProvider.getCredential(user.email!!, password)
                user.reauthenticate(credential).await()
            }

            val uid = user.uid

            // 2. Delete Firestore user document
            try {
                FirebaseFirestore.getInstance().collection("users").document(uid).delete().await()
            } catch (_: Exception) { /* best effort */ }

            // 3. Delete user profile document
            try {
                FirebaseFirestore.getInstance().collection("user_profiles").document(uid).delete().await()
            } catch (_: Exception) { /* best effort */ }

            // 4. Delete Storage avatar
            try {
                FirebaseStorage.getInstance()
                    .reference.child("user_avatars/$uid/avatar.jpg")
                    .delete().await()
            } catch (_: Exception) { /* best effort */ }

            // 5. Delete Firebase Auth user
            user.delete().await()

            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    fun signOut() {
        auth.signOut()
    }
}