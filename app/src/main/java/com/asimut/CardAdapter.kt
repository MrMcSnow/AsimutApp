package com.asimut

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter

sealed class CardItem {
    data class StudentCard(
        val firstName: String,
        val lastName: String,
        val matrikelnummer: String,
        val birthDate: String
    ) : CardItem()

    data class DeutschlandTicketCard(
        val title: String,
        val logoText: String?,
        val holderName: String?,
        val ticketNumber: String?,
        val birthDate: String?,
        val validity: String?,
        val status: String?,
        val provider: String?,
        val customerNumber: String?,
        val expirationDate: String?,
        val qrMessage: String?,
        val qrAltText: String?,
        val backgroundColor: String?,
        val foregroundColor: String?,
        val labelColor: String?,
        val serialNumber: String,
        val storedFilePath: String
    ) : CardItem()
}

class CardAdapter(
    private val cards: List<CardItem>,
    private val onDeleteClick: (CardItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_DEUTSCHLAND_TICKET -> {
                val view = inflater.inflate(R.layout.item_deutschlandticket_card, parent, false)
                DeutschlandTicketViewHolder(view)
            }

            else -> {
                val view = inflater.inflate(R.layout.item_card, parent, false)
                StudentCardViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val card = cards[position]
        when (holder) {
            is StudentCardViewHolder -> holder.bind(card as CardItem.StudentCard, onDeleteClick)
            is DeutschlandTicketViewHolder -> holder.bind(card as CardItem.DeutschlandTicketCard, onDeleteClick)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CardListItem.Student -> VIEW_TYPE_STUDENT
            is CardListItem.Ticket -> VIEW_TYPE_TICKET
        }
    }

    override fun getItemViewType(position: Int): Int {
        return when (cards[position]) {
            is CardItem.StudentCard -> VIEW_TYPE_STUDENT
            is CardItem.DeutschlandTicketCard -> VIEW_TYPE_DEUTSCHLAND_TICKET
        }
    }

    private class StudentCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val firstNameTextView: TextView = itemView.findViewById(R.id.card_first_name)
        private val lastNameTextView: TextView = itemView.findViewById(R.id.card_last_name)
        private val matrikelnummerTextView: TextView = itemView.findViewById(R.id.card_matrikelnummer)
        private val birthDateTextView: TextView = itemView.findViewById(R.id.card_birth_date)

        fun bind(card: CardItem.StudentCard, onDeleteClick: (CardItem) -> Unit) {
            firstNameTextView.text = card.firstName
            lastNameTextView.text = card.lastName
            matrikelnummerTextView.text = card.matrikelnummer
            birthDateTextView.text = card.birthDate

            itemView.setOnClickListener { onCardClick(CardListItem.Student(card)) }
        }
    }

    private class DticketViewHolder(
        itemView: View,
        private val onCardClick: (CardListItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.text_title)
        private val subtitleText: TextView = itemView.findViewById(R.id.text_subtitle)
        private val barcodeImage: ImageView = itemView.findViewById(R.id.image_barcode)
        private val expirationText: TextView = itemView.findViewById(R.id.text_expiration)
        private val altText: TextView = itemView.findViewById(R.id.text_alt)

        fun bind(ticket: Dticket) {
            val context = itemView.context
            titleText.text = ticket.title.ifBlank { context.getString(R.string.deutschlandticket_title_fallback) }
            subtitleText.isVisible = !ticket.subtitle.isNullOrBlank()
            subtitleText.text = ticket.subtitle

            val validityParts = mutableListOf<String>()
            val validFrom = ticket.validFrom
            val validTo = ticket.validTo
            if (!validFrom.isNullOrBlank() && !validTo.isNullOrBlank()) {
                validityParts += context.getString(R.string.deutschlandticket_validity_range_format, validFrom, validTo)
            } else if (!validFrom.isNullOrBlank()) {
                validityParts += context.getString(R.string.deutschlandticket_valid_from_format, validFrom)
            } else if (!validTo.isNullOrBlank()) {
                validityParts += context.getString(R.string.deutschlandticket_valid_to_format, validTo)
            }

            ticket.expirationDate?.let {
                validityParts += context.getString(R.string.deutschlandticket_expiration_format, it)
            }

            if (validityParts.isNotEmpty()) {
                expirationText.isVisible = true
                expirationText.text = validityParts.joinToString(separator = "\n")
            } else {
                expirationText.isVisible = false
            }

            altText.isVisible = !ticket.holder.isNullOrBlank()
            altText.text = ticket.holder?.let { context.getString(R.string.deutschlandticket_holder_format, it) }

            val bitmap = BarcodeUtil.generateCode(ticket.barcodeMessage, ticket.barcodeFormat)
            barcodeImage.setImageBitmap(bitmap)
            barcodeImage.contentDescription = context.getString(R.string.deutschlandticket_barcode_description)

            itemView.setOnClickListener { onCardClick(CardListItem.Ticket(ticket)) }
        }
    }

    private class DeutschlandTicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: CardView = itemView as CardView
        private val titleTextView: TextView = itemView.findViewById(R.id.dt_title_text)
        private val logoTextView: TextView = itemView.findViewById(R.id.dt_logo_text)
        private val holderNameTextView: TextView = itemView.findViewById(R.id.dt_holder_name)
        private val ticketNumberTextView: TextView = itemView.findViewById(R.id.dt_ticket_number)
        private val validityTextView: TextView = itemView.findViewById(R.id.dt_validity)
        private val birthDateTextView: TextView = itemView.findViewById(R.id.dt_birth_date)
        private val statusTextView: TextView = itemView.findViewById(R.id.dt_status)
        private val providerTextView: TextView = itemView.findViewById(R.id.dt_provider)
        private val customerNumberTextView: TextView = itemView.findViewById(R.id.dt_customer_number)
        private val expirationTextView: TextView = itemView.findViewById(R.id.dt_expiration)
        private val qrAltTextView: TextView = itemView.findViewById(R.id.dt_qr_alt_text)
        private val qrImageView: ImageView = itemView.findViewById(R.id.dt_qr_code)

        fun bind(card: CardItem.DeutschlandTicketCard, onDeleteClick: (CardItem) -> Unit) {
            val context = itemView.context

            val parsedBackground = parseRgbColor(card.backgroundColor) ?: Color.parseColor(DEFAULT_BACKGROUND_COLOR)
            val parsedForeground = parseRgbColor(card.foregroundColor) ?: Color.WHITE
            val parsedLabel = parseRgbColor(card.labelColor) ?: parsedForeground

            cardView.setCardBackgroundColor(parsedBackground)
            titleTextView.setTextColor(parsedForeground)
            logoTextView.setTextColor(parsedLabel)
            holderNameTextView.setTextColor(parsedForeground)
            ticketNumberTextView.setTextColor(parsedForeground)
            validityTextView.setTextColor(parsedForeground)
            birthDateTextView.setTextColor(parsedForeground)
            statusTextView.setTextColor(parsedForeground)
            providerTextView.setTextColor(parsedForeground)
            customerNumberTextView.setTextColor(parsedForeground)
            expirationTextView.setTextColor(parsedForeground)
            qrAltTextView.setTextColor(parsedLabel)

            titleTextView.text = card.title.ifBlank { context.getString(R.string.deutschlandticket_title_fallback) }
            logoTextView.isVisible = !card.logoText.isNullOrBlank()
            logoTextView.text = card.logoText

            holderNameTextView.isVisible = !card.holderName.isNullOrBlank()
            holderNameTextView.text = card.holderName?.let { context.getString(R.string.deutschlandticket_holder_format, it) }

            ticketNumberTextView.isVisible = !card.ticketNumber.isNullOrBlank()
            ticketNumberTextView.text = card.ticketNumber?.let { context.getString(R.string.deutschlandticket_ticket_number_format, it) }

            validityTextView.isVisible = !card.validity.isNullOrBlank()
            validityTextView.text = card.validity?.let { context.getString(R.string.deutschlandticket_validity_format, it) }

            birthDateTextView.isVisible = !card.birthDate.isNullOrBlank()
            birthDateTextView.text = card.birthDate?.let { context.getString(R.string.deutschlandticket_birth_date_format, it) }

            statusTextView.isVisible = !card.status.isNullOrBlank()
            statusTextView.text = card.status?.let { context.getString(R.string.deutschlandticket_status_format, it) }

            providerTextView.isVisible = !card.provider.isNullOrBlank()
            providerTextView.text = card.provider?.let { context.getString(R.string.deutschlandticket_provider_format, it) }

            customerNumberTextView.isVisible = !card.customerNumber.isNullOrBlank()
            customerNumberTextView.text = card.customerNumber?.let { context.getString(R.string.deutschlandticket_customer_number_format, it) }

            expirationTextView.isVisible = !card.expirationDate.isNullOrBlank()
            expirationTextView.text = card.expirationDate?.let { context.getString(R.string.deutschlandticket_expiration_format, it) }

            qrAltTextView.isVisible = !card.qrAltText.isNullOrBlank()
            qrAltTextView.text = card.qrAltText?.let { context.getString(R.string.deutschlandticket_alt_text_format, it) }

            val qrBitmap = card.qrMessage?.let { createQrBitmap(it, parsedForeground) }
            qrImageView.isVisible = qrBitmap != null
            qrImageView.setImageBitmap(qrBitmap)

            itemView.setOnLongClickListener {
                onDeleteClick(card)
                true
            }
        }

        private fun createQrBitmap(content: String, color: Int): Bitmap? {
            return try {
                val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, QR_CODE_SIZE, QR_CODE_SIZE)
                val bitmap = Bitmap.createBitmap(QR_CODE_SIZE, QR_CODE_SIZE, Bitmap.Config.ARGB_8888)
                for (x in 0 until QR_CODE_SIZE) {
                    for (y in 0 until QR_CODE_SIZE) {
                        bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) color else Color.WHITE)
                    }
                }
                bitmap
            } catch (exception: WriterException) {
                null
            }
        }

        private fun parseRgbColor(rgb: String?): Int? {
            if (rgb.isNullOrBlank()) return null
            val matchResult = RGB_REGEX.find(rgb.lowercase()) ?: return null
            val (rString, gString, bString) = matchResult.destructured
            return try {
                val r = rString.toInt().coerceIn(0, 255)
                val g = gString.toInt().coerceIn(0, 255)
                val b = bString.toInt().coerceIn(0, 255)
                Color.rgb(r, g, b)
            } catch (_: NumberFormatException) {
                null
            }
        }

        companion object {
            private const val QR_CODE_SIZE = 384
            private const val DEFAULT_BACKGROUND_COLOR = "#303030"
            private val RGB_REGEX = Regex("rgb\\((\\d{1,3}),\\s*(\\d{1,3}),\\s*(\\d{1,3})\\)")
        }
    }

    companion object {
        private const val VIEW_TYPE_STUDENT = 0
        private const val VIEW_TYPE_DEUTSCHLAND_TICKET = 1
    }
}
