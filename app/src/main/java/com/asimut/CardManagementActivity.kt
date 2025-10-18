package com.asimut

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asimut.data.StudentCardStorage
import com.asimut.data.TicketsRepository
import com.asimut.models.Dticket
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import androidx.documentfile.provider.DocumentFile

class CardManagementActivity : AppCompatActivity() {

    private lateinit var addCardFab: FloatingActionButton
    private lateinit var backButton: ImageButton
    private lateinit var cardRecyclerView: RecyclerView
    private lateinit var cardAdapter: CardAdapter
    private val cards = mutableListOf<CardListItem>()

    private lateinit var ticketsRepository: TicketsRepository
    private lateinit var studentCardStorage: StudentCardStorage

    private val pickDeutschlandTicketLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                handleDeutschlandTicketSelection(uri)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_management)

        ticketsRepository = TicketsRepository(this)
        studentCardStorage = StudentCardStorage(this)

        backButton = findViewById(R.id.back_button)
        addCardFab = findViewById(R.id.add_card_fab)
        cardRecyclerView = findViewById(R.id.card_recycler_view)

        cardAdapter = CardAdapter(cards, ::handleCardClick)
        cardRecyclerView.layoutManager = LinearLayoutManager(this)
        cardRecyclerView.adapter = cardAdapter

        attachSwipeToDelete()

        loadCards()
        updateFabVisibility()

        backButton.setOnClickListener { finish() }
        addCardFab.setOnClickListener {
            if (hasReachedLimit()) {
                Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
            } else {
                showCardTypeSelectionSheet()
            }
        }
    }

    private fun attachSwipeToDelete() {
        val itemTouchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    showDeleteConfirmation(position)
                }
            }
        })
        itemTouchHelper.attachToRecyclerView(cardRecyclerView)
    }

    private fun loadCards() {
        cards.clear()
        studentCardStorage.getCards().mapTo(cards) { CardListItem.Student(it) }
        ticketsRepository.getAllTickets().mapTo(cards) { CardListItem.Ticket(it) }
        cardAdapter.notifyDataSetChanged()
    }

    private fun updateFabVisibility() {
        addCardFab.isVisible = !hasReachedLimit()
    }

    private fun hasReachedLimit(): Boolean = cards.size >= MAX_CARDS

    private fun showCardTypeSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.layout_card_type_sheet, null)
        dialog.setContentView(sheetView)

        sheetView.findViewById<View>(R.id.sheet_student_option)?.setOnClickListener {
            dialog.dismiss()
            showAddStudentCardDialog()
        }
        sheetView.findViewById<View>(R.id.sheet_ticket_option)?.setOnClickListener {
            dialog.dismiss()
            launchDeutschlandTicketPicker()
        }

        dialog.show()
    }

    private fun launchDeutschlandTicketPicker() {
        pickDeutschlandTicketLauncher.launch(arrayOf("application/vnd.apple.pkpass", "application/zip"))
    }

    private fun showAddStudentCardDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_card, null)
        val firstNameEditText: EditText = dialogView.findViewById(R.id.first_name_edit_text)
        val lastNameEditText: EditText = dialogView.findViewById(R.id.last_name_edit_text)
        val matrikelnummerEditText: EditText = dialogView.findViewById(R.id.matrikelnummer_edit_text)
        val birthDateEditText: EditText = dialogView.findViewById(R.id.birth_date_edit_text)

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setPositiveButton(R.string.add_student_card_positive, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (hasReachedLimit()) {
                    Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    return@setOnClickListener
                }

                val firstName = firstNameEditText.text.toString().trim()
                val lastName = lastNameEditText.text.toString().trim()
                val matrikelnummer = matrikelnummerEditText.text.toString().trim()
                val birthDate = birthDateEditText.text.toString().trim()

                if (firstName.isEmpty() || lastName.isEmpty() || matrikelnummer.isEmpty()) {
                    Toast.makeText(this, R.string.student_card_validation_error, Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val newCard = studentCardStorage.createCard(firstName, lastName, matrikelnummer, birthDate)
                studentCardStorage.addCard(newCard)
                cards.add(CardListItem.Student(newCard))
                cardAdapter.notifyItemInserted(cards.lastIndex)
                updateFabVisibility()
                Toast.makeText(this, R.string.student_card_saved, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun handleDeutschlandTicketSelection(uri: Uri) {
        if (hasReachedLimit()) {
            Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
            return
        }

        takePersistablePermission(uri)

        lifecycleScope.launch {
            val ticket = runCatching { importDeutschlandTicket(uri) }.getOrNull()

            if (ticket == null) {
                Toast.makeText(this@CardManagementActivity, R.string.deutschlandticket_import_failed, Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            val added = ticketsRepository.addTicket(ticket)
            if (!added && cards.none { it is CardListItem.Ticket && it.ticket.id == ticket.id }) {
                File(ticket.pkpassLocalPath).delete()
                Toast.makeText(this@CardManagementActivity, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG)
                    .show()
                return@launch
            }

            val existingIndex = cards.indexOfFirst { it is CardListItem.Ticket && it.ticket.id == ticket.id }
            if (existingIndex >= 0) {
                cards[existingIndex] = CardListItem.Ticket(ticket)
                cardAdapter.notifyItemChanged(existingIndex)
            } else {
                cards.add(CardListItem.Ticket(ticket))
                cardAdapter.notifyItemInserted(cards.lastIndex)
            }

            updateFabVisibility()
            Toast.makeText(this@CardManagementActivity, R.string.deutschlandticket_import_success, Toast.LENGTH_LONG)
                .show()
        }
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Ignore if permission cannot be persisted
        }
    }

    private suspend fun importDeutschlandTicket(uri: Uri): Dticket? = withContext(Dispatchers.IO) {
        DocumentFile.fromSingleUri(this@CardManagementActivity, uri) ?: return@withContext null
        val storageDir = File(filesDir, PASSES_DIRECTORY).apply { if (!exists()) mkdirs() }
        val tempFile = File.createTempFile("ticket_", ".pkpass", storageDir)

        contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: return@withContext null

        val payload = parseDeutschlandTicket(tempFile) ?: run {
            tempFile.delete()
            return@withContext null
        }

        val finalFile = File(storageDir, "${payload.id}.pkpass")
        if (finalFile.exists()) {
            finalFile.delete()
        }

        try {
            FileInputStream(tempFile).use { input ->
                FileOutputStream(finalFile).use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            tempFile.delete()
        }

        return@withContext payload.toTicket(finalFile.absolutePath)
    }

    private fun handleCardClick(item: CardListItem) {
        when (item) {
            is CardListItem.Student -> Unit
            is CardListItem.Ticket -> startActivity(DticketDetailActivity.createIntent(this, item.ticket.id))
        }
    }

    private fun showDeleteConfirmation(position: Int) {
        val item = cards.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_card_title)
            .setMessage(R.string.delete_card_message)
            .setPositiveButton(R.string.delete_yes) { dialog, _ ->
                dialog.dismiss()
                deleteCardAt(position)
            }
            .setNegativeButton(R.string.delete_no) { dialog, _ ->
                dialog.dismiss()
                cardAdapter.notifyItemChanged(position)
            }
            .setOnCancelListener {
                cardAdapter.notifyItemChanged(position)
            }
            .show()
    }

    private fun deleteCardAt(position: Int) {
        val item = cards.getOrNull(position) ?: return
        when (item) {
            is CardListItem.Student -> studentCardStorage.deleteCardById(item.card.id)
            is CardListItem.Ticket -> {
                ticketsRepository.deleteTicketById(item.ticket.id)
                runCatching { File(item.ticket.pkpassLocalPath).takeIf { it.exists() }?.delete() }
            }
        }
        cards.removeAt(position)
        cardAdapter.notifyItemRemoved(position)
        updateFabVisibility()
    }

    private fun parseDeutschlandTicket(passFile: File): TicketPayload? {
        val json = readPassJson(passFile) ?: return null

        val serialNumber = json.optString("serialNumber").takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
        val barcodeMessage = json.findBarcodeMessage() ?: return null
        val barcodeFormat = json.findBarcodeFormat()

        val organizationName = json.optString("organizationName").takeIf { it.isNotBlank() }
        val description = json.optString("description").takeIf { it.isNotBlank() }

        val holder = json.optString("ticketHolder").takeIf { it.isNotBlank() }
            ?: json.optString("personName").takeIf { it.isNotBlank() }
            ?: json.findFieldValue(listOf("b1"), listOf("name"))

        val validityField = json.findFieldValue(listOf("b4"), listOf("gültig", "gueltig", "valid"))
        val (rangeStart, rangeEnd) = parseValidityRange(validityField)

        val validFrom = formatDate(json.optString("validFrom")) ?: formatDate(rangeStart)
        val validTo = formatDate(json.optString("validTo"))
            ?: formatDate(json.optString("validUntil"))
            ?: formatDate(rangeEnd)
        val expirationDate = formatDate(json.optString("expirationDate"))
            ?: json.findFieldValue(emptyList(), listOf("ablauf", "expire"))?.let { formatDate(it) }

        val title = when {
            !validFrom.isNullOrBlank() && !validTo.isNullOrBlank() -> "$validFrom – $validTo"
            !validFrom.isNullOrBlank() -> validFrom
            !validTo.isNullOrBlank() -> validTo
            else -> organizationName ?: description ?: "Deutschlandticket"
        }

        val subtitle = description ?: organizationName

        return TicketPayload(
            id = serialNumber,
            title = title,
            subtitle = subtitle,
            barcodeMessage = barcodeMessage,
            barcodeFormat = barcodeFormat,
            validFrom = validFrom,
            validTo = validTo,
            expirationDate = expirationDate,
            holder = holder
        )
    }

    private fun readPassJson(passFile: File): JSONObject? {
        var jsonString: String? = null
        ZipInputStream(FileInputStream(passFile)).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.equals("pass.json", ignoreCase = true)) {
                    val outputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var read: Int
                    while (zipStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                    }
                    jsonString = outputStream.toString(Charsets.UTF_8.name())
                    outputStream.close()
                    zipStream.closeEntry()
                    break
                }
                entry = zipStream.nextEntry
            }
        }
        return try {
            jsonString?.let { JSONObject(it) }
        } catch (exception: JSONException) {
            null
        }
    }

    private fun JSONObject.findBarcodeMessage(): String? {
        val barcodeObject = optJSONObject("barcode")
        val barcodesArray = optJSONArray("barcodes")
        val directMessage = barcodeObject?.optString("message")
        if (!directMessage.isNullOrBlank()) return directMessage
        if (barcodesArray != null) {
            for (index in 0 until barcodesArray.length()) {
                val candidate = barcodesArray.optJSONObject(index)?.optString("message")
                if (!candidate.isNullOrBlank()) return candidate
            }
        }
        return null
    }

    private fun JSONObject.findBarcodeFormat(): String {
        val barcodeObject = optJSONObject("barcode")
        val barcodesArray = optJSONArray("barcodes")
        val directFormat = barcodeObject?.optString("format")
        if (!directFormat.isNullOrBlank()) return directFormat
        if (barcodesArray != null) {
            for (index in 0 until barcodesArray.length()) {
                val candidate = barcodesArray.optJSONObject(index)?.optString("format")
                if (!candidate.isNullOrBlank()) return candidate
            }
        }
        return "PKBarcodeFormatAztec"
    }

    private fun JSONObject.findFieldValue(keys: List<String>, labels: List<String>): String? {
        val generic = optJSONObject("generic") ?: return null
        val arrays = listOf(
            generic.optJSONArray("primaryFields"),
            generic.optJSONArray("secondaryFields"),
            generic.optJSONArray("auxiliaryFields"),
            generic.optJSONArray("backFields"),
            generic.optJSONArray("additionalInfoFields")
        )
        arrays.forEach { array ->
            array?.let {
                for (index in 0 until it.length()) {
                    val obj = it.optJSONObject(index) ?: continue
                    val key = obj.optString("key").lowercase(Locale.getDefault())
                    val label = obj.optString("label").lowercase(Locale.getDefault())
                    val value = obj.optString("value")
                    if (value.isNullOrBlank()) continue
                    if (keys.any { keyCandidate -> key == keyCandidate.lowercase(Locale.getDefault()) }) {
                        return value
                    }
                    if (labels.any { labelCandidate -> label.contains(labelCandidate.lowercase(Locale.getDefault())) }) {
                        return value
                    }
                }
            }
        }
        return null
    }

    private fun parseValidityRange(value: String?): Pair<String?, String?> {
        if (value.isNullOrBlank()) return null to null
        val separators = listOf("-", "–", "—")
        separators.forEach { separator ->
            if (value.contains(separator)) {
                val parts = value.split(separator)
                if (parts.size >= 2) {
                    return parts[0].trim() to parts[1].trim()
                }
            }
        }
        return value.trim() to null
    }

    private fun formatDate(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        DATE_PATTERNS.forEach { pattern ->
            try {
                val parser = SimpleDateFormat(pattern, Locale.getDefault())
                parser.isLenient = true
                val date = parser.parse(trimmed) ?: return@forEach
                return SimpleDateFormat(OUTPUT_DATE_PATTERN, Locale.getDefault()).format(date)
            } catch (_: ParseException) {
                // try next pattern
            }
        }
        return trimmed
    }

    private data class TicketPayload(
        val id: String,
        val title: String,
        val subtitle: String?,
        val barcodeMessage: String,
        val barcodeFormat: String,
        val validFrom: String?,
        val validTo: String?,
        val expirationDate: String?,
        val holder: String?
    ) {
        fun toTicket(path: String) = Dticket(
            id = id,
            title = title,
            subtitle = subtitle,
            barcodeMessage = barcodeMessage,
            barcodeFormat = barcodeFormat,
            validFrom = validFrom,
            validTo = validTo,
            expirationDate = expirationDate,
            holder = holder,
            pkpassLocalPath = path
        )
    }

    companion object {
        private const val MAX_CARDS = 5
        private const val PASSES_DIRECTORY = "dtickets"
        private const val BUFFER_SIZE = 4096
        private val DATE_PATTERNS = listOf(
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mmXXX",
            "yyyy-MM-dd",
            "dd.MM.yyyy",
            "dd.MM.yy"
        )
        private const val OUTPUT_DATE_PATTERN = "dd.MM.yyyy"
    }
}
