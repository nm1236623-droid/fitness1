package com.example.fitness.ui

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.billing.SubscriptionManager
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import com.revenuecat.purchases.Package
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PaywallScreen(
    onDismiss: () -> Unit,
    onPurchaseSuccess: () -> Unit,
    userRole: com.example.fitness.coach.UserRole = com.example.fitness.coach.UserRole.COACH // 預設為教練
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var packages by remember { mutableStateOf<List<Package>>(emptyList()) }
    var selectedPackage by remember { mutableStateOf<Package?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var offeringsLoaded by remember { mutableStateOf(false) }

    // 載入商品 — 只顯示月費方案
    LaunchedEffect(Unit) {
        // 啟動超時保護（並行）
        val timeoutJob = scope.launch {
            delay(8000L)
            if (!offeringsLoaded) {
                offeringsLoaded = true
            }
        }
        SubscriptionManager.getOfferings { fetched ->
            // 只保留月費 package（RevenueCat identifier: $rc_monthly）
            val monthlyOnly = fetched.filter {
                it.identifier.equals("\$rc_monthly", ignoreCase = true)
            }.ifEmpty { fetched.take(1) } // fallback：若找不到月費，取第一個
            packages = monthlyOnly
            offeringsLoaded = true
            if (monthlyOnly.isNotEmpty()) selectedPackage = monthlyOnly.first()
            timeoutJob.cancel()
        }
    }

    // 全螢幕半透明背景
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.9f))
            .clickable(enabled = false) {}, // 攔截點擊
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .glassEffect(cornerRadius = 24.dp)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 關閉按鈕
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null, tint = Color.White)
                }
            }

            // 標題與圖示
            Icon(
                imageVector = Icons.Default.Star,
                contentDescription = null,
                tint = TechColors.NeonBlue,
                modifier = Modifier.size(64.dp)
            )
            Spacer(Modifier.height(16.dp))

            val (title, subtitle) = when (userRole) {
                com.example.fitness.coach.UserRole.COACH -> Pair(
                    "UNLOCK COACH FEATURES",
                    "Manage clients & advanced analytics"
                )
                com.example.fitness.coach.UserRole.TRAINEE -> Pair(
                    "UNLOCK AI COACH",
                    "Get personalized plans & analytics"
                )
            }

            Text(
                title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(32.dp))

            // 顯示方案列表
            if (packages.isEmpty() && !offeringsLoaded) {
                // 仍在載入中
                CircularProgressIndicator(color = TechColors.NeonBlue)
                Spacer(Modifier.height(8.dp))
                Text(
                    "載入方案中…",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.5f)
                )
            } else if (packages.isEmpty() && offeringsLoaded) {
                // 載入完成但無方案
                Text(
                    "⚠️ 目前無可用的訂閱方案",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(0.7f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "請稍後再試，或聯繫開發者。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.4f),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("返回", color = Color.White)
                }
            } else {
                packages.forEach { pkg ->
                    val isSelected = selectedPackage == pkg
                    val product = pkg.product

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) TechColors.NeonBlue.copy(0.2f) else Color.White.copy(0.05f))
                            .border(
                                1.dp,
                                if (isSelected) TechColors.NeonBlue else Color.White.copy(0.1f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { selectedPackage = pkg }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.title, color = Color.White, fontWeight = FontWeight.Bold)
                            Text(product.description, color = Color.White.copy(0.6f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        Text(
                            text = product.price.formatted,
                            color = if (isSelected) TechColors.NeonBlue else Color.White,
                            fontWeight = FontWeight.Bold
                        )
                        if (isSelected) {
                            Spacer(Modifier.width(8.dp))
                            Icon(Icons.Default.CheckCircle, null, tint = TechColors.NeonBlue)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // 訂閱按鈕
            Button(
                onClick = {
                    selectedPackage?.let { pkg ->
                        isLoading = true
                        SubscriptionManager.purchase(
                            activity = context as Activity,
                            packageToBuy = pkg,
                            onSuccess = {
                                isLoading = false
                                onPurchaseSuccess()
                            },
                            onError = { msg ->
                                isLoading = false
                                Toast.makeText(context, "Purchase failed: $msg", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                },
                enabled = !isLoading && selectedPackage != null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .neonGlowBorder(cornerRadius = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = TechColors.NeonBlue.copy(0.2f))
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TechColors.NeonBlue)
                } else {
                    Text("SUBSCRIBE NOW", color = TechColors.NeonBlue, fontWeight = FontWeight.Bold)
                }
            }

            Text(
                "Cancel anytime from Google Play",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(0.4f),
                modifier = Modifier.padding(top = 12.dp)
            )
        }
    }
}