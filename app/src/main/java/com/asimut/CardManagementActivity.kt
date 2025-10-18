package com.asimut

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.nfc.NfcAdapter
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asimut.CardAdapter.CardItem
import com.asimut.data.DeutschlandTicketParser
import com.asimut.data.DticketRepository
import com.asimut.data.StudentCardStorage
import com.asimut.data.TicketsRepository
import com.asimut.models.Dticket
import com.asimut.models.StudentCard
import com.asimut.util.BarcodeUtil
import com.asimut.StudentCardNfcCaptureActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.card.MaterialCardView
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class CardManagementActivity : AppCompatActivity() {

    private lateinit var addCardFab: FloatingActionButton
    private lateinit var backButton: ImageButton
    private lateinit var cardRecyclerView: RecyclerView
    private lateinit var deleteHintButton: ImageButton
    private lateinit var nfcStatusBanner: MaterialCardView
    private lateinit var nfcStatusText: TextView

    private lateinit var studentCardStorage: StudentCardStorage
    private lateinit var ticketsRepository: TicketsRepository

    private val cards = mutableListOf<CardItem>()
    private lateinit var cardAdapter: CardAdapter

    private var isFabHiddenByScroll = false
    private var activeDialog: AlertDialog? = null

    private val pickDeutschlandTicketLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                handleDeutschlandTicketSelection(uri)
            }
        }

    private val captureStudentCardLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val cardId = result.data?.getStringExtra(StudentCardNfcCaptureActivity.EXTRA_CARD_ID)
                val message = result.data?.getStringExtra(StudentCardNfcCaptureActivity.EXTRA_STATUS_MESSAGE)
                val toastMessage = message ?: getString(R.string.student_card_nfc_saved_toast)
                Toast.makeText(this, toastMessage, Toast.LENGTH_LONG).show()
                maybePromptForDefaultPaymentSelection(cardId)
                loadCards()
            } else {
                loadCards()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_management)

        studentCardStorage = StudentCardStorage(this)
        ticketsRepository = TicketsRepository(this)

        backButton = findViewById(R.id.back_button)
        addCardFab = findViewById(R.id.add_card_fab)
        cardRecyclerView = findViewById(R.id.card_recycler_view)
        deleteHintButton = findViewById(R.id.delete_hint_button)
        nfcStatusBanner = findViewById(R.id.nfc_status_banner)
        nfcStatusText = findViewById(R.id.nfc_status_text)

        cardAdapter = CardAdapter(cards, ::handleCardClick)
        cardRecyclerView.layoutManager = LinearLayoutManager(this)
        cardRecyclerView.adapter = cardAdapter
        attachSwipeGestures()
        cardRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (!addCardFab.isVisible || hasReachedLimit()) return
                if (dy > 10 && !isFabHiddenByScroll) {
                    hideFabForScroll()
                } else if (dy < -10 && isFabHiddenByScroll) {
                    showFabAfterScroll()
                }
            }
        })

        backButton.setOnClickListener { finish() }
        deleteHintButton.setOnClickListener {
            Toast.makeText(this, R.string.card_swipe_delete_hint, Toast.LENGTH_SHORT).show()
        }
        addCardFab.setOnClickListener {
            if (hasReachedLimit()) {
                Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
            } else {
                showCardTypeSelectionSheet()
            }
        }

        loadCards()
        maybePromptForDefaultPaymentSelection(null)
    }

    override fun onDestroy() {
        activeDialog?.dismiss()
        super.onDestroy()
    }

    private fun attachSwipeGestures() {
        val deleteBackgroundColor = ContextCompat.getColor(this, R.color.card_swipe_delete_background)
        val defaultBackgroundColor = ContextCompat.getColor(this, R.color.card_swipe_default_background)
        val deleteIcon = ContextCompat.getDrawable(this, R.drawable.ic_delete_white)
        val deletePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = deleteBackgroundColor }
        val defaultBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = defaultBackgroundColor }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@CardManagementActivity, R.color.card_swipe_default_text)
            textSize = resources.getDimension(R.dimen.card_swipe_default_text_size)
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                val position = viewHolder.bindingAdapterPosition
                val card = cards.getOrNull(position)
                val swipeFlags = when (card) {
                    is CardItem.StudentCard -> ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
                    is CardItem.DeutschlandTicketCard -> ItemTouchHelper.LEFT
                    else -> 0
                }
                return makeMovementFlags(0, swipeFlags)
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val card = cards.getOrNull(position)
                    when {
                        direction == ItemTouchHelper.LEFT -> {
                            showDeleteConfirmation(position)
                        }
                        direction == ItemTouchHelper.RIGHT && card is CardItem.StudentCard -> {
                            if (card.isDefaultPayment) {
                                Toast.makeText(
                                    this@CardManagementActivity,
                                    R.string.student_card_already_default,
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                studentCardStorage.setDefaultCardId(card.id)
                                Toast.makeText(
                                    this@CardManagementActivity,
                                    R.string.student_card_swipe_set_default_success,
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                            loadCards()
                        }
                        else -> {
                            cardAdapter.notifyItemChanged(position)
                        }
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    val itemView = viewHolder.itemView
                    val position = viewHolder.bindingAdapterPosition
                    if (position == RecyclerView.NO_POSITION) {
                        super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
                        return
                    }
                    val card = cards.getOrNull(position)

                    if (dX < 0) {
                        val background = RectF(
                            itemView.right + dX,
                            itemView.top.toFloat(),
                            itemView.right.toFloat(),
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, deletePaint)

                        deleteIcon?.let { icon ->
                            val itemHeight = itemView.bottom - itemView.top
                            val iconTop = itemView.top + (itemHeight - icon.intrinsicHeight) / 2
                            val iconMargin = (itemHeight - icon.intrinsicHeight) / 2
                            val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                            val iconRight = itemView.right - iconMargin
                            val iconBottom = iconTop + icon.intrinsicHeight
                            icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                            icon.draw(c)
                        }
                    } else if (dX > 0 && card is CardItem.StudentCard) {
                        val background = RectF(
                            itemView.left.toFloat(),
                            itemView.top.toFloat(),
                            itemView.left + dX,
                            itemView.bottom.toFloat()
                        )
                        c.drawRect(background, defaultBackgroundPaint)

                        val text = getString(R.string.card_swipe_default_label).uppercase()
                        val textWidth = textPaint.measureText(text)
                        val textX = itemView.left + minOf(dX / 2f, itemView.width / 2f) - textWidth / 2f
                        val textY = itemView.top + (itemView.height / 2f) - (textPaint.descent() + textPaint.ascent()) / 2f
                        c.drawText(text, textX, textY, textPaint)
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(cardRecyclerView)
    }

    private fun loadCards() {
        cards.clear()

        val studentCards = studentCardStorage.getCards()
        val defaultCardId = studentCardStorage.getDefaultCardId()
        val sortedStudentCards = studentCards.sortedWith(
            compareByDescending<StudentCard> { card -> card.id == defaultCardId }
                .thenBy { card -> card.lastName.lowercase() }
                .thenBy { card -> card.firstName.lowercase() }
        )
        sortedStudentCards.mapTo(cards) { card ->
            CardItem.StudentCard(
                id = card.id,
                firstName = card.firstName,
                lastName = card.lastName,
                matrikelnummer = card.matrikelnummer,
                birthDate = card.birthDate,
                isDefaultPayment = card.id == defaultCardId,
                isNfcConfigured = !card.nfcTagId.isNullOrBlank()
            )
        }

        val dtickets = ticketsRepository.getAllTickets()
        dtickets.mapNotNullTo(cards) { ticket ->
            createDeutschlandTicketCard(ticket)
        }

        cardAdapter.notifyDataSetChanged()
        updateFabVisibility()
        val defaultCard = sortedStudentCards.firstOrNull { it.id == defaultCardId }
        updateNfcStatusBanner(defaultCard)
    }

    private fun showDeleteConfirmation(position: Int) {
        val card = cards.getOrNull(position) ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_card_title)
            .setMessage(R.string.delete_card_message)
            .setPositiveButton(R.string.delete_yes) { dialog, _ ->
                deleteCard(card, position)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.delete_no) { dialog, _ ->
                dialog.dismiss()
                cardAdapter.notifyItemChanged(position)
            }
            .setOnCancelListener { cardAdapter.notifyItemChanged(position) }
            .show()
    }

    private fun deleteCard(card: CardItem, position: Int) {
        when (card) {
            is CardItem.StudentCard -> {
                studentCardStorage.deleteCardById(card.id)
            }

            is CardItem.DeutschlandTicketCard -> {
                ticketsRepository.deleteTicketById(card.id)
                runCatching { File(card.storedFilePath).takeIf { it.exists() }?.delete() }
                if (DticketRepository.getTicketId(this) == card.id) {
                    val previewPath = DticketRepository.getPreviewPath(this)
                    if (previewPath != null) {
                        runCatching { File(previewPath).takeIf { it.exists() }?.delete() }
                    }
                    DticketRepository.clear(this)
                }
            }
        }

        loadCards()
    }

    private fun updateFabVisibility() {
        addCardFab.isVisible = !hasReachedLimit()
    }

    private fun hasReachedLimit(): Boolean = cards.size >= MAX_CARDS

    private fun updateNfcStatusBanner(defaultCard: StudentCard?) {
        val hasActiveNfc = defaultCard?.nfcTagId?.isNotBlank() == true
        nfcStatusBanner.isVisible = hasActiveNfc
        if (hasActiveNfc && defaultCard != null) {
            val displayName = listOf(defaultCard.firstName, defaultCard.lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")
                .ifBlank { defaultCard.matrikelnummer }
            nfcStatusText.text = getString(
                R.string.student_card_nfc_active_banner,
                displayName
            )
        } else {
            nfcStatusText.text = ""
        }
    }

    private fun hideFabForScroll() {
        isFabHiddenByScroll = true
        addCardFab.animate()
            .translationY(addCardFab.height * 1.5f)
            .setDuration(FAB_ANIMATION_DURATION)
            .start()
    }

    private fun showFabAfterScroll() {
        isFabHiddenByScroll = false
        addCardFab.animate()
            .translationY(0f)
            .setDuration(FAB_ANIMATION_DURATION)
            .start()
    }

    private fun showCardTypeSelectionSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.layout_card_type_sheet, null)
        view.findViewById<MaterialButton>(R.id.sheet_student_option).setOnClickListener {
            dialog.dismiss()
            showAddStudentCardDialog()
        }
        view.findViewById<MaterialButton>(R.id.sheet_ticket_option).setOnClickListener {
            dialog.dismiss()
            launchDeutschlandTicketPicker()
        }
        dialog.setContentView(view)
        dialog.show()
    }

    private fun showAddStudentCardDialog() {
        if (hasReachedLimit()) {
            Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_add_card, null)
        val firstNameInput: EditText = dialogView.findViewById(R.id.first_name_edit_text)
        val lastNameInput: EditText = dialogView.findViewById(R.id.last_name_edit_text)
        val matrikelnummerInput: EditText = dialogView.findViewById(R.id.matrikelnummer_edit_text)
        val birthDateInput: EditText = dialogView.findViewById(R.id.birth_date_edit_text)

        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.card_type_student)
            .setView(dialogView)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener { buttonView: View ->
                val firstName = firstNameInput.text.toString().trim()
                val lastName = lastNameInput.text.toString().trim()
                val matrikelnummer = matrikelnummerInput.text.toString().trim()
                val birthDate = birthDateInput.text.toString().trim()

                if (firstName.isBlank() || lastName.isBlank() || matrikelnummer.isBlank() || birthDate.isBlank()) {
                    Toast.makeText(
                        buttonView.context,
                        R.string.student_card_validation_error,
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }

                saveStudentCardData(firstName, lastName, matrikelnummer, birthDate)
                dialog.dismiss()
            }
        }

        activeDialog = dialog
        dialog.show()
    }

    private fun saveStudentCardData(firstName: String, lastName: String, matrikelnummer: String, birthDate: String) {
        if (hasReachedLimit()) {
            Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
            return
        }

        val card = studentCardStorage.createCard(firstName, lastName, matrikelnummer, birthDate)
        studentCardStorage.addCard(card)
        loadCards()
        Toast.makeText(this, R.string.student_card_saved, Toast.LENGTH_SHORT).show()
        if (isNfcSupported()) {
            launchStudentCardNfcCapture(card)
        } else {
            Toast.makeText(this, R.string.nfc_not_supported, Toast.LENGTH_LONG).show()
        }
    }

    private fun launchStudentCardNfcCapture(card: StudentCard) {
        if (!isNfcSupported()) {
            return
        }
        val intent = StudentCardNfcCaptureActivity.createIntent(
            context = this,
            cardId = card.id,
            firstName = card.firstName,
            lastName = card.lastName
        )
        captureStudentCardLauncher.launch(intent)
    }

    private fun isNfcSupported(): Boolean = NfcAdapter.getDefaultAdapter(this) != null

    private fun maybePromptForDefaultPaymentSelection(preferredCardId: String?) {
        val studentCards = studentCardStorage.getCards()
        if (studentCards.isEmpty()) return

        val currentDefaultId = studentCardStorage.getDefaultCardId()
        if (studentCards.size <= 2) {
            val candidate = preferredCardId ?: currentDefaultId ?: studentCards.first().id
            if (!candidate.isNullOrBlank() && candidate != currentDefaultId) {
                studentCardStorage.setDefaultCardId(candidate)
            }
            return
        }

        val shouldPrompt = currentDefaultId.isNullOrBlank() || (preferredCardId != null && preferredCardId != currentDefaultId)
        if (!shouldPrompt) return

        val names = studentCards.map { card ->
            listOf(card.firstName, card.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { card.matrikelnummer }
        }
        var selectedIndex = studentCards.indexOfFirst { it.id == (preferredCardId ?: currentDefaultId) }
        if (selectedIndex < 0) selectedIndex = 0

        activeDialog?.dismiss()
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.student_card_choose_default_title)
            .setSingleChoiceItems(names.toTypedArray(), selectedIndex) { _, which ->
                selectedIndex = which
            }
            .setPositiveButton(R.string.student_card_choose_default_confirm) { dialog, _ ->
                val chosenCard = studentCards.getOrNull(selectedIndex)
                if (chosenCard != null) {
                    studentCardStorage.setDefaultCardId(chosenCard.id)
                    loadCards()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        activeDialog?.show()
    }

    private fun launchDeutschlandTicketPicker() {
        pickDeutschlandTicketLauncher.launch(arrayOf(MIME_PKPASS, MIME_ZIP))
    }

    private fun handleDeutschlandTicketSelection(uri: Uri) {
        if (hasReachedLimit()) {
            Toast.makeText(this, getString(R.string.card_limit_reached, MAX_CARDS), Toast.LENGTH_LONG).show()
            return
        }

        takePersistablePermission(uri)
        lifecycleScope.launch {
            val result = importDeutschlandTicket(uri)
            if (result == null) {
                Toast.makeText(this@CardManagementActivity, R.string.deutschlandticket_import_failed, Toast.LENGTH_LONG)
                    .show()
            } else {
                loadCards()
                Toast.makeText(this@CardManagementActivity, R.string.deutschlandticket_import_success, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    private suspend fun importDeutschlandTicket(uri: Uri): CardItem.DeutschlandTicketCard? = withContext(Dispatchers.IO) {
        runCatching {
            val passesDir = File(filesDir, PASSES_DIRECTORY).apply { if (!exists()) mkdirs() }
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
                val finalFile = File(passesDir, "${payload.id}.pkpass")
                FileOutputStream(finalFile).use { output ->
                    tempFile.inputStream().use { input ->
                        input.copyTo(output)
                    }
                }

                val ticket = payload.toTicket(finalFile.absolutePath)

                val existingTickets = ticketsRepository.getAllTickets()
                existingTickets.forEach { existing ->
                    ticketsRepository.deleteTicketById(existing.id)
                    runCatching { File(existing.pkpassLocalPath).takeIf { it.exists() }?.delete() }
                }

                val added = ticketsRepository.addTicket(ticket)
                if (!added) {
                    throw IllegalStateException("Ticket limit reached")
                }

                val previewBitmap = BarcodeUtil.generateCode(payload.barcodeMessage, payload.barcodeFormat, size = 900)
                val previewPath = DticketRepository.savePreviewBitmap(this@CardManagementActivity, previewBitmap)

                DticketRepository.savePassData(
                    context = this@CardManagementActivity,
                    ticketId = ticket.id,
                    passJson = parserResult.jsonString,
                    pkpassPath = finalFile.absolutePath,
                    previewPath = previewPath
                )

                createDeutschlandTicketCard(ticket, parserResult.json)
            } finally {
                tempFile.delete()
            }
        }.getOrNull()
    }

    private fun createDeutschlandTicketCard(ticket: Dticket): CardItem.DeutschlandTicketCard? {
        val passJson = DticketRepository.getPassJson(this)
        return createDeutschlandTicketCard(ticket, passJson)
    }

    private fun createDeutschlandTicketCard(ticket: Dticket, passJson: JSONObject?): CardItem.DeutschlandTicketCard? {
        val logoText = passJson?.optString("logoText").takeIf { !it.isNullOrBlank() }
        val backgroundColor = passJson?.optString("backgroundColor").takeIf { !it.isNullOrBlank() }
        val foregroundColor = passJson?.optString("foregroundColor").takeIf { !it.isNullOrBlank() }
        val labelColor = passJson?.optString("labelColor").takeIf { !it.isNullOrBlank() }

        val barcodeObject = passJson?.optJSONObject("barcode")
        val barcodesArray = passJson?.optJSONArray("barcodes")
        val qrAltText = barcodeObject?.optString("altText").takeIf { !it.isNullOrBlank() }
            ?: barcodesArray?.firstNonBlank("altText")

        val generic = passJson?.optJSONObject("generic")
        val secondaryFields = generic?.optJSONArray("secondaryFields")
        val auxiliaryFields = generic?.optJSONArray("auxiliaryFields")

        val ticketNumber = secondaryFields?.findFieldValue(listOf("b2"), listOf("ticket", "nummer"))
        val birthDate = secondaryFields?.findFieldValue(listOf("b3"), listOf("birth", "geburt"))
        val validityField = secondaryFields?.findFieldValue(listOf("b4"), listOf("gültig", "valid"))

        val status = auxiliaryFields?.findFieldValue(listOf("a1"), listOf("status"))
        val provider = auxiliaryFields?.findFieldValue(listOf("a2"), listOf("anbieter", "provider"))
        val customerNumber = auxiliaryFields?.findFieldValue(listOf("a3"), listOf("kund", "customer"))

        val validityText = validityField ?: buildValidityString(ticket.validFrom, ticket.validTo)

        return CardItem.DeutschlandTicketCard(
            id = ticket.id,
            title = ticket.title,
            logoText = logoText,
            holderName = ticket.holder,
            ticketNumber = ticketNumber,
            birthDate = birthDate,
            validity = validityText,
            status = status,
            provider = provider,
            customerNumber = customerNumber,
            expirationDate = ticket.expirationDate,
            qrMessage = ticket.barcodeMessage,
            qrFormat = ticket.barcodeFormat,
            qrAltText = qrAltText,
            backgroundColor = backgroundColor,
            foregroundColor = foregroundColor,
            labelColor = labelColor,
            storedFilePath = ticket.pkpassLocalPath
        )
    }

    private fun buildValidityString(validFrom: String?, validTo: String?): String? {
        return when {
            !validFrom.isNullOrBlank() && !validTo.isNullOrBlank() ->
                getString(R.string.deutschlandticket_validity_range_format, validFrom, validTo)

            !validFrom.isNullOrBlank() ->
                getString(R.string.deutschlandticket_valid_from_format, validFrom)

            !validTo.isNullOrBlank() ->
                getString(R.string.deutschlandticket_valid_to_format, validTo)

            else -> null
        }
    }

    private fun JSONArray.firstNonBlank(key: String): String? {
        for (index in 0 until length()) {
            val candidate = optJSONObject(index)?.optString(key)
            if (!candidate.isNullOrBlank()) return candidate
        }
        return null
    }

    private fun JSONArray.findFieldValue(keys: List<String>, labels: List<String>): String? {
        for (index in 0 until length()) {
            val field = optJSONObject(index) ?: continue
            val key = field.optString("key").orEmpty().lowercase()
            val label = field.optString("label").orEmpty().lowercase()
            val value = field.optString("value")
            if (value.isNullOrBlank()) continue
            if (keys.any { candidate -> key == candidate.lowercase() }) {
                return value
            }
            if (labels.any { candidate -> label.contains(candidate.lowercase()) }) {
                return value
            }
        }
        return null
    }

    private fun takePersistablePermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: SecurityException) {
            // ignore
        }
    }

    private fun handleCardClick(card: CardItem) {
        when (card) {
            is CardItem.StudentCard -> {
                if (card.isDefaultPayment) {
                    Toast.makeText(this, R.string.student_card_already_default, Toast.LENGTH_SHORT).show()
                } else {
                    showSetDefaultCardDialog(card)
                }
            }
            is CardItem.DeutschlandTicketCard -> {
                val intent = DticketDetailActivity.createIntent(this, card.id)
                startActivity(intent)
            }
        }
    }

    private fun showSetDefaultCardDialog(card: CardItem.StudentCard) {
        activeDialog?.dismiss()
        activeDialog = AlertDialog.Builder(this)
            .setTitle(R.string.student_card_make_default_title)
            .setMessage(getString(R.string.student_card_make_default_message, card.firstName, card.lastName))
            .setPositiveButton(R.string.student_card_make_default_confirm) { dialog, _ ->
                studentCardStorage.setDefaultCardId(card.id)
                Toast.makeText(this, R.string.student_card_make_default_success, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                loadCards()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        activeDialog?.show()
    }

    companion object {
        private const val MAX_CARDS = 5
        private const val MIME_PKPASS = "application/vnd.apple.pkpass"
        private const val MIME_ZIP = "application/zip"
        private const val PASSES_DIRECTORY = "passes"
        private const val FAB_ANIMATION_DURATION = 200L
    }
}
