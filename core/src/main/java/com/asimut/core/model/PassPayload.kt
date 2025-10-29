package com.asimut.core.model

import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject

/**
 * Shared representation of a pass/card payload that can be synchronised between the phone and
 * wearable applications.
 */
sealed class PassPayload {

    abstract val id: String
    abstract val type: CardType
    abstract val title: String
    abstract val subtitle: String?
    abstract val description: String?
    abstract val updatedAtEpochMillis: Long?
    abstract val fields: Map<String, String>
    abstract val barcode: Barcode?
    abstract val imagePng: ByteArray?
    abstract val rawBytes: ByteArray?
    abstract val qrToken: String?
    abstract val displayQr: Boolean

    data class StudentCard(
        override val id: String,
        val firstName: String,
        val lastName: String,
        val matrikelnummer: String,
        val birthDate: String,
        val status: String? = null,
        val nfcTagId: String? = null,
        val nfcPayload: String? = null,
        val isDefault: Boolean = false,
        override val title: String = listOfNotNull(firstName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { matrikelnummer },
        override val subtitle: String? = status,
        override val description: String? = null,
        override val updatedAtEpochMillis: Long? = null,
        override val fields: Map<String, String> = emptyMap(),
        override val barcode: Barcode? = null,
        override val imagePng: ByteArray? = null,
        override val rawBytes: ByteArray? = null,
        override val qrToken: String? = null,
        override val displayQr: Boolean = false
    ) : PassPayload() {
        override val type: CardType = CardType.STUDENT_CARD
    }

    data class DeutschlandTicket(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        val barcodeMessage: String,
        val barcodeFormat: String,
        val validFrom: String?,
        val validTo: String?,
        val expirationDate: String?,
        val holder: String?,
        override val description: String? = null,
        override val updatedAtEpochMillis: Long? = null,
        override val fields: Map<String, String> = emptyMap(),
        override val imagePng: ByteArray? = null,
        override val rawBytes: ByteArray? = null,
        override val qrToken: String? = null,
        override val displayQr: Boolean = true,
        override val barcode: Barcode = Barcode(
            data = barcodeMessage,
            format = Barcode.Format.fromRaw(barcodeFormat)
        )
    ) : PassPayload() {
        override val type: CardType = CardType.DEUTSCHLANDTICKET
    }

    data class MensaCard(
        override val id: String,
        val cardNumber: String,
        val balance: String?,
        val lastUpdated: String?,
        val lastTransaction: String? = null,
        override val title: String = "Mensa Card",
        override val subtitle: String? = balance,
        override val description: String? = null,
        override val updatedAtEpochMillis: Long? = null,
        override val fields: Map<String, String> = emptyMap(),
        override val barcode: Barcode? = null,
        override val imagePng: ByteArray? = null,
        override val rawBytes: ByteArray? = null,
        override val qrToken: String? = null,
        override val displayQr: Boolean = false
    ) : PassPayload() {
        override val type: CardType = CardType.MENSA_CARD
    }

    data class Generic(
        override val id: String,
        override val title: String,
        override val subtitle: String?,
        override val description: String?,
        override val updatedAtEpochMillis: Long?,
        override val fields: Map<String, String>,
        override val barcode: Barcode?,
        override val imagePng: ByteArray?,
        override val rawBytes: ByteArray?,
        override val qrToken: String?,
        override val displayQr: Boolean
    ) : PassPayload() {
        override val type: CardType = CardType.UNKNOWN
    }

    data class Barcode(
        val data: String,
        val format: Format = Format.QR_CODE,
        val imagePng: ByteArray? = null
    ) {
        enum class Format {
            QR_CODE,
            PDF_417,
            CODE_128,
            AZTEC,
            UNKNOWN;

            companion object {
                fun fromRaw(raw: String?): Format {
                    if (raw.isNullOrBlank()) return QR_CODE
                    return values().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
                }
            }
        }
    }

    enum class CardType {
        STUDENT_CARD,
        DEUTSCHLANDTICKET,
        MENSA_CARD,
        UNKNOWN
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put(KEY_TYPE, type.name)
        root.put(KEY_ID, id)
        root.put(KEY_TITLE, title)
        subtitle?.let { root.put(KEY_SUBTITLE, it) }
        description?.let { root.put(KEY_DESCRIPTION, it) }
        updatedAtEpochMillis?.let { root.put(KEY_UPDATED_AT, it) }
        root.put(KEY_DISPLAY_QR, displayQr)
        qrToken?.let { root.put(KEY_QR_TOKEN, it) }
        imagePng?.let { root.put(KEY_IMAGE_PNG, it.encodeBase64()) }
        rawBytes?.let { root.put(KEY_RAW_BYTES, it.encodeBase64()) }

        val fieldsJson = JSONObject()
        fields.forEach { (key, value) -> fieldsJson.put(key, value) }
        root.put(KEY_FIELDS, fieldsJson)

        barcode?.let { barcode ->
            val barcodeJson = JSONObject()
            barcodeJson.put(KEY_BARCODE_DATA, barcode.data)
            barcodeJson.put(KEY_BARCODE_FORMAT, barcode.format.name)
            barcode.imagePng?.let { barcodeJson.put(KEY_BARCODE_IMAGE, it.encodeBase64()) }
            root.put(KEY_BARCODE, barcodeJson)
        }

        when (this) {
            is StudentCard -> {
                root.put(KEY_FIRST_NAME, firstName)
                root.put(KEY_LAST_NAME, lastName)
                root.put(KEY_MATRIKELNUMMER, matrikelnummer)
                root.put(KEY_BIRTH_DATE, birthDate)
                status?.let { root.put(KEY_STATUS, it) }
                nfcTagId?.let { root.put(KEY_NFC_TAG_ID, it) }
                nfcPayload?.let { root.put(KEY_NFC_PAYLOAD, it) }
                root.put(KEY_IS_DEFAULT, isDefault)
            }

            is DeutschlandTicket -> {
                root.put(KEY_BARCODE_MESSAGE, barcodeMessage)
                root.put(KEY_BARCODE_FORMAT, barcodeFormat)
                validFrom?.let { root.put(KEY_VALID_FROM, it) }
                validTo?.let { root.put(KEY_VALID_TO, it) }
                expirationDate?.let { root.put(KEY_EXPIRATION_DATE, it) }
                holder?.let { root.put(KEY_HOLDER, it) }
            }

            is MensaCard -> {
                root.put(KEY_CARD_NUMBER, cardNumber)
                balance?.let { root.put(KEY_BALANCE, it) }
                lastUpdated?.let { root.put(KEY_LAST_UPDATED, it) }
                lastTransaction?.let { root.put(KEY_LAST_TRANSACTION, it) }
            }

            is Generic -> {
                // no-op, generic payload already persisted through base fields
            }
        }

        return root.toString()
    }

    companion object {
        private const val KEY_TYPE = "type"
        private const val KEY_ID = "id"
        private const val KEY_TITLE = "title"
        private const val KEY_SUBTITLE = "subtitle"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_UPDATED_AT = "updatedAt"
        private const val KEY_FIELDS = "fields"
        private const val KEY_BARCODE = "barcode"
        private const val KEY_BARCODE_DATA = "data"
        private const val KEY_BARCODE_FORMAT = "format"
        private const val KEY_BARCODE_IMAGE = "imagePng"
        private const val KEY_IMAGE_PNG = "imagePng"
        private const val KEY_RAW_BYTES = "rawBytes"
        private const val KEY_QR_TOKEN = "qrToken"
        private const val KEY_DISPLAY_QR = "displayQr"

        private const val KEY_FIRST_NAME = "firstName"
        private const val KEY_LAST_NAME = "lastName"
        private const val KEY_MATRIKELNUMMER = "matrikelnummer"
        private const val KEY_BIRTH_DATE = "birthDate"
        private const val KEY_STATUS = "status"
        private const val KEY_NFC_TAG_ID = "nfcTagId"
        private const val KEY_NFC_PAYLOAD = "nfcPayload"
        private const val KEY_IS_DEFAULT = "isDefault"

        private const val KEY_BARCODE_MESSAGE = "barcodeMessage"
        private const val KEY_VALID_FROM = "validFrom"
        private const val KEY_VALID_TO = "validTo"
        private const val KEY_EXPIRATION_DATE = "expirationDate"
        private const val KEY_HOLDER = "holder"

        private const val KEY_CARD_NUMBER = "cardNumber"
        private const val KEY_BALANCE = "balance"
        private const val KEY_LAST_UPDATED = "lastUpdated"
        private const val KEY_LAST_TRANSACTION = "lastTransaction"

        fun fromJson(json: String): PassPayload {
            val obj = JSONObject(json)
            val type = CardType.values().firstOrNull {
                it.name.equals(obj.optString(KEY_TYPE), ignoreCase = true)
            } ?: CardType.UNKNOWN

            val id = obj.getString(KEY_ID)
            val title = obj.optString(KEY_TITLE)
            val subtitle = obj.optString(KEY_SUBTITLE, null)
            val description = obj.optString(KEY_DESCRIPTION, null)
            val updatedAt = obj.optLong(KEY_UPDATED_AT, -1L).takeIf { it > 0 }
            val displayQr = obj.optBoolean(KEY_DISPLAY_QR, false)
            val qrToken = obj.optString(KEY_QR_TOKEN, null)
            val imageBytes = obj.optString(KEY_IMAGE_PNG, null).decodeBase64()
            val rawBytes = obj.optString(KEY_RAW_BYTES, null).decodeBase64()

            val fields = obj.optJSONObject(KEY_FIELDS)?.let { jsonObject ->
                val result = mutableMapOf<String, String>()
                val keys = jsonObject.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    result[key] = jsonObject.optString(key)
                }
                result
            } ?: emptyMap()

            val barcode = obj.optJSONObject(KEY_BARCODE)?.let { barcodeObj ->
                val data = barcodeObj.optString(KEY_BARCODE_DATA)
                if (data.isNullOrBlank()) {
                    null
                } else {
                    val format = Barcode.Format.fromRaw(barcodeObj.optString(KEY_BARCODE_FORMAT))
                    val barcodeImage = barcodeObj.optString(KEY_BARCODE_IMAGE, null).decodeBase64()
                    Barcode(data = data, format = format, imagePng = barcodeImage)
                }
            }

            return when (type) {
                CardType.STUDENT_CARD -> StudentCard(
                    id = id,
                    firstName = obj.optString(KEY_FIRST_NAME),
                    lastName = obj.optString(KEY_LAST_NAME),
                    matrikelnummer = obj.optString(KEY_MATRIKELNUMMER),
                    birthDate = obj.optString(KEY_BIRTH_DATE),
                    status = obj.optString(KEY_STATUS, null),
                    nfcTagId = obj.optString(KEY_NFC_TAG_ID, null),
                    nfcPayload = obj.optString(KEY_NFC_PAYLOAD, null),
                    isDefault = obj.optBoolean(KEY_IS_DEFAULT, false),
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    updatedAtEpochMillis = updatedAt,
                    fields = fields,
                    barcode = barcode,
                    imagePng = imageBytes,
                    rawBytes = rawBytes,
                    qrToken = qrToken,
                    displayQr = displayQr
                )

                CardType.DEUTSCHLANDTICKET -> DeutschlandTicket(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    barcodeMessage = obj.optString(KEY_BARCODE_MESSAGE),
                    barcodeFormat = obj.optString(KEY_BARCODE_FORMAT),
                    validFrom = obj.optString(KEY_VALID_FROM, null),
                    validTo = obj.optString(KEY_VALID_TO, null),
                    expirationDate = obj.optString(KEY_EXPIRATION_DATE, null),
                    holder = obj.optString(KEY_HOLDER, null),
                    description = description,
                    updatedAtEpochMillis = updatedAt,
                    fields = fields,
                    imagePng = imageBytes,
                    rawBytes = rawBytes,
                    qrToken = qrToken,
                    displayQr = displayQr,
                    barcode = barcode ?: Barcode(
                        data = obj.optString(KEY_BARCODE_MESSAGE),
                        format = Barcode.Format.fromRaw(obj.optString(KEY_BARCODE_FORMAT))
                    )
                )

                CardType.MENSA_CARD -> MensaCard(
                    id = id,
                    cardNumber = obj.optString(KEY_CARD_NUMBER),
                    balance = obj.optString(KEY_BALANCE, null),
                    lastUpdated = obj.optString(KEY_LAST_UPDATED, null),
                    lastTransaction = obj.optString(KEY_LAST_TRANSACTION, null),
                    title = title.ifBlank { "Mensa Card" },
                    subtitle = subtitle,
                    description = description,
                    updatedAtEpochMillis = updatedAt,
                    fields = fields,
                    barcode = barcode,
                    imagePng = imageBytes,
                    rawBytes = rawBytes,
                    qrToken = qrToken,
                    displayQr = displayQr
                )

                CardType.UNKNOWN -> Generic(
                    id = id,
                    title = title,
                    subtitle = subtitle,
                    description = description,
                    updatedAtEpochMillis = updatedAt,
                    fields = fields,
                    barcode = barcode,
                    imagePng = imageBytes,
                    rawBytes = rawBytes,
                    qrToken = qrToken,
                    displayQr = displayQr
                )
            }
        }

        fun fromJsonArray(jsonArray: JSONArray): List<PassPayload> {
            val result = mutableListOf<PassPayload>()
            for (index in 0 until jsonArray.length()) {
                val json = jsonArray.optString(index)
                if (!json.isNullOrBlank()) {
                    runCatching { fromJson(json) }
                        .onSuccess { result += it }
                }
            }
            return result
        }

        private fun ByteArray.encodeBase64(): String =
            Base64.encodeToString(this, Base64.NO_WRAP)

        private fun String?.decodeBase64(): ByteArray? {
            if (this.isNullOrBlank()) return null
            return runCatching { Base64.decode(this, Base64.DEFAULT) }.getOrNull()
        }
    }
}
