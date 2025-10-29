package com.asimut.wear.data

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.asimut.core.model.PassPayload
import com.asimut.core.model.PassPayloadJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import org.json.JSONObject

private const val STORE_FILE = "card_repository_secure"
private val Context.metadataDataStore by preferencesDataStore(name = "card_repository_metadata")

class CardRepository private constructor(private val appContext: Context) {

    data class CardEntry(
        val payload: PassPayload,
        val imageBytes: ByteArray?,
        val syncedAtEpochMillis: Long,
        val isPrimary: Boolean
    ) {
        val lastUpdatedText: String?
            get() = syncedAtEpochMillis.takeIf { it > 0L }?.let {
                val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(ZoneId.systemDefault())
                formatter.format(Instant.ofEpochMilli(it))
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences = createEncryptedPrefs(appContext)

    private val cardsState = MutableStateFlow(loadCardsInternal())
    private val primaryIdFlow: Flow<String?> = appContext.metadataDataStore.data
        .map { prefs -> prefs[PRIMARY_CARD_ID] }
        .distinctUntilChanged()

    init {
        ensurePrimaryCardExists()
    }

    val cards: Flow<List<CardEntry>> = combine(cardsState, primaryIdFlow) { cards, primaryId ->
        cards.map { entry -> entry.copy(isPrimary = entry.payload.id == primaryId) }
    }.distinctUntilChanged()

    suspend fun getPrimaryCard(): CardEntry? {
        val primaryId = primaryIdFlow.first()
        return cardsState.value.firstOrNull { it.payload.id == primaryId }
    }

    fun observeCards() = cards

    suspend fun setPrimaryCard(id: String?) {
        appContext.metadataDataStore.edit { prefs ->
            if (id == null) {
                prefs.remove(PRIMARY_CARD_ID)
            } else {
                prefs[PRIMARY_CARD_ID] = id
            }
        }
    }

    fun ensurePrimaryCardExists() {
        scope.launch {
            val primaryId = primaryIdFlow.first()
            val currentCards = cardsState.value
            if (primaryId == null && currentCards.isNotEmpty()) {
                setPrimaryCard(currentCards.first().payload.id)
            }
        }
    }

    suspend fun saveCard(payload: PassPayload, imageBytes: ByteArray?, timestamp: Long) {
        withContext(Dispatchers.IO) {
            val stored = StoredCard.fromPayload(payload, imageBytes, timestamp)
            prefs.edit {
                putString(payload.id, stored.toJson())
            }
            refreshState()
            ensurePrimaryCardExists()
        }
    }

    suspend fun removeCard(cardId: String) {
        withContext(Dispatchers.IO) {
            prefs.edit { remove(cardId) }
            refreshState()
            val primaryId = primaryIdFlow.first()
            if (primaryId == cardId) {
                val next = cardsState.value.firstOrNull()?.payload?.id
                setPrimaryCard(next)
            }
        }
    }

    fun hasDeviceSecurity(): Boolean {
        val keyguard = appContext.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
        return keyguard?.isDeviceSecure ?: false
    }

    fun refreshState() {
        cardsState.value = loadCardsInternal()
    }

    private fun loadCardsInternal(): List<CardEntry> {
        val entries = prefs.all.mapNotNull { (_, value) ->
            if (value is String) {
                StoredCard.fromJson(value)
            } else {
                null
            }
        }
        return entries
            .sortedBy { it.payload.displayName().lowercase() }
            .map { stored ->
                CardEntry(
                    payload = stored.payload,
                    imageBytes = stored.imageBytes,
                    syncedAtEpochMillis = stored.syncedAt,
                    isPrimary = false
                )
            }
    }

    companion object {
        private val PRIMARY_CARD_ID = preferencesKey<String>("primary_card_id")

        @Volatile
        private var INSTANCE: CardRepository? = null

        fun getInstance(context: Context): CardRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: CardRepository(context.applicationContext).also { INSTANCE = it }
            }
        }

        private fun createEncryptedPrefs(context: Context): SharedPreferences {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            return EncryptedSharedPreferences.create(
                context,
                STORE_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        }
    }
}

private data class StoredCard(
    val payload: PassPayload,
    val imageBytes: ByteArray?,
    val syncedAt: Long
) {
    fun toJson(): String {
        val json = JSONObject()
        json.put(KEY_PAYLOAD, PassPayloadJson.toJson(payload))
        imageBytes?.let { json.put(KEY_IMAGE, Base64.encodeToString(it, Base64.NO_WRAP)) }
        json.put(KEY_SYNCED_AT, syncedAt)
        return json.toString()
    }

    companion object {
        private const val KEY_PAYLOAD = "payload"
        private const val KEY_IMAGE = "image"
        private const val KEY_SYNCED_AT = "syncedAt"

        fun fromPayload(payload: PassPayload, imageBytes: ByteArray?, timestamp: Long): StoredCard {
            val adjustedPayload = if (payload is PassPayload.StudentCard && imageBytes != null) {
                payload.copy(imagePng = imageBytes)
            } else {
                payload
            }
            return StoredCard(adjustedPayload, imageBytes, timestamp)
        }

        fun fromJson(raw: String): StoredCard? {
            return runCatching {
                val json = JSONObject(raw)
                val payloadJson = json.getJSONObject(KEY_PAYLOAD)
                val payload = PassPayloadJson.fromJson(payloadJson)
                val imageBase64 = json.optString(KEY_IMAGE, null)
                val imageBytes = if (imageBase64.isNullOrBlank()) {
                    null
                } else {
                    runCatching { Base64.decode(imageBase64, Base64.DEFAULT) }.getOrNull()
                }
                val syncedAt = json.optLong(KEY_SYNCED_AT, 0L)
                val adjustedPayload = if (payload is PassPayload.StudentCard && imageBytes != null) {
                    payload.copy(imagePng = imageBytes)
                } else {
                    payload
                }
                StoredCard(adjustedPayload, imageBytes, syncedAt)
            }.getOrNull()
        }
    }
}

private fun PassPayload.displayName(): String = when (this) {
    is PassPayload.StudentCard -> listOf(firstName, lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
        .ifBlank { "Student Card" }

    is PassPayload.DeutschlandTicket -> holderName.ifBlank { "Deutschlandticket" }

    is PassPayload.MensaCard -> holderName.ifBlank { "Mensa Card" }
}
