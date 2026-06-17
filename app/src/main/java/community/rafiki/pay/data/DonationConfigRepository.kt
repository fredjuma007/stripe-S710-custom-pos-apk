package community.rafiki.pay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import community.rafiki.pay.domain.DonationAmountValidator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.rafikiPayDataStore by preferencesDataStore(name = "rafikipay_settings")

class DonationConfigRepository(context: Context) {
    private val dataStore = context.rafikiPayDataStore

    val presetAmounts: Flow<List<Int>> = dataStore.data.map { prefs ->
        listOf(
            prefs[PRESET_ONE] ?: DEFAULT_PRESETS[0],
            prefs[PRESET_TWO] ?: DEFAULT_PRESETS[1],
            prefs[PRESET_THREE] ?: DEFAULT_PRESETS[2],
        ).sorted()
    }

    suspend fun savePresetAmounts(amounts: List<Int>) {
        val normalized = amounts
            .map { it.coerceIn(DonationAmountValidator.MIN_POUNDS, DonationAmountValidator.MAX_POUNDS) }
            .distinct()
            .sorted()
            .take(3)
            .let { values ->
                values + DEFAULT_PRESETS.drop(values.size)
            }

        dataStore.edit { prefs ->
            prefs[PRESET_ONE] = normalized[0]
            prefs[PRESET_TWO] = normalized[1]
            prefs[PRESET_THREE] = normalized[2]
        }
    }

    suspend fun resetDefaults() {
        savePresetAmounts(DEFAULT_PRESETS)
    }

    companion object {
        val DEFAULT_PRESETS = listOf(5, 10, 15)
        private val PRESET_ONE = intPreferencesKey("preset_one")
        private val PRESET_TWO = intPreferencesKey("preset_two")
        private val PRESET_THREE = intPreferencesKey("preset_three")
    }
}
