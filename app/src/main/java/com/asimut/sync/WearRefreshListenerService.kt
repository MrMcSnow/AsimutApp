package com.asimut.sync

import android.content.Context
import android.util.Log
import com.asimut.core.sync.CardSyncContract
import com.asimut.data.DticketRepository
import com.asimut.data.StudentCardStorage
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class WearRefreshListenerService : WearableListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onMessageReceived(messageEvent: MessageEvent) {
        when (messageEvent.path) {
            REFRESH_PATH -> {
                scope.launch { resendLatestCards(applicationContext) }
            }

            else -> super.onMessageReceived(messageEvent)
        }
    }

    private suspend fun resendLatestCards(context: Context) {
        val appContext = context.applicationContext
        val wearSync = WearSync.from(appContext)
        val studentStorage = StudentCardStorage(appContext)
        val studentCards = studentStorage.getCards()
        studentCards.forEach { card ->
            val payload = WearSync.Builder.studentCard(appContext, card)
            if (payload == null) {
                Log.w(TAG, "Skipping student card ${card.id} during wear refresh: payload serialization failed")
            } else {
                wearSync.pushCard(payload)
            }
        }

        val ticketPayload = DticketRepository.latestWearPayload(appContext)
        if (ticketPayload != null) {
            wearSync.pushCard(ticketPayload)
        } else {
            Log.d(TAG, "No Deutschlandticket payload available during wear refresh")
        }

        Log.d(TAG, "Wear refresh completed: ${studentCards.size} manual cards synced")
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WearRefreshService"
        const val REFRESH_PATH = CardSyncContract.PATH_REFRESH_REQUEST
    }
}
