package com.asimut

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.asimut.data.DeutschlandTicketParser
import com.asimut.data.DticketRepository
import com.asimut.data.TicketsRepository
import com.asimut.util.BarcodeUtil
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PkpassImportActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(incomingIntent: Intent?) {
        val uri = incomingIntent?.let { extractUri(it) }
        if (uri == null) {
            Toast.makeText(this, R.string.deutschlandticket_import_error, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        takePersistablePermission(uri)

        lifecycleScope.launch {
            val ticketId = withContext(Dispatchers.IO) { importPkpass(uri) }
            if (ticketId != null) {
                Toast.makeText(this@PkpassImportActivity, R.string.deutschlandticket_update_success, Toast.LENGTH_LONG).show()
                navigateBackToDetail(ticketId)
            } else {
                Toast.makeText(this@PkpassImportActivity, R.string.deutschlandticket_import_error, Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private suspend fun importPkpass(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val passesDir = File(filesDir, DticketRepository.PASSES_DIRECTORY).apply { if (!exists()) mkdirs() }
            val tempFile = File.createTempFile("import_", ".pkpass", cacheDir)
            try {
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempFile).use { output ->
                        input.copyTo(output)
                    }
                } ?: throw IllegalStateException("Unable to read pkpass")

                val parserResult = DeutschlandTicketParser.parse(tempFile)
                    ?: throw IllegalStateException("Unable to parse pkpass")

                val payload = parserResult.payload
                val finalFile = File(passesDir, FINAL_PKPASS_NAME)
                if (finalFile.exists()) {
                    runCatching { finalFile.delete() }
                }

                FileOutputStream(finalFile).use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                val ticket = payload.toTicket(finalFile.absolutePath)

                val previewBitmap = BarcodeUtil.generateCode(payload.barcodeMessage, payload.barcodeFormat, size = 900)
                val previewPath = DticketRepository.savePreviewBitmap(this@PkpassImportActivity, previewBitmap)

                val ticketsRepository = TicketsRepository(this@PkpassImportActivity)
                val existingTickets = ticketsRepository.getAllTickets()
                existingTickets.forEach { existing ->
                    ticketsRepository.deleteTicketById(existing.id)
                    runCatching { File(existing.pkpassLocalPath).takeIf { it.exists() }?.delete() }
                }
                val added = ticketsRepository.addTicket(ticket)
                if (!added) {
                    throw IllegalStateException("Ticket limit reached")
                }

                DticketRepository.savePassData(
                    context = this@PkpassImportActivity,
                    payload = payload,
                    passJson = parserResult.jsonString,
                    pkpassPath = finalFile.absolutePath,
                    previewPath = previewPath
                )
                ticket.id
            } finally {
                tempFile.delete()
            }
        }.getOrElse {
            null
        }
    }

    private fun navigateBackToDetail(ticketId: String) {
        if (isTaskRoot) {
            finish()
            return
        }
        val detailIntent = Intent(this, DticketDetailActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(DticketDetailActivity.EXTRA_TICKET_ID, ticketId)
        }
        startActivity(detailIntent)
        finish()
    }

    private fun extractUri(intent: Intent): Uri? {
        intent.data?.let { return it }
        val clipUri = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
        if (clipUri != null) return clipUri
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // ignore
        }
    }

    companion object {
        private const val FINAL_PKPASS_NAME = "deutschlandticket.pkpass"
    }
}
