package com.asimut

import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.asimut.DticketDetailActivity
import com.asimut.data.DeutschlandTicketParser
import com.asimut.data.DticketRepository
import com.asimut.data.StudentCardStorage
import com.asimut.data.TicketsRepository
import com.asimut.models.Dticket
import com.asimut.util.BarcodeUtil
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CardManagementActivity : AppCompatActivity() {

    private lateinit var addCardFab: FloatingActionButton
    private lateinit var backButton: ImageButton
    private lateinit var cardRecyclerView: RecyclerView
    private lateinit var cardAdapter: CardAdapter
    private val cards = mutableListOf<CardListItem>()
    private var isFabHiddenByScroll = false

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

        cardAdapter = CardAdapter(cards, ::onCardToggled, ::openTicketDetail)
        cardRecyclerView.layoutManager = LinearLayoutManager(this)
        cardRecyclerView.adapter = cardAdapter
        val overlap = resources.getDimensionPixelSize(R.dimen.card_stack_overlap)
        cardRecyclerView.addItemDecoration(CardStackItemDecoration(overlap))
        cardRecyclerView.clipToPadding = false
        cardRecyclerView.clipChildren = false
        cardRecyclerView.itemAnimator?.changeDuration = CARD_ANIMATION_DURATION.toLong()
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
        val itemTouchHelper = ItemTouchHelper(SwipeToDeleteCallback())
        itemTouchHelper.attachToRecyclerView(cardRecyclerView)
    }

    private fun loadCards() {
        cards.clear()
        studentCardStorage.getCards().mapTo(cards) { CardListItem.Student(it) }
        ticketsRepository.getAllTickets().mapTo(cards) { CardListItem.Ticket(it) }
        cardAdapter.collapseExpanded()
        cardAdapter.notifyDataSetChanged()
    }

    private fun updateFabVisibility() {
        val reachedLimit = hasReachedLimit()
        addCardFab.isVisible = !reachedLimit
        if (reachedLimit) {
            addCardFab.translationY = 0f
            isFabHiddenByScroll = false
        } else if (!isFabHiddenByScroll) {
            addCardFab.animate().translationY(0f).setDuration(FAB_ANIMATION_DURATION).start()
        }
    }

    private fun hasReachedLimit(): Boolean = cards.size >= MAX_CARDS

    private fun hideFabForScroll() {
        val params = addCardFab.layoutParams as ViewGroup.MarginLayoutParams
        val translation = addCardFab.height + params.bottomMargin.toFloat()
        addCardFab.animate().translationY(translation).setDuration(FAB_ANIMATION_DURATION).start()
        isFabHiddenByScroll = true
    }

    private fun showFabAfterScroll() {
        addCardFab.animate().translationY(0f).setDuration(FAB_ANIMATION_DURATION).start()
        isFabHiddenByScroll = false
    }

    private fun onCardToggled(position: Int, expanded: Boolean) {
        if (expanded) {
            cardRecyclerView.post { cardRecyclerView.smoothScrollToPosition(position) }
        }
    }

    private fun openTicketDetail(ticket: Dticket) {
        startActivity(DticketDetailActivity.createIntent(this, ticket.id))
    }

    private inner class SwipeToDeleteCallback : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(this@CardManagementActivity, R.color.card_delete_background)
            style = Paint.Style.FILL
        }
        private val deleteIcon = ContextCompat.getDrawable(this@CardManagementActivity, R.drawable.ic_delete)
        private val cornerRadius = this@CardManagementActivity.resources.getDimension(R.dimen.swipe_background_radius)

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

        override fun onChildDraw(
            canvas: Canvas,
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            dX: Float,
            dY: Float,
            actionState: Int,
            isCurrentlyActive: Boolean
        ) {
            val itemView = viewHolder.itemView
            if (dX < 0) {
                val backgroundRect = RectF(
                    itemView.right + dX,
                    itemView.top.toFloat(),
                    itemView.right.toFloat(),
                    itemView.bottom.toFloat()
                )
                canvas.drawRoundRect(backgroundRect, cornerRadius, cornerRadius, backgroundPaint)

                deleteIcon?.let { icon ->
                    val iconMargin = (itemView.height - icon.intrinsicHeight) / 2
                    val iconLeft = itemView.right - iconMargin - icon.intrinsicWidth
                    val iconRight = itemView.right - iconMargin
                    val iconTop = itemView.top + (itemView.height - icon.intrinsicHeight) / 2
                    val iconBottom = iconTop + icon.intrinsicHeight
                    icon.setBounds(iconLeft, iconTop, iconRight, iconBottom)
                    icon.draw(canvas)
                }
            }
            super.onChildDraw(canvas, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
        }
    }

    private class CardStackItemDecoration(private val overlap: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            val position = parent.getChildAdapterPosition(view)
            if (position > 0) {
                outRect.top = -overlap
            }
        }
    }

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
        val tempFile = File.createTempFile("ticket_", ".pkpass", cacheDir)

        val inputStream = contentResolver.openInputStream(uri) ?: return@withContext null
        try {
            FileOutputStream(tempFile).use { output ->
                inputStream.use { input ->
                    input.copyTo(output)
                }
            }
        } catch (_: Exception) {
            tempFile.delete()
            return@withContext null
        }

        val parserResult = DeutschlandTicketParser.parse(tempFile) ?: run {
            tempFile.delete()
            return@withContext null
        }

        val payload = parserResult.payload
        val finalFile = File(storageDir, "${payload.id}.pkpass")
        if (finalFile.exists()) {
            runCatching { finalFile.delete() }
        }

        val copySucceeded = runCatching {
            FileOutputStream(finalFile).use { output ->
                tempFile.inputStream().use { input ->
                    input.copyTo(output)
                }
            }
        }.isSuccess
        tempFile.delete()
        if (!copySucceeded) {
            return@withContext null
        }

        val ticket = payload.toTicket(finalFile.absolutePath)

        val previewBitmap = runCatching {
            BarcodeUtil.generateCode(payload.barcodeMessage, payload.barcodeFormat, size = 900)
        }.getOrNull()
        val previewPath = previewBitmap?.let {
            DticketRepository.savePreviewBitmap(this@CardManagementActivity, it)
        }

        DticketRepository.savePassData(
            context = this@CardManagementActivity,
            ticketId = ticket.id,
            passJson = parserResult.jsonString,
            pkpassPath = finalFile.absolutePath,
            previewPath = previewPath
        )

        return@withContext ticket
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
        cardAdapter.collapseExpanded()
        cards.removeAt(position)
        cardAdapter.notifyItemRemoved(position)
        updateFabVisibility()
    }

    companion object {
        private const val MAX_CARDS = 5
        private const val PASSES_DIRECTORY = "dtickets"
        private const val CARD_ANIMATION_DURATION = 250
        private const val FAB_ANIMATION_DURATION = 200L
    }
}
