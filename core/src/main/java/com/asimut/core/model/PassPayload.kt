package com.asimut.core.model

import android.util.Base64
import org.json.JSONObject

sealed class PassPayload {
    abstract val id: String

    data class StudentCard(
        override val id: String,
        val firstName: String,
        val lastName: String,
        val matrikelnummer: String,
        val birthDate: String,
        val imagePng: ByteArray?
    ) : PassPayload()

    data class DeutschlandTicket(
        override val id: String,
        val holderName: String,
        val validFrom: Long,
        val validTo: Long,
        val rawBytes: ByteArray?,
        val displayQr: Boolean = true
    ) : PassPayload()

    companion object {
        fun fromJson(json: String): PassPayload = PassPayloadJson.fromJson(JSONObject(json))

        fun fromBytes(bytes: ByteArray): PassPayload =
            fromJson(bytes.toString(Charsets.UTF_8))
    }
}

object PassPayloadJson {
    private const val KEY_TYPE = "type"
    private const val TYPE_STUDENT = "student"
    private const val TYPE_DEUTSCHLAND = "deutschlandticket"

    private const val KEY_ID = "id"
    private const val KEY_FIRST_NAME = "firstName"
    private const val KEY_LAST_NAME = "lastName"
    private const val KEY_MATRIKELNUMMER = "matrikelnummer"
    private const val KEY_BIRTH_DATE = "birthDate"
    private const val KEY_IMAGE = "imagePng"

    private const val KEY_HOLDER = "holderName"
    private const val KEY_VALID_FROM = "validFrom"
    private const val KEY_VALID_TO = "validTo"
    private const val KEY_RAW_BYTES = "rawBytes"
    private const val KEY_DISPLAY_QR = "displayQr"

    fun toBytes(payload: PassPayload): ByteArray =
        toJson(payload).toString().toByteArray(Charsets.UTF_8)

    fun toJson(payload: PassPayload): JSONObject {
        val json = JSONObject()
        json.put(KEY_ID, payload.id)
        when (payload) {
            is PassPayload.StudentCard -> {
                json.put(KEY_TYPE, TYPE_STUDENT)
                json.put(KEY_FIRST_NAME, payload.firstName)
                json.put(KEY_LAST_NAME, payload.lastName)
                json.put(KEY_MATRIKELNUMMER, payload.matrikelnummer)
                json.put(KEY_BIRTH_DATE, payload.birthDate)
                payload.imagePng?.let { json.put(KEY_IMAGE, it.toBase64()) }
            }

            is PassPayload.DeutschlandTicket -> {
                json.put(KEY_TYPE, TYPE_DEUTSCHLAND)
                json.put(KEY_HOLDER, payload.holderName)
                json.put(KEY_VALID_FROM, payload.validFrom)
                json.put(KEY_VALID_TO, payload.validTo)
                payload.rawBytes?.let { json.put(KEY_RAW_BYTES, it.toBase64()) }
                json.put(KEY_DISPLAY_QR, payload.displayQr)
            }
        }
        return json
    }

    fun fromJson(json: JSONObject): PassPayload {
        val id = json.getString(KEY_ID)
        return when (json.getString(KEY_TYPE)) {
            TYPE_STUDENT -> PassPayload.StudentCard(
                id = id,
                firstName = json.optString(KEY_FIRST_NAME, ""),
                lastName = json.optString(KEY_LAST_NAME, ""),
                matrikelnummer = json.optString(KEY_MATRIKELNUMMER, ""),
                birthDate = json.optString(KEY_BIRTH_DATE, ""),
                imagePng = json.optString(KEY_IMAGE, null).fromBase64()
            )

            TYPE_DEUTSCHLAND -> PassPayload.DeutschlandTicket(
                id = id,
                holderName = json.optString(KEY_HOLDER, ""),
                validFrom = json.optLong(KEY_VALID_FROM, 0L),
                validTo = json.optLong(KEY_VALID_TO, 0L),
                rawBytes = json.optString(KEY_RAW_BYTES, null).fromBase64(),
                displayQr = json.optBoolean(KEY_DISPLAY_QR, true)
            )

            else -> throw IllegalArgumentException("Unknown pass payload type: ${json.optString(KEY_TYPE)}")
        }
    }

    fun fromBytes(bytes: ByteArray): PassPayload =
        fromJson(JSONObject(bytes.toString(Charsets.UTF_8)))

    private fun ByteArray.toBase64(): String =
        Base64.encodeToString(this, Base64.NO_WRAP)

    private fun String?.fromBase64(): ByteArray? {
        if (this.isNullOrBlank()) return null
        return try {
            Base64.decode(this, Base64.DEFAULT)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

fun PassPayload.encodeToBytes(): ByteArray = PassPayloadJson.toBytes(this)
