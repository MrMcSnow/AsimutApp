package com.asimut.sync

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.asimut.core.model.PassPayload
import com.asimut.core.model.encodeToBytes
import com.asimut.core.sync.CardSyncContract
import com.asimut.core.util.BarcodeUtil
import com.asimut.models.StudentCard
import com.asimut.util.StudentCardRenderer
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream

class WearSync(private val context: Context) {

    private val dataClient = Wearable.getDataClient(context.applicationContext)

    suspend fun pushCard(type: String, id: String, payload: ByteArray, image: ByteArray?) {
        retryWithBackoff("push", type, id) {
            val imageAsset = image?.let(Asset::createFromBytes)

            val request = PutDataMapRequest.create("${CardSyncContract.PATH_BASE}/$type/$id").apply {
                dataMap.putByteArray(CardSyncContract.KEY_PAYLOAD, payload)
                imageAsset?.let { asset ->
                    dataMap.putAsset(CardSyncContract.KEY_IMAGE, asset)
                }
                dataMap.putLong(CardSyncContract.KEY_TIMESTAMP, System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()

            dataClient.putDataItem(request).await()
        }
    }

    suspend fun pushCard(cardPayload: CardPayload) =
        pushCard(cardPayload.type, cardPayload.id, cardPayload.payloadBytes, cardPayload.imageBytes)

    suspend fun deleteCard(type: String, id: String) {
        val uri = Uri.Builder()
            .scheme(PutDataRequest.WEAR_URI_SCHEME)
            .authority("*")
            .path("${CardSyncContract.PATH_BASE}/$type/$id")
            .build()

        retryWithBackoff("delete", type, id) {
            dataClient.deleteDataItems(uri).await()
        }
    }

    suspend fun deleteCard(cardPayload: CardPayload) = deleteCard(cardPayload.type, cardPayload.id)

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
                    showDefaultBadge = false
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
                imagePng = null,
                nfcTagId = null,
                nfcPayload = null
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
            val holderName = json.optNullableString("holderName")?.takeIf { it.isNotBlank() }
                ?: json.optNullableString("name")
                ?: ""
            val rawBalance = json.opt("balance")
            val balance = when (rawBalance) {
                is Number -> rawBalance.toDouble()
                is String -> rawBalance.toDoubleOrNull()
                else -> null
            }
            val balanceDisplay = json.optNullableString("balanceDisplay").takeIf { !it.isNullOrBlank() }
                ?: json.optNullableString("balanceText").takeIf { !it.isNullOrBlank() }
                ?: (rawBalance as? String)?.takeIf { it.isNotBlank() }
            val lastUpdated = json.optLong("lastUpdated", System.currentTimeMillis())
            val qrToken = json.optNullableString("qrToken")
            val nfcTagId = json.optNullableString("nfcTagId").takeIf { !it.isNullOrBlank() }
            val nfcPayload = json.optNullableString("nfcPayload").takeIf { !it.isNullOrBlank() }

            val payload = PassPayload.MensaCard(
                id = cardId,
                holderName = holderName,
                balance = balance,
                balanceDisplay = balanceDisplay,
                lastUpdated = lastUpdated,
                qrToken = qrToken,
                nfcTagId = nfcTagId,
                nfcPayload = nfcPayload
            )

            return CardPayload(
                type = TYPE_MENSA,
                id = cardId,
                payloadBytes = payload.encodeToBytes(),
                imageBytes = null
            )
        }
    }

    private suspend fun retryWithBackoff(action: String, type: String, id: String, block: suspend () -> Unit) {
        var delayMillis = INITIAL_BACKOFF_MS
        repeat(MAX_ATTEMPTS - 1) { attempt ->
            try {
                block()
                return
            } catch (error: Throwable) {
                if (error is CancellationException) throw error
                Log.w(TAG, "Failed to $action $type card $id on attempt ${attempt + 1}", error)
            }
            delay(delayMillis)
            delayMillis *= 2
        }
        try {
            block()
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Log.e(TAG, "Failed to $action $type card $id after $MAX_ATTEMPTS attempts", error)
            throw error
        }
    }

    companion object {
        private const val TAG = "WearSync"
        private const val MAX_ATTEMPTS = 3
        private const val INITIAL_BACKOFF_MS = 500L

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

private fun JSONObject.optNullableString(key: String): String? =
    if (has(key) && !isNull(key)) getString(key) else null
