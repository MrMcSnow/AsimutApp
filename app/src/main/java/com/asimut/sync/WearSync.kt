package com.asimut.sync

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.asimut.core.sync.CardSyncContract
import com.asimut.core.sync.PassPayload
import com.asimut.core.util.BarcodeUtil
import com.asimut.data.DeutschlandTicketParser
import com.asimut.models.StudentCard
import com.asimut.util.StudentCardRenderer
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object WearSync {

    private const val TAG = "WearSync"

    data class SyncPayload(
        val payload: PassPayload,
        val imageBytes: ByteArray? = null
    ) {
        fun toPutDataRequest(): PutDataRequest {
            val request = PutDataMapRequest.create("${CardSyncContract.PATH_BASE}/${payload.id}")
            val map = request.dataMap
            map.putString(CardSyncContract.KEY_PAYLOAD_JSON, payload.toJson())
            imageBytes?.let { bytes ->
                map.putAsset(CardSyncContract.KEY_IMAGE_ASSET, Asset.createFromBytes(bytes))
            }
            return request.asPutDataRequest().apply { setUrgent() }
        }
    }

    suspend fun pushCard(context: Context, payload: SyncPayload) {
        val appContext = context.applicationContext
        val request = payload.toPutDataRequest()
        try {
            Wearable.getDataClient(appContext).putDataItem(request).await()
            Log.d(TAG, "Pushed card ${payload.payload.id} (${payload.payload.type}) to wear devices")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to push card ${payload.payload.id}", error)
        }
    }

    object Factory {
        fun studentCard(context: Context, card: StudentCard, isDefault: Boolean): SyncPayload? {
            val renderer = StudentCardRenderer(context)
            val bitmap = runCatching {
                renderer.render(
                    firstName = card.firstName,
                    lastName = card.lastName,
                    matrikelnummer = card.matrikelnummer,
                    birthDate = card.birthDate,
                    showDefaultBadge = isDefault,
                    showNfcBadge = !card.nfcTagId.isNullOrBlank()
                )
            }.getOrElse { error ->
                Log.e(TAG, "Unable to render student card image for ${card.id}", error)
                return null
            }

            val photoBytes = bitmap.toPngByteArray()
            if (photoBytes == null) {
                Log.e(TAG, "Failed to encode student card image for ${card.id}")
                return null
            }

            val title = listOf(card.firstName, card.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { "Student Card" }
            val subtitle = card.matrikelnummer.takeIf { it.isNotBlank() }
            val fields = buildMap {
                put("firstName", card.firstName)
                put("lastName", card.lastName)
                put("matrikelnummer", card.matrikelnummer)
                put("birthDate", card.birthDate)
                card.nfcTagId?.takeIf { it.isNotBlank() }?.let { put("nfcTagId", it) }
                card.nfcPayload?.takeIf { it.isNotBlank() }?.let { put("nfcPayload", it) }
                if (isDefault) put("status", "Primary card")
            }

            val payload = PassPayload(
                id = card.id,
                type = PassPayload.CardType.STUDENT_CARD,
                title = title,
                subtitle = subtitle,
                fields = fields,
                updatedAtEpochMillis = System.currentTimeMillis()
            )

            return SyncPayload(payload = payload, imageBytes = photoBytes)
        }

        fun deutschlandTicket(
            payload: DeutschlandTicketParser.Payload,
            jsonString: String
        ): SyncPayload? {
            val barcodeBitmap = runCatching {
                BarcodeUtil.generateCode(payload.barcodeMessage, payload.barcodeFormat, size = 900)
            }.getOrElse { error ->
                Log.e(TAG, "Unable to generate barcode bitmap for Deutschlandticket ${payload.id}", error)
                return null
            }

            val barcodeBytes = barcodeBitmap.toPngByteArray()
            if (barcodeBytes == null) {
                Log.e(TAG, "Failed to encode barcode bitmap for Deutschlandticket ${payload.id}")
                return null
            }

            val json = runCatching { JSONObject(jsonString) }.getOrNull()
            val fields = buildMap {
                payload.holder?.takeIf { it.isNotBlank() }?.let { put("holder", it) }
                payload.validFrom?.takeIf { it.isNotBlank() }?.let { put("validFrom", it) }
                payload.validTo?.takeIf { it.isNotBlank() }?.let {
                    put("validTo", it)
                    put("validUntil", it)
                }
                payload.expirationDate?.takeIf { it.isNotBlank() }?.let { put("expirationDate", it) }
                json?.optString("subscriptionId")?.takeIf { it.isNotBlank() }?.let {
                    put("subscriptionId", it)
                }
            }

            val passPayload = PassPayload(
                id = payload.id,
                type = PassPayload.CardType.DEUTSCHLANDTICKET,
                title = payload.title,
                subtitle = payload.subtitle,
                fields = fields,
                barcode = PassPayload.Barcode(
                    data = payload.barcodeMessage,
                    format = mapBarcodeFormat(payload.barcodeFormat)
                ),
                updatedAtEpochMillis = System.currentTimeMillis()
            )

            return SyncPayload(payload = passPayload, imageBytes = barcodeBytes)
        }

        fun mensa(cardId: String, json: JSONObject): SyncPayload {
            val title = json.optString("title").ifBlank { "Mensa Card" }
            val subtitle = json.optString("subtitle").takeIf { it.isNotBlank() }
            val description = json.optString("description").takeIf { it.isNotBlank() }
            val fields = buildMap {
                json.optString("balance").takeIf { it.isNotBlank() }?.let { put("balance", it) }
                json.optString("cardNumber").takeIf { it.isNotBlank() }?.let { put("cardNumber", it) }
                json.optString("lastUpdated").takeIf { it.isNotBlank() }?.let { put("lastUpdated", it) }
                json.optString("lastTransaction").takeIf { it.isNotBlank() }?.let { put("lastTransaction", it) }
            }

            val payload = PassPayload(
                id = cardId,
                type = PassPayload.CardType.MENSA_CARD,
                title = title,
                subtitle = subtitle,
                description = description,
                fields = fields,
                updatedAtEpochMillis = System.currentTimeMillis()
            )

            return SyncPayload(payload = payload)
        }
    }

    private fun mapBarcodeFormat(format: String?): PassPayload.Barcode.Format {
        val normalized = format?.uppercase() ?: return PassPayload.Barcode.Format.QR_CODE
        return when {
            normalized.contains("PDF417") || normalized.contains("PDF_417") -> PassPayload.Barcode.Format.PDF_417
            normalized.contains("128") -> PassPayload.Barcode.Format.CODE_128
            else -> PassPayload.Barcode.Format.QR_CODE
        }
    }

    private fun Bitmap.toPngByteArray(): ByteArray? {
        return try {
            ByteArrayOutputStream().use { output ->
                if (!compress(Bitmap.CompressFormat.PNG, 100, output)) {
                    null
                } else {
                    output.toByteArray()
                }
            }
        } catch (error: Exception) {
            Log.e(TAG, "Failed to compress bitmap", error)
            null
        } finally {
            recycle()
        }
    }
}
