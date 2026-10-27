package com.example.fitness.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitness.activity.ActivityLogRepository

/**
 * 簡化的營養記錄頁面
 * 專注於基本功能展示，避免複雜的數據依賴問題
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleNutritionRecordsScreen(
    activityRepository: ActivityLogRepository,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf("summary") }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("營養記錄") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "添加記錄")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 標籤選擇
            TabRow(selectedTabIndex = when (selectedTab) {
                "summary" -> 0
                "records" -> 1
                "analytics" -> 2
                else -> 0
            }) {
                Tab(
                    selected = selectedTab == "summary",
                    onClick = { selectedTab = "summary" },
                    text = { Text("摘要") }
                )
                Tab(
                    selected = selectedTab == "records",
                    onClick = { selectedTab = "records" },
                    text = { Text("記錄") }
                )
                Tab(
                    selected = selectedTab == "analytics",
                    onClick = { selectedTab = "analytics" },
                    text = { Text("分析") }
                )
            }

            // 內容區域
            when (selectedTab) {
                "summary" -> NutritionSummaryContent(activityRepository)
                "records" -> NutritionRecordsContent(activityRepository)
                "analytics" -> NutritionAnalyticsContent()
            }
        }
    }

    // 添加記錄對話框
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("添加營養記錄") },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = "",
                        onValueChange = { },
                        label = { Text("餐點名稱") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = "",
                        onValueChange = { },
                        label = { Text("卡路里") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    
                    OutlinedTextField(
                        value = "",
                        onValueChange = { },
                        label = { Text("蛋白質 (g)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showAddDialog = false }
                ) {
                    Text("保存")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showAddDialog = false }
                ) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun NutritionSummaryContent(
    activityRepository: ActivityLogRepository
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            SummaryCard(
                title = "今日營養摘要",
                stats = listOf(
                    "卡路里" to "1,850 / 2,200 kcal",
                    "蛋白質" to "95g / 120g",
                    "碳水化合物" to "220g / 275g",
                    "脂肪" to "65g / 73g"
                )
            )
        }

        item {
            SummaryCard(
                title = "本週統計",
                stats = listOf(
                    "平均卡路里" to "2,100 kcal",
                    "運動次數" to "4 次",
                    "蛋白質平均" to "88g",
                    "達成率" to "85%"
                )
            )
        }

        item {
            ProgressCard(
                title = "今日目標達成率",
                progress = 0.85f,
                description = "繼續保持！您今天做得很好。"
            )
        }
    }
}

@Composable
private fun NutritionRecordsContent(
    activityRepository: ActivityLogRepository
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 模擬一些記錄數據
        val mockRecords = listOf(
            NutritionRecord("早餐", "燕麥粥", 450, 15, "08:00"),
            NutritionRecord("午餐", "雞胸肉沙拉", 650, 35, "12:30"),
            NutritionRecord("晚餐", "糙米飯配菜", 750, 25, "18:45"),
            NutritionRecord("加餐", "蛋白質奶昔", 200, 20, "21:00")
        )

        items(mockRecords) { record ->
            NutritionRecordCard(record = record)
        }
    }
}

@Composable
private fun NutritionAnalyticsContent() {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnalyticsCard(
                title = "營養趨勢分析",
                description = "過去7天的卡路里攝取呈現上升趨勢",
                trend = "上升",
                percentage = "+12%"
            )
        }

        item {
            AnalyticsCard(
                title = "營養素分布",
                description = "本週營養素分布均衡",
                trend = "均衡",
                percentage = "良好"
            )
        }

        item {
            AnalyticsCard(
                title = "建議改善",
                description = "建議增加蔬菜攝取，減少精緻碳水化合物",
                trend = "改善",
                percentage = "待優化"
            )
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    stats: List<Pair<String, String>>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            stats.forEach { (label, value) ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        value,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun ProgressCard(
    title: String,
    progress: Float,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth()
            )
            
            Text(
                "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NutritionRecordCard(
    record: NutritionRecord
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    record.meal,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    record.food,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    record.time,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    "${record.calories} kcal",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "蛋白質 ${record.protein}g",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@Composable
private fun AnalyticsCard(
    title: String,
    description: String,
    trend: String,
    percentage: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "趨勢：$trend",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    percentage,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = when (trend) {
                        "上升", "良好" -> MaterialTheme.colorScheme.primary
                        "下降", "待優化" -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

/**
 * 營養記錄數據類
 */
data class NutritionRecord(
    val meal: String,
    val food: String,
    val calories: Int,
    val protein: Int,
    val time: String
)
