package com.asimut.wear.data

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.preferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.asimut.core.model.PassPayload
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
private val Context.metadataDataStore by preferencesDataStore(name = "card_repository_metadata")

class CardRepository private constructor(private val appContext: Context) {

    data class CardEntry(
        val payload: PassPayload,
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

    suspend fun saveCard(payload: PassPayload) {
        withContext(Dispatchers.IO) {
            prefs.edit {
                putString(payload.id, payload.toJson())
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
        val all = prefs.all
        val entries = mutableListOf<CardEntry>()
        for ((key, value) in all) {
            if (value is String) {
                runCatching {
                    val payload = PassPayload.fromJson(value)
                    entries += CardEntry(payload, isPrimary = false)
                }
            }
        }
        return entries.sortedBy { it.payload.title.lowercase() }
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
