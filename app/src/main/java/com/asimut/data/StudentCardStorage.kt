package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
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
                if (id.isNullOrBlank() || matrikelnummer.isNullOrBlank()) continue
                result.add(
                    StudentCard(
                        id = id,
                        firstName = firstName,
                        lastName = lastName,
                        matrikelnummer = matrikelnummer,
                        birthDate = birthDate
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
        val currentCards = getCards()
        val updatedCards = currentCards.filterNot { it.id == id }
        if (updatedCards.size == currentCards.size) {
            return
        }

        saveCards(updatedCards)
        wearScope.launch {
            WearSync.from(appContext).deleteCard(WearSync.TYPE_STUDENT, id)
        }
    }

    fun getCardById(id: String): StudentCard? {
        return getCards().firstOrNull { it.id == id }
    }

    fun clearAll() {
        val existingIds = getCards().map { it.id }
        saveCards(emptyList())
        wearScope.launch {
            val sync = WearSync.from(appContext)
            existingIds.forEach { id ->
                sync.deleteCard(WearSync.TYPE_STUDENT, id)
            }
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
            }
            array.put(json)
        }
        preferences.edit().putString(KEY_STUDENT_CARDS, array.toString()).apply()
    }

    private fun syncCard(card: StudentCard) {
        wearScope.launch {
            val wearSync = WearSync.from(appContext)
            val payload = WearSync.Builder.studentCard(appContext, card)
            payload?.let { wearSync.pushCard(it) }
        }
    }

    companion object {
        private const val PREFS_NAME = "student_cards"
        private const val KEY_STUDENT_CARDS = "cards"
        private const val JSON_ID = "id"
        private const val JSON_FIRST_NAME = "firstName"
        private const val JSON_LAST_NAME = "lastName"
        private const val JSON_MATRIKELNUMMER = "matrikelnummer"
        private const val JSON_BIRTH_DATE = "birthDate"
    }
}
