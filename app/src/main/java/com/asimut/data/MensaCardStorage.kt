package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.asimut.sync.WearSync
import com.asimut.models.StudentCard
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
        val (enrichedJson, enriched) = ensureNfcData(json)
        val payload = WearSync.Builder.mensaCard(cardId, enrichedJson)
        if (payload == null) {
            handleInvalidPayload(cardId, "payload serialization failed")
            return
        }

        preferences.edit()
            .putString(KEY_CARD_ID, cardId)
            .putString(KEY_CARD_JSON, enrichedJson.toString())
            .apply()
        if (enriched) {
            Log.d(TAG, "Enriched Mensa card $cardId with student NFC data before syncing")
        }
        syncCard(payload)
    }

    fun saveCard(cardId: String, rawJson: String) {
        val json = runCatching { JSONObject(rawJson) }.getOrElse { error ->
            Log.e(TAG, "Failed to parse Mensa card JSON", error)
            handleInvalidPayload(cardId, "invalid JSON payload")
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
        val (enrichedJson, enriched) = ensureNfcData(json)
        val payload = WearSync.Builder.mensaCard(cardId, enrichedJson)
        if (payload == null) {
            handleInvalidPayload(cardId, "payload serialization failed during refresh")
            if (enriched) {
                preferences.edit().putString(KEY_CARD_JSON, enrichedJson.toString()).apply()
            }
        }
        if (payload != null && enriched) {
            preferences.edit().putString(KEY_CARD_JSON, enrichedJson.toString()).apply()
            Log.d(TAG, "Stored Mensa card $cardId after enriching NFC data from student card")
        }
        return payload
    }

    fun updateNfcDataFromStudentCard(tagId: String?, payload: String?) {
        val normalizedTag = tagId?.takeIf { it.isNotBlank() } ?: return
        val cardId = getCardId() ?: return
        val currentJson = getCardJson() ?: return
        val existingTag = currentJson.optNullableString(JSON_NFC_TAG_ID)?.takeIf { it.isNotBlank() }
        val existingPayload = currentJson.optNullableString(JSON_NFC_PAYLOAD)?.takeIf { it.isNotBlank() }
        val normalizedPayload = payload?.takeIf { it.isNotBlank() }

        if (existingTag == normalizedTag && existingPayload == normalizedPayload) {
            return
        }

        val updatedJson = JSONObject(currentJson.toString()).apply {
            put(JSON_NFC_TAG_ID, normalizedTag)
            if (normalizedPayload != null) {
                put(JSON_NFC_PAYLOAD, normalizedPayload)
            } else {
                remove(JSON_NFC_PAYLOAD)
            }
            put(JSON_LAST_UPDATED, System.currentTimeMillis())
        }

        val wearPayload = WearSync.Builder.mensaCard(cardId, updatedJson)
        if (wearPayload == null) {
            handleInvalidPayload(cardId, "payload serialization failed after student NFC update")
            return
        }

        preferences.edit().putString(KEY_CARD_JSON, updatedJson.toString()).apply()
        Log.d(TAG, "Updated Mensa card $cardId with NFC data from student card")
        syncCard(wearPayload)
    }

    private fun syncCard(payload: WearSync.CardPayload) {
        wearScope.launch {
            WearSync.from(appContext).pushCard(payload)
        }
    }

    private fun ensureNfcData(json: JSONObject): Pair<JSONObject, Boolean> {
        val existingTag = json.optNullableString(JSON_NFC_TAG_ID)?.takeIf { it.isNotBlank() }
        val existingPayload = json.optNullableString(JSON_NFC_PAYLOAD)?.takeIf { it.isNotBlank() }
        if (!existingTag.isNullOrBlank() && !existingPayload.isNullOrBlank()) {
            return json to false
        }

        val fallbackCard = resolveFallbackStudentCard()
        val fallbackTag = fallbackCard?.nfcTagId?.takeIf { it.isNotBlank() }
        val fallbackPayload = fallbackCard?.nfcPayload?.takeIf { it.isNotBlank() }

        if (fallbackTag.isNullOrBlank()) {
            return json to false
        }

        var changed = false
        val enrichedJson = JSONObject(json.toString())
        if (existingTag.isNullOrBlank()) {
            enrichedJson.put(JSON_NFC_TAG_ID, fallbackTag)
            changed = true
        }
        if (existingPayload.isNullOrBlank() && !fallbackPayload.isNullOrBlank()) {
            enrichedJson.put(JSON_NFC_PAYLOAD, fallbackPayload)
            changed = true
        }

        if (changed) {
            enrichedJson.put(JSON_LAST_UPDATED, System.currentTimeMillis())
        }

        return (if (changed) enrichedJson else json) to changed
    }

    private fun resolveFallbackStudentCard(): StudentCard? {
        val studentStorage = StudentCardStorage(appContext)
        val defaultId = studentStorage.getDefaultCardId()
        val defaultCard = defaultId?.let { studentStorage.getCardById(it) }
        if (defaultCard?.nfcTagId?.isNotBlank() == true) {
            return defaultCard
        }

        return studentStorage.getCards().firstOrNull { !it.nfcTagId.isNullOrBlank() }
    }

    private fun handleInvalidPayload(cardId: String, reason: String) {
        Log.w(TAG, "Skipping Mensa card $cardId: $reason")
        val storedId = clearStoredState() ?: cardId.takeIf { it.isNotBlank() }
        storedId?.let { id ->
            wearScope.launch { WearSync.from(appContext).deleteCard(WearSync.TYPE_MENSA, id) }
        }
    }

    companion object {
        private const val TAG = "MensaCardStorage"
        private const val PREFS_NAME = "mensa_card_storage"
        private const val KEY_CARD_ID = "card_id"
        private const val KEY_CARD_JSON = "card_json"
        private const val JSON_NFC_TAG_ID = "nfcTagId"
        private const val JSON_NFC_PAYLOAD = "nfcPayload"
        private const val JSON_LAST_UPDATED = "lastUpdated"
    }

    private fun clearStoredState(): String? {
        val cardId = preferences.getString(KEY_CARD_ID, null)?.takeIf { it.isNotBlank() }
        preferences.edit().clear().apply()
        return cardId
    }
}

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
