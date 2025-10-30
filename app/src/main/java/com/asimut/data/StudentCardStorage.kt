package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.asimut.models.StudentCard
import com.asimut.sync.WearSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class StudentCardStorage(context: Context) {

    private val appContext = context.applicationContext
    private val preferences: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val wearScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun getCards(): List<StudentCard> {
        val serialized = preferences.getString(KEY_STUDENT_CARDS, null) ?: return emptyList()
        if (serialized.isBlank()) return emptyList()

        try {
            val array = JSONArray(serialized)
            val result = mutableListOf<StudentCard>()
            for (index in 0 until array.length()) {
                val json = array.optJSONObject(index) ?: continue
                val id = json.optString(JSON_ID)
                val firstName = json.optString(JSON_FIRST_NAME)
                val lastName = json.optString(JSON_LAST_NAME)
                val matrikelnummer = json.optString(JSON_MATRIKELNUMMER)
                val birthDate = json.optString(JSON_BIRTH_DATE)
                val nfcTagId = json.optString(JSON_NFC_TAG_ID).takeIf { !it.isNullOrBlank() }
                val nfcPayload = json.optString(JSON_NFC_PAYLOAD).takeIf { !it.isNullOrBlank() }
                if (id.isNullOrBlank() || matrikelnummer.isNullOrBlank()) continue
                result.add(
                    StudentCard(
                        id = id,
                        firstName = firstName,
                        lastName = lastName,
                        matrikelnummer = matrikelnummer,
                        birthDate = birthDate,
                        nfcTagId = nfcTagId,
                        nfcPayload = nfcPayload
                    )
                )
            }
            return result
        } catch (error: JSONException) {
            preferences.edit().remove(KEY_STUDENT_CARDS).apply()
            return emptyList()
        }
    }

    fun addCard(card: StudentCard) {
        val cards = getCards().toMutableList()
        val index = cards.indexOfFirst { it.id == card.id }
        if (index >= 0) {
            cards[index] = card
        } else {
            cards.add(card)
        }
        saveCards(cards)
        if (getDefaultCardId().isNullOrBlank()) {
            setDefaultCardId(card.id)
        }
        syncCard(card)
    }

    fun createCard(firstName: String, lastName: String, matrikelnummer: String, birthDate: String): StudentCard {
        return StudentCard(
            id = UUID.randomUUID().toString(),
            firstName = firstName,
            lastName = lastName,
            matrikelnummer = matrikelnummer,
            birthDate = birthDate
        )
    }

    fun deleteCardById(id: String) {
        val cards = getCards().filterNot { it.id == id }
        saveCards(cards)
        if (getDefaultCardId() == id) {
            setDefaultCardId(cards.firstOrNull()?.id)
        }
        wearScope.launch {
            WearSync.from(appContext).deleteCard(WearSync.TYPE_STUDENT, id)
        }
    }

    fun getCardById(id: String): StudentCard? {
        return getCards().firstOrNull { it.id == id }
    }

    fun updateCardNfcData(cardId: String, tagId: String, payload: String?) {
        val cards = getCards().map { card ->
            if (card.id == cardId) {
                card.copy(nfcTagId = tagId, nfcPayload = payload ?: card.nfcPayload)
            } else {
                card
            }
        }
        saveCards(cards)
        cards.firstOrNull { it.id == cardId }?.let { syncCard(it) }
    }

    fun getDefaultCardId(): String? = preferences.getString(KEY_DEFAULT_CARD_ID, null)

    fun setDefaultCardId(cardId: String?) {
        val previousDefault = getDefaultCardId()
        preferences.edit().putString(KEY_DEFAULT_CARD_ID, cardId).apply()
        previousDefault?.takeIf { it != cardId }?.let { id ->
            getCardById(id)?.let { syncCard(it) }
        }
        cardId?.let { id ->
            getCardById(id)?.let { syncCard(it) }
        }
    }

    fun ensureDefaultCardId(preferredCardId: String? = null) {
        val current = getDefaultCardId()
        if (!current.isNullOrBlank()) return
        val cards = getCards()
        val fallback = preferredCardId ?: cards.firstOrNull()?.id
        if (!fallback.isNullOrBlank()) {
            setDefaultCardId(fallback)
        }
    }

    private fun saveCards(cards: List<StudentCard>) {
        val array = JSONArray()
        cards.forEach { card ->
            val json = JSONObject().apply {
                put(JSON_ID, card.id)
                put(JSON_FIRST_NAME, card.firstName)
                put(JSON_LAST_NAME, card.lastName)
                put(JSON_MATRIKELNUMMER, card.matrikelnummer)
                put(JSON_BIRTH_DATE, card.birthDate)
                card.nfcTagId?.let { put(JSON_NFC_TAG_ID, it) }
                card.nfcPayload?.let { put(JSON_NFC_PAYLOAD, it) }
            }
            array.put(json)
        }
        preferences.edit().putString(KEY_STUDENT_CARDS, array.toString()).apply()
    }

    private fun syncCard(card: StudentCard) {
        wearScope.launch {
            val wearSync = WearSync.from(appContext)
            val payload = WearSync.Builder.studentCard(appContext, card)
            if (payload == null) {
                Log.w(TAG, "Skipping wear sync for student card ${card.id}: payload serialization failed")
                return@launch
            }
            wearSync.pushCard(payload)
        }
    }

    companion object {
        private const val TAG = "StudentCardStorage"
        private const val PREFS_NAME = "student_cards"
        private const val KEY_STUDENT_CARDS = "cards"
        private const val KEY_DEFAULT_CARD_ID = "default_card_id"
        private const val JSON_ID = "id"
        private const val JSON_FIRST_NAME = "firstName"
        private const val JSON_LAST_NAME = "lastName"
        private const val JSON_MATRIKELNUMMER = "matrikelnummer"
        private const val JSON_BIRTH_DATE = "birthDate"
        private const val JSON_NFC_TAG_ID = "nfcTagId"
        private const val JSON_NFC_PAYLOAD = "nfcPayload"
    }
}
