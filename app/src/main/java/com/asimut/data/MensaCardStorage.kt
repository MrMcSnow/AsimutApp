package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.asimut.sync.WearSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONException
import org.json.JSONObject

class MensaCardStorage(context: Context) {

    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val wearScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun saveCard(cardId: String, json: JSONObject) {
        val payload = WearSync.Builder.mensaCard(cardId, json)
        if (payload == null) {
            Log.w(TAG, "Skipping Mensa card $cardId: payload serialization failed")
            val storedId = clearStoredState() ?: cardId.takeIf { it.isNotBlank() }
            storedId?.let { id ->
                wearScope.launch { WearSync.from(appContext).deleteCard(WearSync.TYPE_MENSA, id) }
            }
            return
        }

        preferences.edit()
            .putString(KEY_CARD_ID, cardId)
            .putString(KEY_CARD_JSON, json.toString())
            .apply()
        syncCard(payload)
    }

    fun saveCard(cardId: String, rawJson: String) {
        val json = runCatching { JSONObject(rawJson) }.getOrElse { error ->
            Log.e(TAG, "Failed to parse Mensa card JSON", error)
            return
        }
        saveCard(cardId, json)
    }

    fun clear() {
        val clearedId = clearStoredState()
        clearedId?.let { id ->
            wearScope.launch { WearSync.from(appContext).deleteCard(WearSync.TYPE_MENSA, id) }
        }
    }

    fun getCardId(): String? =
        preferences.getString(KEY_CARD_ID, null)?.takeIf { it.isNotBlank() }

    fun getCardJson(): JSONObject? {
        val raw = preferences.getString(KEY_CARD_JSON, null)?.takeIf { it.isNotBlank() }
            ?: return null
        return try {
            JSONObject(raw)
        } catch (error: JSONException) {
            Log.e(TAG, "Invalid Mensa card JSON", error)
            val clearedId = clearStoredState()
            clearedId?.let { id ->
                wearScope.launch { WearSync.from(appContext).deleteCard(WearSync.TYPE_MENSA, id) }
            }
            null
        }
    }

    fun latestWearPayload(): WearSync.CardPayload? {
        val cardId = getCardId() ?: return null
        val json = getCardJson() ?: return null
        return WearSync.Builder.mensaCard(cardId, json)
    }

    private fun syncCard(payload: WearSync.CardPayload) {
        wearScope.launch {
            WearSync.from(appContext).pushCard(payload)
        }
    }

    companion object {
        private const val TAG = "MensaCardStorage"
        private const val PREFS_NAME = "mensa_card_storage"
        private const val KEY_CARD_ID = "card_id"
        private const val KEY_CARD_JSON = "card_json"
    }

    private fun clearStoredState(): String? {
        val cardId = preferences.getString(KEY_CARD_ID, null)?.takeIf { it.isNotBlank() }
        preferences.edit().clear().apply()
        return cardId
    }
}
