package com.asimut.sync

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.asimut.data.DeutschlandTicketParser
import com.asimut.models.StudentCard
import com.asimut.core.util.BarcodeUtil
import com.asimut.util.StudentCardRenderer
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataMap
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.io.ByteArrayOutputStream

object WearSync {

    private const val TAG = "WearSync"

    private const val PATH_CARDS = "/cards"
    private const val KEY_TYPE = "type"
    private const val KEY_ID = "id"
    private const val KEY_UPDATED_AT = "updatedAt"
    private const val KEY_FIRST_NAME = "firstName"
    private const val KEY_LAST_NAME = "lastName"
    private const val KEY_MATRIKELNUMMER = "matrikelnummer"
    private const val KEY_BIRTH_DATE = "birthDate"
    private const val KEY_NFC_TAG_ID = "nfcTagId"
    private const val KEY_NFC_PAYLOAD = "nfcPayload"
    private const val KEY_IS_DEFAULT = "isDefault"
    private const val KEY_STUDENT_PHOTO = "studentPhoto"
    private const val KEY_TITLE = "title"
    private const val KEY_SUBTITLE = "subtitle"
    private const val KEY_VALID_FROM = "validFrom"
    private const val KEY_VALID_TO = "validTo"
    private const val KEY_EXPIRATION = "expirationDate"
    private const val KEY_HOLDER = "holder"
    private const val KEY_BARCODE_MESSAGE = "barcodeMessage"
    private const val KEY_BARCODE_FORMAT = "barcodeFormat"
    private const val KEY_JSON_PAYLOAD = "jsonPayload"
    private const val KEY_BARCODE_BITMAP = "barcodeBitmap"

    private const val TYPE_STUDENT_CARD = "student_card"
    private const val TYPE_DEUTSCHLAND_TICKET = "deutschlandticket"
    private const val TYPE_MENSA_CARD = "mensa_card"

    sealed class PassPayload(val id: String) {
        abstract val type: String
        open val timestamp: Long = System.currentTimeMillis()

        protected abstract fun writeTo(dataMap: DataMap)
        protected open fun provideAssets(): Map<String, Asset> = emptyMap()

        fun toPutDataRequest(): PutDataRequest {
            val request = PutDataMapRequest.create("$PATH_CARDS/$id")
            val map = request.dataMap
            map.putString(KEY_ID, id)
            map.putString(KEY_TYPE, type)
            map.putLong(KEY_UPDATED_AT, timestamp)
            writeTo(map)
            provideAssets().forEach { (key, asset) -> map.putAsset(key, asset) }
            return request.asPutDataRequest().apply { setUrgent() }
        }

        data class StudentCard(
            private val cardId: String,
            private val firstName: String,
            private val lastName: String,
            private val matrikelnummer: String,
            private val birthDate: String,
            private val nfcTagId: String?,
            private val nfcPayload: String?,
            private val isDefault: Boolean,
            private val photoBytes: ByteArray?
        ) : PassPayload(cardId) {
            override val type: String = TYPE_STUDENT_CARD

            override fun writeTo(dataMap: DataMap) {
                dataMap.putString(KEY_FIRST_NAME, firstName)
                dataMap.putString(KEY_LAST_NAME, lastName)
                dataMap.putString(KEY_MATRIKELNUMMER, matrikelnummer)
                dataMap.putString(KEY_BIRTH_DATE, birthDate)
                nfcTagId?.let { dataMap.putString(KEY_NFC_TAG_ID, it) }
                nfcPayload?.let { dataMap.putString(KEY_NFC_PAYLOAD, it) }
                dataMap.putBoolean(KEY_IS_DEFAULT, isDefault)
            }

            override fun provideAssets(): Map<String, Asset> {
                val bytes = photoBytes ?: return emptyMap()
                return mapOf(KEY_STUDENT_PHOTO to Asset.createFromBytes(bytes))
            }
        }

        data class DeutschlandTicket(
            private val ticketId: String,
            private val title: String,
            private val subtitle: String?,
            private val validFrom: String?,
            private val validTo: String?,
            private val expirationDate: String?,
            private val holder: String?,
            private val barcodeMessage: String,
            private val barcodeFormat: String,
            private val jsonPayload: String,
            private val barcodeBytes: ByteArray?
        ) : PassPayload(ticketId) {
            override val type: String = TYPE_DEUTSCHLAND_TICKET

            override fun writeTo(dataMap: DataMap) {
                dataMap.putString(KEY_TITLE, title)
                subtitle?.let { dataMap.putString(KEY_SUBTITLE, it) }
                validFrom?.let { dataMap.putString(KEY_VALID_FROM, it) }
                validTo?.let { dataMap.putString(KEY_VALID_TO, it) }
                expirationDate?.let { dataMap.putString(KEY_EXPIRATION, it) }
                holder?.let { dataMap.putString(KEY_HOLDER, it) }
                dataMap.putString(KEY_BARCODE_MESSAGE, barcodeMessage)
                dataMap.putString(KEY_BARCODE_FORMAT, barcodeFormat)
                dataMap.putString(KEY_JSON_PAYLOAD, jsonPayload)
            }

            override fun provideAssets(): Map<String, Asset> {
                val bytes = barcodeBytes ?: return emptyMap()
                return mapOf(KEY_BARCODE_BITMAP to Asset.createFromBytes(bytes))
            }
        }

        data class Mensa(
            private val cardId: String,
            private val jsonPayload: String
        ) : PassPayload(cardId) {
            override val type: String = TYPE_MENSA_CARD

            override fun writeTo(dataMap: DataMap) {
                dataMap.putString(KEY_JSON_PAYLOAD, jsonPayload)
            }
        }
    }

    suspend fun pushCard(context: Context, payload: PassPayload) {
        val appContext = context.applicationContext
        val request = payload.toPutDataRequest()
        try {
            Wearable.getDataClient(appContext).putDataItem(request).await()
            Log.d(TAG, "Pushed card ${payload.id} (${payload.type}) to wear devices")
        } catch (error: Exception) {
            Log.e(TAG, "Failed to push card ${payload.id}", error)
        }
    }

    object Factory {
        fun studentCard(context: Context, card: StudentCard, isDefault: Boolean): PassPayload.StudentCard? {
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

            return PassPayload.StudentCard(
                cardId = card.id,
                firstName = card.firstName,
                lastName = card.lastName,
                matrikelnummer = card.matrikelnummer,
                birthDate = card.birthDate,
                nfcTagId = card.nfcTagId,
                nfcPayload = card.nfcPayload,
                isDefault = isDefault,
                photoBytes = photoBytes
            )
        }

        fun deutschlandTicket(
            payload: DeutschlandTicketParser.Payload,
            jsonString: String
        ): PassPayload.DeutschlandTicket? {
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

            return PassPayload.DeutschlandTicket(
                ticketId = payload.id,
                title = payload.title,
                subtitle = payload.subtitle,
                validFrom = payload.validFrom,
                validTo = payload.validTo,
                expirationDate = payload.expirationDate,
                holder = payload.holder,
                barcodeMessage = payload.barcodeMessage,
                barcodeFormat = payload.barcodeFormat,
                jsonPayload = jsonString,
                barcodeBytes = barcodeBytes
            )
        }

        fun mensa(cardId: String, json: JSONObject): PassPayload.Mensa {
            return PassPayload.Mensa(cardId = cardId, jsonPayload = json.toString())
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
