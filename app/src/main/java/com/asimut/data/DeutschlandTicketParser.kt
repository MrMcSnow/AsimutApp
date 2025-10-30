package com.asimut.data

import com.asimut.core.model.PassPayload
import com.asimut.models.Dticket
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.text.Charsets

object DeutschlandTicketParser {

    data class Result(
        val payload: PassPayload.DeutschlandTicket,
        val barcodeMessage: String,
        val barcodeFormat: String,
        val title: String,
        val subtitle: String?,
        val validFromText: String?,
        val validToText: String?,
        val expirationDateText: String?,
        val holderName: String?,
        val json: JSONObject,
        val jsonString: String
    )

    fun parse(passFile: File): Result? {
        val jsonPair = readPassJson(passFile) ?: return null
        val json = jsonPair.first
        val jsonString = jsonPair.second

        val parsed = buildPayload(json)
            ?: return null

        return Result(
            payload = parsed.payload,
            barcodeMessage = parsed.barcodeMessage,
            barcodeFormat = parsed.barcodeFormat,
            title = parsed.title,
            subtitle = parsed.subtitle,
            validFromText = parsed.validFromText,
            validToText = parsed.validToText,
            expirationDateText = parsed.expirationDateText,
            holderName = parsed.holderName,
            json = json,
            jsonString = jsonString
        )
    }

    data class ParsedTicket(
        val payload: PassPayload.DeutschlandTicket,
        val barcodeMessage: String,
        val barcodeFormat: String,
        val title: String,
        val subtitle: String?,
        val validFromText: String?,
        val validToText: String?,
        val expirationDateText: String?,
        val holderName: String?
    )

    fun buildPayload(json: JSONObject, fallbackId: String? = null): ParsedTicket? {
        val serialNumber = json.optString("serialNumber").takeIf { it.isNotBlank() }
            ?: fallbackId
            ?: UUID.randomUUID().toString()
        val barcodeMessage = json.findBarcodeMessage() ?: return null
        val barcodeFormat = json.findBarcodeFormat()

        val organizationName = json.optString("organizationName").takeIf { it.isNotBlank() }
        val description = json.optString("description").takeIf { it.isNotBlank() }

        val holder = json.optString("ticketHolder").takeIf { it.isNotBlank() }
            ?: json.optString("personName").takeIf { it.isNotBlank() }
            ?: json.findFieldValue(listOf("b1"), listOf("name"))

        val validityField = json.findFieldValue(listOf("b4"), listOf("gültig", "gueltig", "valid"))
        val (rangeStart, rangeEnd) = parseValidityRange(validityField)

        val validFromDate = parseDate(json.optString("validFrom")) ?: parseDate(rangeStart)
        val validToDate = parseDate(json.optString("validTo"))
            ?: parseDate(json.optString("validUntil"))
            ?: parseDate(rangeEnd)
        val expirationDate = parseDate(json.optString("expirationDate"))
            ?: json.findFieldValue(emptyList(), listOf("ablauf", "expire"))?.let { parseDate(it) }

        val title = when {
            validFromDate != null && validToDate != null -> "${validFromDate.display} – ${validToDate.display}"
            validFromDate != null -> validFromDate.display
            validToDate != null -> validToDate.display
            else -> organizationName ?: description ?: "Deutschlandticket"
        }

        val subtitle = description ?: organizationName

        val payload = PassPayload.DeutschlandTicket(
            id = serialNumber,
            holderName = holder ?: "",
            validFrom = validFromDate?.epochMillis ?: 0L,
            validTo = validToDate?.epochMillis ?: 0L,
            rawBytes = barcodeMessage.toByteArray(Charsets.UTF_8),
            displayQr = true
        )

        return ParsedTicket(
            payload = payload,
            barcodeMessage = barcodeMessage,
            barcodeFormat = barcodeFormat,
            title = title,
            subtitle = subtitle,
            validFromText = validFromDate?.display,
            validToText = validToDate?.display,
            expirationDateText = expirationDate?.display,
            holderName = holder
        )
    }

    private fun readPassJson(passFile: File): Pair<JSONObject, String>? {
        var jsonString: String? = null
        ZipInputStream(FileInputStream(passFile)).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.equals("pass.json", ignoreCase = true)) {
                    val outputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (zipStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    jsonString = outputStream.toString(Charsets.UTF_8.name())
                    outputStream.close()
                    zipStream.closeEntry()
                    break
                }
                entry = zipStream.nextEntry
            }
        }
        return try {
            jsonString?.let { JSONObject(it) }?.let { it to jsonString!! }
        } catch (_: JSONException) {
            null
        }
    }

    private fun JSONObject.findBarcodeMessage(): String? {
        val barcodeObject = optJSONObject("barcode")
        val barcodesArray = optJSONArray("barcodes")
        val directMessage = barcodeObject?.optString("message")
        if (!directMessage.isNullOrBlank()) return directMessage
        if (barcodesArray != null) {
            for (index in 0 until barcodesArray.length()) {
                val candidate = barcodesArray.optJSONObject(index)?.optString("message")
                if (!candidate.isNullOrBlank()) return candidate
            }
        }
        return null
    }

    private fun JSONObject.findBarcodeFormat(): String {
        val barcodeObject = optJSONObject("barcode")
        val barcodesArray = optJSONArray("barcodes")
        val directFormat = barcodeObject?.optString("format")
        if (!directFormat.isNullOrBlank()) return directFormat
        if (barcodesArray != null) {
            for (index in 0 until barcodesArray.length()) {
                val candidate = barcodesArray.optJSONObject(index)?.optString("format")
                if (!candidate.isNullOrBlank()) return candidate
            }
        }
        return "PKBarcodeFormatAztec"
    }

    private fun JSONObject.findFieldValue(keys: List<String>, labels: List<String>): String? {
        val generic = optJSONObject("generic") ?: return null
        val arrays = listOf(
            generic.optJSONArray("primaryFields"),
            generic.optJSONArray("secondaryFields"),
            generic.optJSONArray("auxiliaryFields"),
            generic.optJSONArray("backFields"),
            generic.optJSONArray("additionalInfoFields")
        )
        arrays.forEach { array ->
            array?.let {
                for (index in 0 until it.length()) {
                    val obj = it.optJSONObject(index) ?: continue
                    val key = obj.optString("key").lowercase(Locale.getDefault())
                    val label = obj.optString("label").lowercase(Locale.getDefault())
                    val value = obj.optString("value")
                    if (value.isNullOrBlank()) continue
                    if (keys.any { keyCandidate -> key == keyCandidate.lowercase(Locale.getDefault()) }) {
                        return value
                    }
                    if (labels.any { labelCandidate -> label.contains(labelCandidate.lowercase(Locale.getDefault())) }) {
                        return value
                    }
                }
            }
        }
        return null
    }

    private fun parseValidityRange(value: String?): Pair<String?, String?> {
        if (value.isNullOrBlank()) return null to null
        val separators = listOf("-", "–", "—")
        separators.forEach { separator ->
            if (value.contains(separator)) {
                val parts = value.split(separator)
                if (parts.size >= 2) {
                    return parts[0].trim() to parts[1].trim()
                }
            }
        }
        return value.trim() to null
    }

    private data class ParsedDate(val display: String, val epochMillis: Long)

    private fun parseDate(raw: String?): ParsedDate? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        DATE_PATTERNS.forEach { pattern ->
            try {
                val parser = SimpleDateFormat(pattern, Locale.getDefault())
                parser.isLenient = true
                val date = parser.parse(trimmed)
                if (date != null) {
                    val display = SimpleDateFormat(OUTPUT_DATE_PATTERN, Locale.getDefault()).format(date)
                    return ParsedDate(display = display, epochMillis = date.time)
                }
            } catch (_: ParseException) {
                // try next pattern
            }
        }
        return ParsedDate(display = trimmed, epochMillis = 0L)
    }

    private const val BUFFER_SIZE = 4096
    private const val OUTPUT_DATE_PATTERN = "dd.MM.yyyy"
    private val DATE_PATTERNS = listOf(
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mmXXX",
        "yyyy-MM-dd",
        "dd.MM.yyyy",
        "dd.MM.yy"
    )
}

fun DeutschlandTicketParser.Result.toTicket(pkpassPath: String) = Dticket(
    id = payload.id,
    title = title,
    subtitle = subtitle,
    barcodeMessage = barcodeMessage,
    barcodeFormat = barcodeFormat,
    validFrom = validFromText,
    validTo = validToText,
    expirationDate = expirationDateText,
    holder = holderName,
    pkpassLocalPath = pkpassPath
)
