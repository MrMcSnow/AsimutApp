package com.asimut.wear.data

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.BitmapFactory
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.asimut.wear.model.PassPayload
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
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

private const val STORE_FILE = "card_repository_secure"
private const val IMAGE_DIR = "cards"
private val Context.metadataDataStore by preferencesDataStore(name = "card_repository_metadata")

class CardRepository private constructor(private val appContext: Context) {

    data class CardEntry(
        val payload: PassPayload,
        val imageBytes: ByteArray?,
        val isPrimary: Boolean
    ) {
        val lastUpdatedText: String?
            get() = payload.updatedAtEpochMillis?.let {
                val formatter = DateTimeFormatter.ofPattern("MMM d, HH:mm")
                    .withZone(ZoneId.systemDefault())
                formatter.format(Instant.ofEpochMilli(it))
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences = createEncryptedPrefs(appContext)
    private val imageDirectory: File = File(appContext.filesDir, IMAGE_DIR).apply { mkdirs() }

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

    suspend fun saveCard(payload: PassPayload, imageBytes: ByteArray?) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putString(payload.id, payload.toJson())
            }
            if (imageBytes != null) {
                saveImage(payload.id, imageBytes)
            } else {
                deleteImage(payload.id)
            }
            refreshState()
            ensurePrimaryCardExists()
        }
    }

    suspend fun removeCard(cardId: String) {
        withContext(Dispatchers.IO) {
            prefs.edit { remove(cardId) }
            deleteImage(cardId)
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

    fun getCardImage(cardId: String): ByteArray? {
        val imageFile = File(imageDirectory, "$cardId.png")
        if (!imageFile.exists()) return null
        return try {
            imageFile.readBytes()
        } catch (io: IOException) {
            null
        }
    }

    private fun loadCardsInternal(): List<CardEntry> {
        val all = prefs.all
        val entries = mutableListOf<CardEntry>()
        for ((key, value) in all) {
            if (value is String) {
                runCatching {
                    val payload = PassPayload.fromJson(value)
                    val imageBytes = getCardImage(key)
                    entries += CardEntry(payload, imageBytes, isPrimary = false)
                }
            }
        }
        return entries.sortedBy { it.payload.title.lowercase() }
    }

    private fun saveImage(cardId: String, bytes: ByteArray) {
        val imageFile = File(imageDirectory, "$cardId.png")
        FileOutputStream(imageFile).use { stream ->
            stream.write(bytes)
        }
    }

    private fun deleteImage(cardId: String) {
        val imageFile = File(imageDirectory, "$cardId.png")
        if (imageFile.exists()) {
            imageFile.delete()
        }
    }

    fun decodeBitmap(cardId: String) = getCardImage(cardId)?.let { bytes ->
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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
