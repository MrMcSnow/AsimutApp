package com.asimut.sync

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.asimut.core.model.PassPayload
import com.asimut.core.model.encodeToBytes
import com.asimut.core.sync.CardSyncContract
import com.asimut.core.util.BarcodeUtil
import com.asimut.models.StudentCard
import com.asimut.util.StudentCardRenderer
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class WearSync(private val context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun pushCard(type: String, id: String, payload: ByteArray, image: ByteArray?) {
        val request = PutDataMapRequest.create("${CardSyncContract.PATH_BASE}/$type/$id").apply {
            dataMap.putByteArray(CardSyncContract.KEY_PAYLOAD, payload)
            image?.let { dataMap.putByteArray(CardSyncContract.KEY_IMAGE, it) }
            dataMap.putLong(CardSyncContract.KEY_TIMESTAMP, System.currentTimeMillis())
        }.asPutDataRequest().setUrgent()

        dataClient.putDataItem(request).await()
    }

    suspend fun pushCard(cardPayload: CardPayload) =
        pushCard(cardPayload.type, cardPayload.id, cardPayload.payloadBytes, cardPayload.imageBytes)

    data class CardPayload(
        val type: String,
        val id: String,
        val payloadBytes: ByteArray,
        val imageBytes: ByteArray?
    )

    object Builder {
        fun studentCard(context: Context, card: StudentCard): CardPayload? {
            val renderer = StudentCardRenderer(context)
            val bitmap = runCatching {
                renderer.render(
                    firstName = card.firstName,
                    lastName = card.lastName,
                    matrikelnummer = card.matrikelnummer,
                    birthDate = card.birthDate,
                    showDefaultBadge = false,
                    showNfcBadge = !card.nfcTagId.isNullOrBlank()
                )
            }.getOrElse { error ->
                Log.e(TAG, "Unable to render student card ${card.id}", error)
                return null
            }

            val imageBytes = bitmap.toPngByteArray() ?: run {
                Log.e(TAG, "Failed to encode student card bitmap for ${card.id}")
                return null
            }

            val payload = PassPayload.StudentCard(
                id = card.id,
                firstName = card.firstName,
                lastName = card.lastName,
                matrikelnummer = card.matrikelnummer,
                birthDate = card.birthDate,
                imagePng = null
            )

            return CardPayload(
                type = TYPE_STUDENT,
                id = card.id,
                payloadBytes = payload.encodeToBytes(),
                imageBytes = imageBytes
            )
        }

        fun deutschlandTicket(
            payload: PassPayload.DeutschlandTicket,
            barcodeMessage: String,
            barcodeFormat: String
        ): CardPayload? {
            val bitmap = runCatching {
                BarcodeUtil.generateCode(barcodeMessage, barcodeFormat, size = 900)
            }.getOrElse { error ->
                Log.e(TAG, "Unable to generate Deutschlandticket barcode for ${payload.id}", error)
                return null
            }

            val imageBytes = bitmap.toPngByteArray() ?: run {
                Log.e(TAG, "Failed to encode barcode bitmap for Deutschlandticket ${payload.id}")
                return null
            }

            val payloadBytes = payload.encodeToBytes()
            return CardPayload(
                type = TYPE_DEUTSCHLAND,
                id = payload.id,
                payloadBytes = payloadBytes,
                imageBytes = imageBytes
            )
        }

        fun mensaCard(cardId: String, json: JSONObject): CardPayload? {
            val holderName = json.optString("holderName")
                .ifBlank { json.optString("name") }
            val balance = json.optDouble("balance", Double.NaN)
            val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            val qrToken = json.optString("qrToken", null)

            if (balance.isNaN()) {
                Log.w(TAG, "Skipping Mensa card $cardId: missing balance field")
                return null
            }

            val payload = PassPayload.MensaCard(
                id = cardId,
                holderName = holderName,
                balance = balance,
                lastUpdated = lastUpdated,
                qrToken = qrToken
            )

            return CardPayload(
                type = TYPE_MENSA,
                id = cardId,
                payloadBytes = payload.encodeToBytes(),
                imageBytes = null
            )
        }
    }

    companion object {
        private const val TAG = "WearSync"

        const val TYPE_STUDENT = "student"
        const val TYPE_DEUTSCHLAND = "deutschlandticket"
        const val TYPE_MENSA = "mensa"

        fun from(context: Context): WearSync = WearSync(context.applicationContext)
    }
}

private fun Bitmap.toPngByteArray(): ByteArray? {
    return runCatching {
        ByteArrayOutputStream().use { stream ->
            if (!compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                null
            } else {
                stream.toByteArray()
            }
        }
    }.getOrNull()
}
