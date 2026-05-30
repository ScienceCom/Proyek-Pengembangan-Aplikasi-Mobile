package com.example.tabungin.presentation.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.tabungin.data.local.datastore.UserPreferences
import com.example.tabungin.notification.NotificationService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isDarkMode: Boolean = false,
    val notifikasiAktif: Boolean = true,
    val namaUser: String = "",
    val isSaved: Boolean = false,
    val notifikasiJam: Int = 9,
    val notifikasiMenit: Int = 0,
    val notifTargetTercapai: Boolean = true,
    val showTimePicker: Boolean = false
)

class SettingsViewModel(
    private val userPreferences: UserPreferences,
    private val notificationService: NotificationService? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Collect all preferences
            launch {
                userPreferences.isDarkMode.collect { isDark ->
                    _uiState.update { it.copy(isDarkMode = isDark) }
                }
            }
            launch {
                userPreferences.namaUser.collect { name ->
                    _uiState.update { it.copy(namaUser = name) }
                }
            }
            launch {
                userPreferences.notifikasiAktif.collect { aktif ->
                    _uiState.update { it.copy(notifikasiAktif = aktif) }
                    // Schedule or cancel notification based on preference
                    if (aktif) {
                        val jam = _uiState.value.notifikasiJam
                        val menit = _uiState.value.notifikasiMenit
                        val nama = _uiState.value.namaUser
                        notificationService?.scheduleDailyReminder(jam, menit)
                    } else {
                        notificationService?.cancelDailyReminder()
                    }
                }
            }
            launch {
                userPreferences.notifikasiJam.collect { jam ->
                    _uiState.update { it.copy(notifikasiJam = jam) }
                    // Reschedule if notifications are enabled
                    if (_uiState.value.notifikasiAktif) {
                        notificationService?.scheduleDailyReminder(jam, _uiState.value.notifikasiMenit)
                    }
                }
            }
            launch {
                userPreferences.notifikasiMenit.collect { menit ->
                    _uiState.update { it.copy(notifikasiMenit = menit) }
                    // Reschedule if notifications are enabled
                    if (_uiState.value.notifikasiAktif) {
                        notificationService?.scheduleDailyReminder(_uiState.value.notifikasiJam, menit)
                    }
                }
            }
            launch {
                userPreferences.notifTargetTercapai.collect { aktif ->
                    _uiState.update { it.copy(notifTargetTercapai = aktif) }
                }
            }
        }
    }

    fun toggleDarkMode() {
        viewModelScope.launch {
            val newValue = !_uiState.value.isDarkMode
            userPreferences.setDarkMode(newValue)
        }
    }

    fun toggleNotifikasi() {
        viewModelScope.launch {
            val newValue = !_uiState.value.notifikasiAktif
            userPreferences.setNotifikasiAktif(newValue)
        }
    }

    fun toggleNotifTargetTercapai() {
        viewModelScope.launch {
            val newValue = !_uiState.value.notifTargetTercapai
            userPreferences.setNotifTargetTercapai(newValue)
        }
    }

    fun showTimePicker() {
        _uiState.update { it.copy(showTimePicker = true) }
    }

    fun hideTimePicker() {
        _uiState.update { it.copy(showTimePicker = false) }
    }

    fun setNotificationTime(hour: Int, minute: Int) {
        viewModelScope.launch {
            userPreferences.setNotifikasiJam(hour)
            userPreferences.setNotifikasiMenit(minute)
            _uiState.update { it.copy(notifikasiJam = hour, notifikasiMenit = minute, showTimePicker = false) }
        }
    }

    fun onNamaUserChange(name: String) {
        _uiState.update { it.copy(namaUser = name, isSaved = false) }
    }

    fun saveNamaUser() {
        viewModelScope.launch {
            userPreferences.setNamaUser(_uiState.value.namaUser)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun resetSavedState() {
        _uiState.update { it.copy(isSaved = false) }
    }
}
