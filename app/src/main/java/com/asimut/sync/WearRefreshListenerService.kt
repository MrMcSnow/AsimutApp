package com.asimut.sync

import android.content.Context
import android.util.Log
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
        if (messageEvent.path == REFRESH_PATH) {
            scope.launch { resendLatestCards(applicationContext) }
        } else {
            super.onMessageReceived(messageEvent)
        }
    }

    private suspend fun resendLatestCards(context: Context) {
        val appContext = context.applicationContext
        val studentStorage = StudentCardStorage(appContext)
        val studentCards = studentStorage.getCards()
        val defaultId = studentStorage.getDefaultCardId()
        studentCards.forEach { card ->
            val payload = WearSync.Factory.studentCard(appContext, card, card.id == defaultId)
            if (payload == null) {
                Log.w(TAG, "Skipping student card ${card.id} during wear refresh: payload serialization failed")
            } else {
                WearSync.pushCard(appContext, payload)
            }
        }

        val ticketPayload = DticketRepository.latestWearPayload(appContext)
        if (ticketPayload != null) {
            WearSync.pushCard(appContext, ticketPayload)
        } else {
            Log.d(TAG, "No Deutschlandticket payload available during wear refresh")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    companion object {
        private const val TAG = "WearRefreshService"
        const val REFRESH_PATH = "/cards/refresh"
    }
}
