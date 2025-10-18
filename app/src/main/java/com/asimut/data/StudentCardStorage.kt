package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
import com.asimut.models.StudentCard
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

class StudentCardStorage(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCards(): List<StudentCard> {
        val serialized = preferences.getString(KEY_STUDENT_CARDS, null) ?: return emptyList()
        if (serialized.isBlank()) return emptyList()

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
