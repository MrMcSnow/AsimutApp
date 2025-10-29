package com.asimut.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import com.asimut.data.DeutschlandTicketParser.Payload
import com.asimut.sync.WearSync
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

object DticketRepository {

    private const val TAG = "DticketRepository"
    private const val PREFS_NAME = "dticket_repository"
    private const val KEY_PASS_JSON = "dticket_json"
    private const val KEY_UPDATED_AT = "dticket_updated_at"
    private const val KEY_PKPASS_PATH = "dticket_pkpass_path"
    private const val KEY_PREVIEW_PATH = "dticket_preview_path"
    private const val KEY_TICKET_ID = "dticket_ticket_id"

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun savePassData(
        context: Context,
        payload: Payload,
        passJson: String,
        pkpassPath: String,
        previewPath: String?
    ) {
        preferences(context).edit().apply {
            putString(KEY_TICKET_ID, payload.id)
            putString(KEY_PASS_JSON, passJson)
            putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            putString(KEY_PKPASS_PATH, pkpassPath)
            putString(KEY_PREVIEW_PATH, previewPath ?: "")
        }.apply()

        scheduleWearSync(context, payload, passJson)
    }

    fun getTicketId(context: Context): String? =
        preferences(context).getString(KEY_TICKET_ID, null)?.takeIf { it.isNotBlank() }

    fun getPassJson(context: Context): JSONObject? {
        val raw = preferences(context).getString(KEY_PASS_JSON, null) ?: return null
        return runCatching { JSONObject(raw) }.getOrNull()
    }

    fun getPassJsonString(context: Context): String? =
        preferences(context).getString(KEY_PASS_JSON, null)?.takeIf { it.isNotBlank() }

    fun getUpdatedAt(context: Context): Long? {
        val value = preferences(context).getLong(KEY_UPDATED_AT, -1L)
        return if (value <= 0L) null else value
    }

    fun getPkpassPath(context: Context): String? =
        preferences(context).getString(KEY_PKPASS_PATH, null)?.takeIf { it.isNotBlank() }

    fun getPreviewPath(context: Context): String? =
        preferences(context).getString(KEY_PREVIEW_PATH, null)?.takeIf { it.isNotBlank() }

    fun savePreviewBitmap(context: Context, bitmap: Bitmap): String? {
        return runCatching {
            val directory = File(context.filesDir, PASSES_DIRECTORY).apply { if (!exists()) mkdirs() }
            val file = File(directory, PREVIEW_FILE_NAME)
            FileOutputStream(file).use { output ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
                output.flush()
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun preferences(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun previewUri(context: Context): Uri? {
        val path = getPreviewPath(context) ?: return null
        val file = File(path)
        if (!file.exists()) return null
        return Uri.fromFile(file)
    }

    fun clear(context: Context) {
        val previewPath = getPreviewPath(context)
        if (previewPath != null) {
            runCatching { File(previewPath).takeIf { it.exists() }?.delete() }
        }
        preferences(context).edit().clear().apply()
    }

    fun latestWearPayload(context: Context): WearSync.PassPayload.DeutschlandTicket? {
        val jsonString = getPassJsonString(context) ?: return null
        val json = runCatching { JSONObject(jsonString) }.getOrElse { error ->
            Log.e(TAG, "Failed to parse Deutschlandticket JSON", error)
            return null
        }
        val payload = DeutschlandTicketParser.buildPayload(json, getTicketId(context))
            ?: return null
        return WearSync.Factory.deutschlandTicket(payload, jsonString)
    }

    private fun scheduleWearSync(context: Context, payload: Payload, passJson: String) {
        val appContext = context.applicationContext
        syncScope.launch {
            val wearPayload = WearSync.Factory.deutschlandTicket(payload, passJson)
            if (wearPayload == null) {
                Log.w(TAG, "Skipping wear sync for Deutschlandticket ${payload.id}: payload serialization failed")
                return@launch
            }
            WearSync.pushCard(appContext, wearPayload)
        }
    }

    const val PASSES_DIRECTORY = "passes"
    private const val PREVIEW_FILE_NAME = "deutschlandticket.png"
}
