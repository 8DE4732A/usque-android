package win.liuping.usque_android.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

private const val PREF_FILE = "usque_secure_prefs"
private const val KEY_CONFIG = "config_json"
private const val KEY_SETTINGS = "settings_json"

class ConfigRepository(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREF_FILE,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    fun hasConfig(): Boolean = prefs.contains(KEY_CONFIG)

    fun saveConfig(configJson: String) {
        prefs.edit().putString(KEY_CONFIG, configJson).apply()
    }

    fun loadConfigJson(): String? = prefs.getString(KEY_CONFIG, null)

    fun loadConfig(): UsqueConfig? {
        val json = loadConfigJson() ?: return null
        return try {
            Json.decodeFromString(UsqueConfig.serializer(), json)
        } catch (_: Exception) {
            null
        }
    }

    fun clearConfig() {
        prefs.edit().remove(KEY_CONFIG).apply()
    }

    fun updateConfigFields(fields: Map<String, String>) {
        val existing = loadConfigJson() ?: return
        val merged = try {
            val base = Json.parseToJsonElement(existing).jsonObject.toMutableMap()
            fields.forEach { (k, v) -> base[k] = Json.parseToJsonElement("\"$v\"") }
            JsonObject(base).toString()
        } catch (_: Exception) {
            return
        }
        saveConfig(merged)
    }

    fun saveSettings(settings: Settings) {
        val json = Json.encodeToString(Settings.serializer(), settings)
        prefs.edit().putString(KEY_SETTINGS, json).apply()
    }

    fun loadSettings(): Settings {
        val json = prefs.getString(KEY_SETTINGS, null) ?: return Settings()
        return try {
            Json.decodeFromString(Settings.serializer(), json)
        } catch (_: Exception) {
            Settings()
        }
    }
}
