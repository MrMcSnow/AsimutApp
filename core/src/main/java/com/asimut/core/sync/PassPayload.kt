package com.asimut.core.sync

import org.json.JSONObject

/**
 * Data container describing a pass/card that can be rendered on the watch.
 */
data class PassPayload(
    val id: String,
    val type: CardType,
    val title: String,
    val subtitle: String? = null,
    val description: String? = null,
    val fields: Map<String, String> = emptyMap(),
    val barcode: Barcode? = null,
    val updatedAtEpochMillis: Long? = null
) {
    enum class CardType {
        STUDENT_CARD,
        DEUTSCHLANDTICKET,
        MENSA_CARD,
        UNKNOWN;

        companion object {
            fun fromRaw(raw: String?): CardType = values().firstOrNull { it.name == raw } ?: UNKNOWN
        }
    }

    data class Barcode(
        val data: String,
        val format: Format = Format.QR_CODE
    ) {
        enum class Format {
            QR_CODE,
            PDF_417,
            CODE_128;

            companion object {
                fun fromRaw(raw: String?): Format = values().firstOrNull { it.name == raw } ?: QR_CODE
            }
        }
    }

    fun toJson(): String {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("type", type.name)
        obj.put("title", title)
        subtitle?.let { obj.put("subtitle", it) }
        description?.let { obj.put("description", it) }
        updatedAtEpochMillis?.let { obj.put("updatedAt", it) }
        val fieldsObj = JSONObject()
        fields.forEach { (key, value) ->
            fieldsObj.put(key, value)
        }
        obj.put("fields", fieldsObj)
        barcode?.let { barcode ->
            val barcodeObj = JSONObject()
            barcodeObj.put("data", barcode.data)
            barcodeObj.put("format", barcode.format.name)
            obj.put("barcode", barcodeObj)
        }
        return obj.toString()
    }

    companion object {
        fun fromJson(json: String): PassPayload {
            val obj = JSONObject(json)
            val id = obj.getString("id")
            val type = CardType.fromRaw(obj.optString("type"))
            val title = obj.optString("title")
            val subtitle = obj.optString("subtitle", null)
            val description = obj.optString("description", null)
            val updatedAt = if (obj.has("updatedAt")) obj.optLong("updatedAt") else null
            val fieldsObj = obj.optJSONObject("fields")
            val fields = mutableMapOf<String, String>()
            if (fieldsObj != null) {
                val keys = fieldsObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    fields[key] = fieldsObj.optString(key)
                }
            }
            val barcodeObj = obj.optJSONObject("barcode")
            val barcode = barcodeObj?.let {
                val data = it.optString("data")
                val format = Barcode.Format.fromRaw(it.optString("format"))
                if (data.isNullOrEmpty()) {
                    null
                } else {
                    Barcode(data, format)
                }
            }
            return PassPayload(
                id = id,
                type = type,
                title = title,
                subtitle = subtitle,
                description = description,
                fields = fields,
                barcode = barcode,
                updatedAtEpochMillis = updatedAt
            )
        }
    }
}
