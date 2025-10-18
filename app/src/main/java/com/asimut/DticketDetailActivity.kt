package com.asimut

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import com.asimut.data.DticketRepository
import com.asimut.data.TicketsRepository
import com.asimut.models.Dticket
import com.asimut.util.BarcodeUtil
import com.google.android.material.button.MaterialButton
import java.io.File

class DticketDetailActivity : AppCompatActivity() {

    private lateinit var ticketsRepository: TicketsRepository

    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var barcodeImage: ImageView
    private lateinit var validityText: TextView
    private lateinit var expirationText: TextView
    private lateinit var holderText: TextView
    private lateinit var openButton: MaterialButton
    private lateinit var updateButton: MaterialButton

    private var currentTicketId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dticket_detail)

        ticketsRepository = TicketsRepository(this)

        titleText = findViewById(R.id.detail_title)
        subtitleText = findViewById(R.id.detail_subtitle)
        barcodeImage = findViewById(R.id.detail_barcode)
        validityText = findViewById(R.id.detail_validity)
        expirationText = findViewById(R.id.detail_expiration)
        holderText = findViewById(R.id.detail_holder)
        openButton = findViewById(R.id.detail_open_original)
        updateButton = findViewById(R.id.update_ticket_button)

        currentTicketId = intent.getStringExtra(EXTRA_TICKET_ID)

        openButton.setOnClickListener { openCurrentTicket() }

        updateButton.setOnClickListener { openUpdatePortal() }
    }

    override fun onResume() {
        super.onResume()
        refreshTicket()
    }

    private fun refreshTicket() {
        val ticketFromId = currentTicketId?.let { ticketsRepository.getTicketById(it) }
        val ticket = ticketFromId ?: ticketsRepository.getAllTickets().firstOrNull()
        if (ticket == null) {
            showTicketMissing()
            finish()
            return
        }
        currentTicketId = ticket.id
        bindTicket(ticket)
    }

    private fun bindTicket(ticket: Dticket) {
        titleText.text = ticket.title.ifBlank { getString(R.string.deutschlandticket_title_fallback) }
        subtitleText.isVisible = !ticket.subtitle.isNullOrBlank()
        subtitleText.text = ticket.subtitle

        val validityParts = mutableListOf<String>()
        val validFrom = ticket.validFrom
        val validTo = ticket.validTo
        if (!validFrom.isNullOrBlank() && !validTo.isNullOrBlank()) {
            validityParts += getString(R.string.deutschlandticket_validity_range_format, validFrom, validTo)
        } else if (!validFrom.isNullOrBlank()) {
            validityParts += getString(R.string.deutschlandticket_valid_from_format, validFrom)
        } else if (!validTo.isNullOrBlank()) {
            validityParts += getString(R.string.deutschlandticket_valid_to_format, validTo)
        }
        if (validityParts.isNotEmpty()) {
            validityText.isVisible = true
            validityText.text = validityParts.joinToString("\n")
        } else {
            validityText.isVisible = false
        }

        if (!ticket.expirationDate.isNullOrBlank()) {
            expirationText.isVisible = true
            expirationText.text = getString(R.string.deutschlandticket_expiration_format, ticket.expirationDate)
        } else {
            expirationText.isVisible = false
        }

        if (!ticket.holder.isNullOrBlank()) {
            holderText.isVisible = true
            holderText.text = getString(R.string.deutschlandticket_holder_format, ticket.holder)
        } else {
            holderText.isVisible = false
        }

        val previewUri = DticketRepository.previewUri(this)
        if (previewUri != null) {
            barcodeImage.setImageURI(null)
            barcodeImage.setImageURI(previewUri)
        } else {
            val barcodeBitmap = runCatching {
                BarcodeUtil.generateCode(ticket.barcodeMessage, ticket.barcodeFormat, size = 900)
            }.getOrNull()
            if (barcodeBitmap != null) {
                barcodeImage.setImageBitmap(barcodeBitmap)
            } else {
                barcodeImage.setImageDrawable(null)
                Toast.makeText(this, R.string.deutschlandticket_import_error, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun openCurrentTicket() {
        val ticket = currentTicketId?.let { ticketsRepository.getTicketById(it) }
            ?: ticketsRepository.getAllTickets().firstOrNull()
        if (ticket != null) {
            openOriginal(ticket)
        } else {
            showTicketMissing()
        }
    }

    private fun openOriginal(ticket: Dticket) {
        val file = DticketRepository.getPkpassPath(this)?.let { File(it) } ?: File(ticket.pkpassLocalPath)
        if (!file.exists()) {
            Toast.makeText(this, R.string.deutschlandticket_file_missing, Toast.LENGTH_LONG).show()
            return
        }

        val authority = "${packageName}.fileprovider"
        val uri: Uri = FileProvider.getUriForFile(this, authority, file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, MIME_PKPASS)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val resolved = intent.resolveActivity(packageManager)
        if (resolved != null) {
            startActivity(intent)
        } else {
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = MIME_PKPASS
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.deutschlandticket_share_title)))
        }
    }

    private fun openUpdatePortal() {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(UPDATE_URL))
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.deutschlandticket_update_launch_error, Toast.LENGTH_LONG).show()
        }
    }

    private fun showTicketMissing() {
        Toast.makeText(this, R.string.deutschlandticket_missing, Toast.LENGTH_LONG).show()
    }

    companion object {
        private const val EXTRA_TICKET_ID = "extra_ticket_id"
        private const val MIME_PKPASS = "application/vnd.apple.pkpass"
        private const val UPDATE_URL = "https://abo.ride-ticketing.de/app/login?partnerId=de61d47ca0d1a6b3b8a6c8502c89e09e"

        fun createIntent(context: Context, ticketId: String): Intent {
            return Intent(context, DticketDetailActivity::class.java).apply {
                putExtra(EXTRA_TICKET_ID, ticketId)
            }
        }
    }
}
