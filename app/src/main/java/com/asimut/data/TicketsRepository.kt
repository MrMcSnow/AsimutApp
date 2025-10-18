package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
import com.asimut.models.Dticket
import org.json.JSONArray
import org.json.JSONObject

class TicketsRepository(context: Context) {

    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAllTickets(): List<Dticket> {
        val serialized = preferences.getString(KEY_TICKETS, null) ?: return emptyList()
        if (serialized.isBlank()) return emptyList()

        val array = JSONArray(serialized)
        val result = mutableListOf<Dticket>()
        for (index in 0 until array.length()) {
            val json = array.optJSONObject(index) ?: continue
            val id = json.optString(JSON_ID)
            val barcodeMessage = json.optString(JSON_BARCODE_MESSAGE)
            val barcodeFormat = json.optString(JSON_BARCODE_FORMAT)
            val pkpassPath = json.optString(JSON_PKPASS_PATH)
            if (id.isNullOrBlank() || barcodeMessage.isNullOrBlank() || pkpassPath.isNullOrBlank()) {
                continue
            }
            result.add(
                Dticket(
                    id = id,
                    title = json.optString(JSON_TITLE),
                    subtitle = json.optString(JSON_SUBTITLE).takeIf { it.isNotBlank() },
                    barcodeMessage = barcodeMessage,
                    barcodeFormat = barcodeFormat,
                    validFrom = json.optString(JSON_VALID_FROM).takeIf { it.isNotBlank() },
                    validTo = json.optString(JSON_VALID_TO).takeIf { it.isNotBlank() },
                    expirationDate = json.optString(JSON_EXPIRATION_DATE).takeIf { it.isNotBlank() },
                    holder = json.optString(JSON_HOLDER).takeIf { it.isNotBlank() },
                    pkpassLocalPath = pkpassPath
                )
            )
        }
        return result
    }

    fun getTicketById(id: String): Dticket? = getAllTickets().firstOrNull { it.id == id }

    fun addTicket(ticket: Dticket): Boolean {
        val tickets = getAllTickets().toMutableList()
        val existingIndex = tickets.indexOfFirst { it.id == ticket.id }
        if (existingIndex == -1 && tickets.size >= MAX_TICKETS) {
            return false
        }

        if (existingIndex >= 0) {
            tickets[existingIndex] = ticket
        } else {
            tickets.add(ticket)
        }
        saveTickets(tickets)
        return true
    }

    fun deleteTicketById(id: String) {
        val tickets = getAllTickets().filterNot { it.id == id }
        saveTickets(tickets)
    }

    fun countTickets(): Int = getAllTickets().size

    private fun saveTickets(tickets: List<Dticket>) {
        val array = JSONArray()
        tickets.forEach { ticket ->
            val json = JSONObject().apply {
                put(JSON_ID, ticket.id)
                put(JSON_TITLE, ticket.title)
                put(JSON_SUBTITLE, ticket.subtitle ?: "")
                put(JSON_BARCODE_MESSAGE, ticket.barcodeMessage)
                put(JSON_BARCODE_FORMAT, ticket.barcodeFormat)
                put(JSON_VALID_FROM, ticket.validFrom ?: "")
                put(JSON_VALID_TO, ticket.validTo ?: "")
                put(JSON_EXPIRATION_DATE, ticket.expirationDate ?: "")
                put(JSON_HOLDER, ticket.holder ?: "")
                put(JSON_PKPASS_PATH, ticket.pkpassLocalPath)
            }
            array.put(json)
        }
        preferences.edit().putString(KEY_TICKETS, array.toString()).apply()
    }

    companion object {
        private const val PREFS_NAME = "deutschland_tickets"
        private const val KEY_TICKETS = "tickets"
        private const val JSON_ID = "id"
        private const val JSON_TITLE = "title"
        private const val JSON_SUBTITLE = "subtitle"
        private const val JSON_BARCODE_MESSAGE = "barcodeMessage"
        private const val JSON_BARCODE_FORMAT = "barcodeFormat"
        private const val JSON_VALID_FROM = "validFrom"
        private const val JSON_VALID_TO = "validTo"
        private const val JSON_EXPIRATION_DATE = "expirationDate"
        private const val JSON_HOLDER = "holder"
        private const val JSON_PKPASS_PATH = "pkpassLocalPath"
        private const val MAX_TICKETS = 5
    }
}
