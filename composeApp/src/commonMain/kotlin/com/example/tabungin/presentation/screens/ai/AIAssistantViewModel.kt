package com.example.tabungin.presentation.screens.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tabungin.domain.model.Setoran
import com.example.tabungin.domain.model.Target
import com.example.tabungin.domain.repository.AIRepository
import com.example.tabungin.domain.repository.TargetRepository
import com.example.tabungin.domain.repository.WritingStyle
import com.example.tabungin.domain.usecase.GenerateIdeasUseCase
import com.example.tabungin.domain.usecase.ImproveWritingUseCase
import com.example.tabungin.domain.usecase.SummarizeNoteUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AIAssistantViewModel(
    private val aiRepository: AIRepository,
    private val summarizeUseCase: SummarizeNoteUseCase,
    private val improveWritingUseCase: ImproveWritingUseCase,
    private val generateIdeasUseCase: GenerateIdeasUseCase,
    private val targetRepository: TargetRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIAssistantUiState())
    val uiState: StateFlow<AIAssistantUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AIAssistantEvent>()
    val events: SharedFlow<AIAssistantEvent> = _events.asSharedFlow()

    init {
        loadTargets()
    }

    private fun loadTargets() {
        viewModelScope.launch {
            try {
                val targets = targetRepository?.getAllTargets()?.first() ?: emptyList()
                val setoran = targetRepository?.getAllSetoran()?.first() ?: emptyList()
                _uiState.update { it.copy(targets = targets, setoran = setoran) }
            } catch (e: Exception) {
                // Handle error silently
            }
        }
    }

    fun setInitialText(text: String?) {
        text?.let {
            _uiState.update { state -> state.copy(inputText = it) }
        }
    }

    fun onInputTextChange(text: String) {
        _uiState.update { it.copy(inputText = text, error = null) }
    }

    fun onActionSelected(action: AIAction) {
        _uiState.update { it.copy(selectedAction = action, result = null) }
        if (action == AIAction.ANALYZE) {
            analyzeTargets()
        }
    }

    private fun analyzeTargets() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val analysisPrompt = buildAnalysisPrompt(state.targets, state.setoran)
                val result = aiRepository.chat(analysisPrompt)
                result.onSuccess { output ->
                    _uiState.update { it.copy(isLoading = false, result = output) }
                }.onFailure { error ->
                    _uiState.update { it.copy(isLoading = false, error = error.message ?: "Terjadi kesalahan") }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Terjadi kesalahan") }
            }
        }
    }

    private fun buildAnalysisPrompt(targets: List<Target>, setoran: List<Setoran>): String {
        val targetsInfo = if (targets.isEmpty()) {
            "Belum ada target tabungan."
        } else {
            targets.joinToString("\n") { target ->
                val progress = ((target.terkumpul / target.targetAmount) * 100).toInt()
                "- ${target.icon} ${target.nama}: Rp ${formatNumber(target.terkumpul.toLong())} / Rp ${formatNumber(target.targetAmount.toLong())} ($progress%), deadline: ${target.deadline}"
            }
        }

        val totalTabungan = targets.sumOf { it.terkumpul }
        val totalTarget = targets.sumOf { it.targetAmount }
        val targetsTercapai = targets.count { it.terkumpul >= it.targetAmount }

        return """
Halo AI! Saya ingin minta bantuan untuk menganalisis tabungan saya.

📊 RINGKASAN TABUNGAN:
- Total Tabungan: Rp ${formatNumber(totalTabungan.toLong())}
- Total Target: Rp ${formatNumber(totalTarget.toLong())}
- Target Tercapai: $targetsTercapai dari ${targets.size}

📋 DAFTAR TARGET:
$targetsInfo

${_uiState.value.inputText.ifBlank { "Berikan saya analisis dan saran untuk mencapai target tabungan saya." }}
        """.trimIndent()
    }

    private fun formatNumber(number: Long): String {
        return number.toString().reversed().chunked(3).joinToString(".").reversed()
    }

    fun executeAction() {
        val state = _uiState.value

        if (state.inputText.isBlank() && state.selectedAction != AIAction.ANALYZE) {
            _uiState.update { it.copy(error = "Masukkan pertanyaan terlebih dahulu") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null, result = null) }

        viewModelScope.launch {
            when (state.selectedAction) {
                AIAction.ANALYZE -> {
                    analyzeTargets()
                }
                AIAction.ASK_ADVICE -> {
                    val context = buildContextForAdvice(state.targets, state.setoran)
                    aiRepository.chat("$context\n\nPertanyaan saya: ${state.inputText}")
                        .onSuccess { output ->
                            _uiState.update { it.copy(isLoading = false, result = output) }
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, error = error.message ?: "Terjadi kesalahan") }
                        }
                }
                AIAction.GENERATE_IDEAS -> {
                    generateIdeasUseCase(state.inputText)
                        .map { ideas ->
                            "💡 Ide untuk \"${state.inputText}\":\n\n" + ideas.mapIndexed { index, idea -> "${index + 1}. $idea" }.joinToString("\n")
                        }
                        .onSuccess { output ->
                            _uiState.update { it.copy(isLoading = false, result = output) }
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, error = error.message ?: "Terjadi kesalahan") }
                        }
                }
                AIAction.CHAT -> {
                    val context = buildSimpleContext(state.targets)
                    aiRepository.chat("$context\n\nPertanyaan saya: ${state.inputText}")
                        .onSuccess { output ->
                            _uiState.update { it.copy(isLoading = false, result = output) }
                        }
                        .onFailure { error ->
                            _uiState.update { it.copy(isLoading = false, error = error.message ?: "Terjadi kesalahan") }
                        }
                }
            }
        }
    }

    private fun buildContextForAdvice(targets: List<Target>, setoran: List<Setoran>): String {
        val totalTabungan = targets.sumOf { it.terkumpul }
        val targetsInfo = targets.take(5).joinToString("\n") { "- ${it.icon} ${it.nama}: ${((it.terkumpul / it.targetAmount) * 100).toInt()}% selesai" }

        return """
Saya sedang menabung dengan detail:
- Total tabungan saat ini: Rp ${formatNumber(totalTabungan.toLong())}
- Target aktif: ${targets.size}
$targetsInfo

Silakan berikan saran yang relevan.
        """.trimIndent()
    }

    private fun buildSimpleContext(targets: List<Target>): String {
        if (targets.isEmpty()) return ""

        val totalTabungan = targets.sumOf { it.terkumpul }
        return """
Sebagai konteks, saya sedang menabung dengan:
- Total tabungan: Rp ${formatNumber(totalTabungan.toLong())}
- ${targets.size} target tabungan aktif
        """.trimIndent()
    }

    fun copyResult() {
        val result = _uiState.value.result
        if (result != null) {
            viewModelScope.launch {
                _events.emit(AIAssistantEvent.CopyToClipboard(result))
            }
        }
    }

    fun applyToNote() {
        val result = _uiState.value.result
        if (result != null) {
            viewModelScope.launch {
                _events.emit(AIAssistantEvent.ApplyToNote(result))
            }
        }
    }

    private suspend fun generateIdeas(topic: String): Result<String> {
        return generateIdeasUseCase(topic).map { ideas ->
            "💡 Ide untuk \"$topic\":\n\n" + ideas.mapIndexed { index, idea -> "${index + 1}. $idea" }.joinToString("\n")
        }
    }

    private suspend fun chat(message: String): Result<String> {
        return aiRepository.chat(message)
    }
}

enum class AIAction(val displayName: String, val description: String) {
    ANALYZE("Analisis", "Analisis tabungan dan lihat saran AI"),
    ASK_ADVICE("Saran", "Tanya saran untuk tabungan"),
    GENERATE_IDEAS("Ide", "Generate ide menabung"),
    CHAT("Tanya AI", " Tanya AI tentang tabungan")
}

data class AIAssistantUiState(
    val inputText: String = "",
    val selectedAction: AIAction = AIAction.ANALYZE,
    val isLoading: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    val targets: List<Target> = emptyList(),
    val setoran: List<Setoran> = emptyList()
) {
    val canExecute: Boolean
        get() = inputText.isNotBlank() || selectedAction == AIAction.ANALYZE
}

sealed interface AIAssistantEvent {
    data class CopyToClipboard(val text: String) : AIAssistantEvent
    data class ApplyToNote(val text: String) : AIAssistantEvent
}
