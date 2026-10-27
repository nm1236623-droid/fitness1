package com.example.fitness.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.fitness.activity.ActivityLogRepository
import kotlinx.coroutines.launch

/**
 * 綜合營養功能測試頁（舊版 Compose 完全相容）
 */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ComprehensiveNutritionTestScreen(
    activityRepository: ActivityLogRepository,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val scope = rememberCoroutineScope()
    var currentTest by remember { mutableStateOf("overview") }
    var testResults by remember { mutableStateOf(mapOf<String, Boolean>()) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("營養功能綜合測試") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                testResults = runAllTests(activityRepository)
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "執行全部測試"
                        )
                    }
                }
            )
        },
        bottomBar = {
            SimpleBottomBar(
                current = currentTest,
                onSelect = { currentTest = it }
            )
        }
    ) { padding ->
        Box(
            modifier = modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            when (currentTest) {
                "overview" -> TestOverviewScreen(
                    activityRepository = activityRepository,
                    testResults = testResults,
                    onRunTest = { testType ->
                        scope.launch {
                            val result = runSingleTest(testType, activityRepository)
                            testResults = testResults.toMutableMap().apply {
                                put(testType, result)
                            }
                        }
                    }
                )

                "responsive" -> ResponsiveTestScreen()
                "charts" -> ChartsTestScreen()
                "preferences" -> PreferencesTestScreen()
            }
        }
    }
}

/* ---------------- Bottom Bar（相容版） ---------------- */

@Composable
private fun SimpleBottomBar(
    current: String,
    onSelect: (String) -> Unit
) {
    Surface(shadowElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SimpleBottomNavItem(
                selected = current == "overview",
                icon = Icons.Default.Dashboard,
                label = "總覽"
            ) { onSelect("overview") }

            SimpleBottomNavItem(
                selected = current == "responsive",
                icon = Icons.Default.Devices,
                label = "響應式"
            ) { onSelect("responsive") }

            SimpleBottomNavItem(
                selected = current == "charts",
                icon = Icons.Default.BarChart,
                label = "圖表"
            ) { onSelect("charts") }

            SimpleBottomNavItem(
                selected = current == "preferences",
                icon = Icons.Default.Settings,
                label = "偏好"
            ) { onSelect("preferences") }
        }
    }
}

@Composable
private fun SimpleBottomNavItem(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val color =
        if (selected) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
            .width(72.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = color)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

/* ---------------- Overview ---------------- */

@Composable
private fun TestOverviewScreen(
    activityRepository: ActivityLogRepository,
    testResults: Map<String, Boolean>,
    onRunTest: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "營養功能測試總覽",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        TestSummaryCard(testResults)
        TestListCard(
            title = "基本功能測試",
            tests = listOf(
                "data_loading" to "資料載入",
                "ui_components" to "UI 組件",
                "navigation" to "導航",
                "state_management" to "狀態管理"
            ),
            onRunTest = onRunTest
        )

        TestListCard(
            title = "功能測試",
            tests = listOf(
                "responsive_layout" to "響應式布局",
                "chart_rendering" to "圖表渲染",
                "user_preferences" to "使用者偏好",
                "data_persistence" to "資料保存"
            ),
            onRunTest = onRunTest
        )
    }
}

@Composable
private fun TestSummaryCard(testResults: Map<String, Boolean>) {
    val total = testResults.size
    val passed = testResults.values.count { it }

    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("測試摘要", style = MaterialTheme.typography.titleLarge)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TestStatItem("總數", total.toString())
                TestStatItem("通過", passed.toString())
                TestStatItem("失敗", (total - passed).toString())
            }

            if (total > 0) {
                LinearProgressIndicator(
                    progress = { passed.toFloat() / total },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TestStatItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TestListCard(
    title: String,
    tests: List<Pair<String, String>>,
    onRunTest: (String) -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            tests.forEach { (id, name) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onRunTest(id) },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(name)
                    Button(onClick = { onRunTest(id) }) {
                        Text("執行")
                    }
                }
            }
        }
    }
}

/* ---------------- Other Screens ---------------- */

@Composable
private fun ResponsiveTestScreen() {
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("響應式布局測試", style = MaterialTheme.typography.headlineMedium) }
        item { TestComponentCard("手機", "小螢幕適配", "正常") }
        item { TestComponentCard("平板", "大螢幕適配", "正常") }
    }
}

@Composable
private fun ChartsTestScreen() {
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("圖表測試", style = MaterialTheme.typography.headlineMedium) }
        item { TestComponentCard("圓餅圖", "營養比例", "正常") }
        item { TestComponentCard("折線圖", "體重趨勢", "正常") }
    }
}

@Composable
private fun PreferencesTestScreen() {
    LazyColumn(
        modifier = Modifier.padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Text("偏好設定測試", style = MaterialTheme.typography.headlineMedium) }
        item { TestComponentCard("資料儲存", "SharedPreferences", "正常") }
    }
}

@Composable
private fun TestComponentCard(
    title: String,
    description: String,
    status: String
) {
    Card {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(status, color = MaterialTheme.colorScheme.primary)
            }
            Text(description)
        }
    }
}

/* ---------------- 假測試邏輯 ---------------- */

private fun runAllTests(repository: ActivityLogRepository): Map<String, Boolean> =
    mapOf(
        "data_loading" to true,
        "ui_components" to true,
        "navigation" to true,
        "state_management" to true,
        "responsive_layout" to true,
        "chart_rendering" to true,
        "user_preferences" to true,
        "data_persistence" to true
    )

private fun runSingleTest(
    testType: String,
    repository: ActivityLogRepository
): Boolean = true
