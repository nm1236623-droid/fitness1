package com.example.fitness.ui.social

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
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
import androidx.compose.ui.unit.dp
import com.example.fitness.R
import com.example.fitness.social.*
import com.example.fitness.ui.theme.TechColors
import com.example.fitness.ui.theme.glassEffect
import com.example.fitness.ui.theme.neonGlowBorder
import kotlinx.coroutines.launch
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocialCenterScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember { SocialRepository() }
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by remember { mutableStateOf(0) }
    val friendsTabStr = stringResource(R.string.friends_tab)
    val challengesTabStr = stringResource(R.string.challenges_tab)
    val leaderboardTabStr = stringResource(R.string.leaderboard_tab)
    val tabs = listOf(friendsTabStr, challengesTabStr, leaderboardTabStr)

    var friends by remember { mutableStateOf<List<Friendship>>(emptyList()) }
    var challenges by remember { mutableStateOf<List<Challenge>>(emptyList()) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showCreateChallengeDialog by remember { mutableStateOf(false) }

    // 載入好友列表
    LaunchedEffect(Unit) {
        repository.getFriends().collect { friends = it }
    }

    // 載入挑戰列表
    LaunchedEffect(Unit) {
        repository.getActiveChallenges().collect { challenges = it }
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
            Column(modifier = Modifier.fillMaxSize().widthIn(max = 840.dp).padding(16.dp)) {
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                stringResource(R.string.social_center),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                stringResource(R.string.social_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Action buttons
                        when (selectedTab) {
                            0 -> IconButton(onClick = { showSearchDialog = true }) {
                                Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                            }
                            1 -> IconButton(onClick = { showCreateChallengeDialog = true }) {
                                Icon(Icons.Default.Add, null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Tabs
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = {
                                Text(
                                    title,
                                    fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                // Content
                val joinedChallengeMsg = stringResource(R.string.joined_challenge)
                when (selectedTab) {
                    0 -> FriendsTab(friends, repository, scope, snackbarHostState)
                    1 -> ChallengesTab(challenges, repository, scope, snackbarHostState, joinedChallengeMsg)
                    2 -> LeaderboardTab(repository, scope)
                }
            }

            // Dialogs
            if (showSearchDialog) {
                val friendRequestSentMsg = stringResource(R.string.friend_request_sent)
                val sendFailedMsg = stringResource(R.string.send_failed)
                SearchUserDialog(
                    repository = repository,
                    onDismiss = { showSearchDialog = false },
                    onUserSelected = { userId, userName ->
                        scope.launch {
                            val result = repository.sendFriendRequest(userId, userName)
                            if (result.isSuccess) {
                                snackbarHostState.showSnackbar(friendRequestSentMsg)
                            } else {
                                snackbarHostState.showSnackbar(sendFailedMsg)
                            }
                            showSearchDialog = false
                        }
                    }
                )
            }

            if (showCreateChallengeDialog) {
                val challengeCreatedMsg = stringResource(R.string.challenge_created)
                CreateChallengeDialog(
                    repository = repository,
                    onDismiss = { showCreateChallengeDialog = false },
                    onCreated = {
                        showCreateChallengeDialog = false
                        scope.launch {
                            snackbarHostState.showSnackbar(challengeCreatedMsg)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun FriendsTab(
    friends: List<Friendship>,
    repository: SocialRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val cs = MaterialTheme.colorScheme
    if (friends.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassEffect(20.dp)
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.People,
                    null,
                    modifier = Modifier.size(64.dp),
                    tint = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.no_friends_yet),
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onBackground
                )
                Text(
                    stringResource(R.string.add_friends_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(friends) { friend ->
                FriendCard(friend, repository, scope, snackbarHostState)
            }
        }
    }
}

@Composable
fun FriendCard(
    friend: Friendship,
    repository: SocialRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(16.dp)
            .clickable { /* Navigate to friend profile */ }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(cs.primary.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    friend.friendName.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.primary
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    friend.friendName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground
                )
                Text(
                    stringResource(R.string.friend_since, friend.createdAt.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))),
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            Icon(
                Icons.AutoMirrored.Filled.Message,
                null,
                tint = cs.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ChallengesTab(
    challenges: List<Challenge>,
    repository: SocialRepository,
    scope: kotlinx.coroutines.CoroutineScope,
    snackbarHostState: SnackbarHostState,
    joinedChallengeMsg: String
) {
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(challenges) { challenge ->
            ChallengeCard(
                challenge = challenge,
                onJoin = {
                    scope.launch {
                        val result = repository.joinChallenge(challenge.id)
                        if (result.isSuccess) {
                            snackbarHostState.showSnackbar(joinedChallengeMsg)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ChallengeCard(
    challenge: Challenge,
    onJoin: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(16.dp)
            .padding(16.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        challenge.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = cs.primary
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        challenge.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = cs.onSurfaceVariant
                    )
                }

                Chip(challenge.type.name)
            }

            Spacer(Modifier.height(12.dp))

            // Goal
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Flag,
                    null,
                    tint = cs.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.goal_target, challenge.goal.target.toString(), challenge.goal.unit),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onBackground
                )
            }

            Spacer(Modifier.height(8.dp))

            // Participants
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.People,
                    null,
                    tint = cs.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.participants_count, challenge.participants.size),
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(16.dp))

            // Join button
            Button(
                onClick = onJoin,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .neonGlowBorder(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = cs.primary.copy(0.2f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.join_challenge), fontWeight = FontWeight.Bold, color = cs.primary)
            }
        }
    }
}

@Composable
fun Chip(text: String) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cs.primary.copy(0.2f)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            color = cs.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun LeaderboardTab(
    repository: SocialRepository,
    scope: kotlinx.coroutines.CoroutineScope
) {
    val cs = MaterialTheme.colorScheme
    var leaderboard by remember { mutableStateOf<List<LeaderboardEntry>>(emptyList()) }
    var selectedMetric by remember { mutableStateOf(ChallengeMetric.TOTAL_CALORIES_BURNED) }

    LaunchedEffect(selectedMetric) {
        scope.launch {
            val result = repository.getGlobalLeaderboard(selectedMetric)
            if (result.isSuccess) {
                leaderboard = result.getOrDefault(emptyList())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Metric selector
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassEffect(12.dp)
                .padding(12.dp)
        ) {
            Text(
                stringResource(R.string.calories_leaderboard),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground
            )
        }

        Spacer(Modifier.height(16.dp))

        // Leaderboard list
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(leaderboard.size) { index ->
                LeaderboardEntryCard(leaderboard[index])
            }
        }
    }
}

@Composable
fun LeaderboardEntryCard(entry: LeaderboardEntry) {
    val cs = MaterialTheme.colorScheme
    val rankColor = when (entry.rank) {
        1 -> Color(0xFFFFD700) // Gold
        2 -> Color(0xFFC0C0C0) // Silver
        3 -> Color(0xFFCD7F32) // Bronze
        else -> cs.onSurfaceVariant
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassEffect(12.dp)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Rank
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(rankColor.copy(0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "${entry.rank}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = rankColor
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.userName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = cs.onBackground
                )
                Text(
                    "${entry.score.toInt()} ${entry.metric}",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant
                )
            }

            if (entry.rank <= 3) {
                Icon(
                    Icons.Default.Star,
                    null,
                    tint = rankColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

@Composable
fun SearchUserDialog(
    repository: SocialRepository,
    onDismiss: () -> Unit,
    onUserSelected: (String, String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserProfile>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = {
            Text(stringResource(R.string.search_friend), fontWeight = FontWeight.Bold, color = cs.onBackground)
        },
        text = {
            Column {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        if (it.length >= 2) {
                            scope.launch {
                                val result = repository.searchUsers(it)
                                searchResults = result.getOrDefault(emptyList())
                            }
                        }
                    },
                    label = { Text(stringResource(R.string.enter_username)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = cs.onBackground,
                        unfocusedTextColor = cs.onBackground,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline
                    )
                )

                Spacer(Modifier.height(12.dp))

                searchResults.forEach { user ->
                    TextButton(
                        onClick = { onUserSelected(user.id, user.displayName) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(user.displayName, color = cs.onBackground)
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
fun CreateChallengeDialog(
    repository: SocialRepository,
    onDismiss: () -> Unit,
    onCreated: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var target by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = cs.surface,
        title = {
            Text(stringResource(R.string.create_challenge), fontWeight = FontWeight.Bold, color = cs.onBackground)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(stringResource(R.string.challenge_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = cs.onBackground,
                        unfocusedTextColor = cs.onBackground,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline
                    )
                )

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text(stringResource(R.string.challenge_description)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = cs.onBackground,
                        unfocusedTextColor = cs.onBackground,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline
                    )
                )

                OutlinedTextField(
                    value = target,
                    onValueChange = { if (it.all { c -> c.isDigit() }) target = it },
                    label = { Text(stringResource(R.string.challenge_target)) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = cs.onBackground,
                        unfocusedTextColor = cs.onBackground,
                        focusedBorderColor = cs.primary,
                        unfocusedBorderColor = cs.outline
                    )
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        val challenge = Challenge(
                            id = "",
                            title = title,
                            description = description,
                            type = ChallengeType.WEEKLY,
                            goal = ChallengeGoal(
                                metric = ChallengeMetric.TOTAL_CALORIES_BURNED,
                                target = target.toDoubleOrNull() ?: 0.0,
                                unit = "kcal"
                            ),
                            startDate = java.time.Instant.now(),
                            endDate = java.time.Instant.now().plusSeconds(7 * 24 * 3600),
                            createdBy = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid ?: ""
                        )

                        repository.createChallenge(challenge)
                        onCreated()
                    }
                }
            ) {
                Text(stringResource(R.string.create_challenge), color = cs.primary, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel), color = cs.onSurfaceVariant)
            }
        }
    )
}
