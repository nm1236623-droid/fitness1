package com.example.fitness.ui.template

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.fitness.template.*
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WorkoutTemplateMarketScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { WorkoutTemplateRepository(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    var templates by remember { mutableStateOf<List<WorkoutTemplate>>(emptyList()) }
    var selectedCategory by remember { mutableStateOf<TemplateCategory?>(null) }
    var showCategoryDialog by remember { mutableStateOf(false) }

    // 載入公開模板
    LaunchedEffect(selectedCategory) {
        repository.getPublicTemplates(selectedCategory).collect {
            templates = it
        }
    }

    // 同時載入本地模板
    var localTemplates by remember { mutableStateOf<List<WorkoutTemplate>>(emptyList()) }
    LaunchedEffect(Unit) {
        localTemplates = repository.getLocalTemplates()
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets(0, 0, 0, 0)
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(TechColors.DarkBlue, TechColors.DeepPurple)))
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassEffect(cornerRadius = 20.dp)
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = TechColors.NeonBlue)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "訓練模板",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = TechColors.NeonBlue
                            )
                            Text(
                                "${templates.size} 個公開模板",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(0.7f)
                            )
                        }
                        IconButton(onClick = { showCategoryDialog = true }) {
                            Icon(Icons.Default.FilterList, null, tint = TechColors.NeonBlue)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // 我的模板區塊
                if (localTemplates.isNotEmpty()) {
                    Text(
                        "我的模板",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LazyColumn(
                        modifier = Modifier.height(120.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(localTemplates) { template ->
                            CompactTemplateCard(
                                template = template,
                                onUse = {
                                    scope.launch {
                                        snackbarHostState.showSnackbar("已使用模板")
                                    }
                                }
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color.White.copy(0.1f))
                    Spacer(Modifier.height(16.dp))
                }

                // 公開模板列表
                Text(
                    "社群模板",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(templates) { template ->
                        TemplateCard(
                            template = template,
                            onUse = {
                                scope.launch {
                                    repository.incrementUsageCount(template.id)
                                    snackbarHostState.showSnackbar("已使用模板創建計劃")
                                }
                            },
                            onLike = {
                                scope.launch {
                                    repository.likeTemplate(template.id)
                                    snackbarHostState.showSnackbar("已點讚")
                                }
                            }
                        )
                    }
                }
            }

            // Category filter dialog
            if (showCategoryDialog) {
                CategoryFilterDialog(
                    currentCategory = selectedCategory,
                    onCategorySelected = { category ->
                        selectedCategory = category
                        showCategoryDialog = false
                    },
                    onDismiss = { showCategoryDialog = false }
                )
            }
        }
    }
}

@Composable
fun TemplateCard(
    template: WorkoutTemplate,
    onUse: () -> Unit,
    onLike: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(16.dp)
            .padding(16.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        template.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        "by ${template.creatorName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(0.6f)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = TechColors.NeonBlue.copy(0.2f)
                ) {
                    Text(
                        template.category.name,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = TechColors.NeonBlue,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                template.description,
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(0.8f),
                maxLines = 2
            )

            // Stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatItem(Icons.Default.Timer, "${template.estimatedDuration} 分鐘")
                StatItem(Icons.Default.FitnessCenter, "${template.exercises.size} 動作")
                StatItem(Icons.AutoMirrored.Filled.TrendingUp, "難度 ${template.difficulty}/5")
            }

            // Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUse,
                    modifier = Modifier.weight(1f).height(40.dp).neonGlowBorder(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TechColors.NeonBlue.copy(0.2f)
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("使用", color = TechColors.NeonBlue, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onLike,
                    modifier = Modifier.height(40.dp),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        Icons.Default.Favorite,
                        null,
                        tint = Color(0xFFFF6B6B),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("${template.likes}", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun CompactTemplateCard(
    template: WorkoutTemplate,
    onUse: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .glassEffect(12.dp)
            .clickable(onClick = onUse)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.FitnessCenter,
                null,
                tint = TechColors.NeonBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    template.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    "${template.exercises.size} 動作",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(0.6f)
                )
            }
            Icon(
                Icons.Default.ChevronRight,
                null,
                tint = Color.White.copy(0.5f)
            )
        }
    }
}

@Composable
fun StatItem(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            icon,
            null,
            tint = Color.White.copy(0.7f),
            modifier = Modifier.size(14.dp)
        )
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = Color.White.copy(0.7f),
            fontSize = 11.sp
        )
    }
}

@Composable
fun CategoryFilterDialog(
    currentCategory: TemplateCategory?,
    onCategorySelected: (TemplateCategory?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TechColors.DarkBlue.copy(0.95f),
        title = {
            Text("選擇分類", fontWeight = FontWeight.Bold, color = TechColors.NeonBlue)
        },
        text = {
            Column {
                TextButton(
                    onClick = { onCategorySelected(null) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "全部",
                        color = if (currentCategory == null) TechColors.NeonBlue else Color.White
                    )
                }

                TemplateCategory.entries.forEach { category ->
                    TextButton(
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            category.name,
                            color = if (currentCategory == category) TechColors.NeonBlue else Color.White
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("關閉", color = Color.White.copy(0.7f))
            }
        }
    )
}
