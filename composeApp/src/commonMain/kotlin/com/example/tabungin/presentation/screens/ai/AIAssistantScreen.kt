package com.example.tabungin.presentation.screens.ai

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.QuestionAnswer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.tabungin.domain.model.Target
import com.example.tabungin.presentation.components.formatRupiah
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    onNavigateBack: () -> Unit,
    viewModel: AIAssistantViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AIAssistantEvent.CopyToClipboard -> {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("AI Result", event.text)
                    clipboard.setPrimaryClip(clip)
                    snackbarHostState.showSnackbar("Disalin ke clipboard")
                }
                is AIAssistantEvent.ApplyToNote -> {
                    snackbarHostState.showSnackbar("Hasil disimpan")
                }
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Assistant", fontWeight = FontWeight.Bold)
                        Text(
                            "TabungIn",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Action Chips
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(AIAction.entries) { action ->
                    ActionChip(
                        action = action,
                        isSelected = uiState.selectedAction == action,
                        onClick = { viewModel.onActionSelected(action) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Input & Result Area
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // Target Summary Card
	if (uiState.targets.isNotEmpty()) {
                    TargetSummaryCard(
                        targets = uiState.targets,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                }

                // Input Field
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = viewModel::onInputTextChange,
                    label = { Text("Tanyakan sesuatu...") },
                    placeholder = {
                        Text(
                            when (uiState.selectedAction) {
                                AIAction.ANALYZE -> "Klik 'Analisis' untuk melihat analisis otomatis"
                                AIAction.ASK_ADVICE -> "Apa yang ingin kamu tanyakan?"
                                AIAction.GENERATE_IDEAS -> "Topik ide yang kamu cari..."
                                AIAction.CHAT -> "Tanyakan apapun tentang tabungan..."
                            }
                        )
                    },
                    minLines = 3,
                    maxLines = 5,
                    isError = uiState.error != null,
                    supportingText = uiState.error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Execute Button
                Button(
                    onClick = { viewModel.executeAction() },
                    enabled = uiState.canExecute,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Menganalisis...")
                    } else {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            when (uiState.selectedAction) {
                                AIAction.ANALYZE -> "Analisis Tabungan"
                                AIAction.ASK_ADVICE -> "Minta Saran"
                                AIAction.GENERATE_IDEAS -> "Buat Ide Menabung"
                                AIAction.CHAT -> "Tanya AI"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Result Section
                AnimatedVisibility(visible = uiState.result != null) {
                    Column {
                        Text(
                            "💬 Hasil Analisis",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                uiState.result?.split("\n")?.forEach { line ->
                                    if (line.isNotBlank()) {
                                        Text(
                                            line,
                                            style = MaterialTheme.typography.bodyMedium,
                                            modifier = Modifier.padding(vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = { viewModel.copyResult() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("📋 Salin Hasil")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ActionChip(
    action: AIAction,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val icon = when (action) {
        AIAction.ANALYZE -> Icons.Default.AutoAwesome
        AIAction.ASK_ADVICE -> Icons.Default.Lightbulb
        AIAction.GENERATE_IDEAS -> Icons.Default.AutoAwesome
        AIAction.CHAT -> Icons.Default.QuestionAnswer
    }

    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(action.displayName) },
        leadingIcon = {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary
        )
    )
}

@Composable
private fun TargetSummaryCard(
    targets: List<Target>,
    modifier: Modifier = Modifier
) {
    val totalTabungan = targets.sumOf { it.terkumpul }
    val totalTarget = targets.sumOf { it.targetAmount }
    val achievedCount = targets.count { it.terkumpul >= it.targetAmount }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Box(
                        modifier = Modifier.padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("💰", style = MaterialTheme.typography.titleLarge)
                    }
                }
                Column {
                    Text(
                        "Ringkasan Tabungan",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${targets.size} target aktif",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatRupiah(totalTabungan),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text("Total Tabungan", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        formatRupiah(totalTarget),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Total Target", style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "$achievedCount/${targets.size}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (achievedCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface
                    )
                    Text("Tercapai", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
