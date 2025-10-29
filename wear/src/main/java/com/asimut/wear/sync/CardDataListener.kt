package com.asimut.wear.sync

import android.net.Uri
import com.asimut.wear.data.CardRepository
import com.asimut.wear.model.PassPayload
import com.google.android.gms.wearable.Asset
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataMapItem
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream

class CardDataListener : WearableListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val repository: CardRepository by lazy { CardRepository.getInstance(applicationContext) }

    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.use { events ->
            for (event in events) {
                val path = event.dataItem.uri.path ?: continue
                if (!path.startsWith(CardSyncContract.PATH_BASE)) continue
                when (event.type) {
                    DataEvent.TYPE_CHANGED -> handleChanged(event)
                    DataEvent.TYPE_DELETED -> handleDeleted(event.dataItem.uri)
                }
            }
        }
    }

    private fun handleChanged(event: DataEvent) {
        val dataItem = event.dataItem
        serviceScope.launch {
            val dataMapItem = DataMapItem.fromDataItem(dataItem)
            val payloadJson = dataMapItem.dataMap.getString(CardSyncContract.KEY_PAYLOAD_JSON)
                ?: return@launch
            val payload = runCatching { PassPayload.fromJson(payloadJson) }.getOrNull() ?: return@launch
            val asset = dataMapItem.dataMap.getAsset(CardSyncContract.KEY_IMAGE_ASSET)
            val imageBytes = asset?.let { loadAssetBytes(it) }
            repository.saveCard(payload, imageBytes)
        }
    }

    private fun handleDeleted(uri: Uri) {
        val cardId = uri.lastPathSegment ?: return
        serviceScope.launch {
            repository.removeCard(cardId)
        }
    }

    private suspend fun loadAssetBytes(asset: Asset): ByteArray? {
        val dataClient = Wearable.getDataClient(this)
        val fd = dataClient.getFdForAsset(asset).await() ?: return null
        return fd.use { assetFd ->
            BufferedInputStream(assetFd.inputStream).use { input ->
                val buffer = ByteArrayOutputStream()
                val temp = ByteArray(16 * 1024)
                while (true) {
                    val read = input.read(temp)
                    if (read == -1) break
                    buffer.write(temp, 0, read)
                }
                buffer.toByteArray()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
