package win.liuping.usque_android.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.data.Settings

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = ConfigRepository(app)

    private val _settings = MutableStateFlow(repo.loadSettings())
    val settings: StateFlow<Settings> = _settings

    fun update(settings: Settings) {
        _settings.value = settings
        viewModelScope.launch {
            repo.saveSettings(settings)
        }
    }
}
