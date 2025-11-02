package com.asimut

import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.asimut.core.util.BarcodeUtil
import com.asimut.util.StudentCardRenderer
import com.google.android.material.card.MaterialCardView

class CardAdapter(
    private val items: List<CardItem>,
    private val onItemClick: (CardItem) -> Unit,
    private val onHelpClick: (CardItem.StudentCard) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class CardItem {
        data class StudentCard(
            val id: String,
            val firstName: String,
            val lastName: String,
            val matrikelnummer: String,
            val birthDate: String
        ) : CardItem()

        data class DeutschlandTicketCard(
            val id: String,
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
            val qrMessage: String,
            val qrFormat: String,
            val qrAltText: String?,
            val backgroundColor: String?,
            val foregroundColor: String?,
            val labelColor: String?,
            val storedFilePath: String
        ) : CardItem()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CardItem.StudentCard -> VIEW_TYPE_STUDENT
            is CardItem.DeutschlandTicketCard -> VIEW_TYPE_DEUTSCHLAND_TICKET
        }
    }

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
        when (holder) {
            is StudentCardViewHolder -> holder.bind(items[position] as CardItem.StudentCard)
            is DeutschlandTicketViewHolder -> holder.bind(items[position] as CardItem.DeutschlandTicketCard)
        }
    }

    private inner class StudentCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
        private val cardImageView: ImageView = itemView.findViewById(R.id.card_image)
        private val helpIcon: ImageView = itemView.findViewById(R.id.card_help_icon)
        private val renderer = StudentCardRenderer(itemView.context)

        fun bind(card: CardItem.StudentCard) {
            val renderedBitmap = renderer.render(
                firstName = card.firstName,
                lastName = card.lastName,
                matrikelnummer = card.matrikelnummer,
                birthDate = card.birthDate
            )
            cardImageView.setImageBitmap(renderedBitmap)

            val context = itemView.context
            cardView.strokeWidth = 0
            cardView.strokeColor = ContextCompat.getColor(context, R.color.student_card_default_stroke)

            itemView.setOnClickListener { onItemClick(card) }
            helpIcon.setOnClickListener { onHelpClick(card) }
        }
    }

    private inner class DeutschlandTicketViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardView: MaterialCardView = itemView as MaterialCardView
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

        fun bind(card: CardItem.DeutschlandTicketCard) {
            val context = itemView.context

            val background = parseRgbColor(card.backgroundColor) ?: Color.parseColor(DEFAULT_BACKGROUND_COLOR)
            val foreground = parseRgbColor(card.foregroundColor) ?: Color.WHITE
            val label = parseRgbColor(card.labelColor) ?: foreground

            cardView.setCardBackgroundColor(background)

            titleTextView.setTextColor(foreground)
            logoTextView.setTextColor(label)
            holderNameTextView.setTextColor(foreground)
            ticketNumberTextView.setTextColor(foreground)
            validityTextView.setTextColor(foreground)
            birthDateTextView.setTextColor(foreground)
            statusTextView.setTextColor(foreground)
            providerTextView.setTextColor(foreground)
            customerNumberTextView.setTextColor(foreground)
            expirationTextView.setTextColor(foreground)
            qrAltTextView.setTextColor(label)

            titleTextView.text = card.title.ifBlank { context.getString(R.string.deutschlandticket_title_fallback) }

            logoTextView.isVisible = !card.logoText.isNullOrBlank()
            logoTextView.text = card.logoText.orEmpty()

            holderNameTextView.isVisible = !card.holderName.isNullOrBlank()
            holderNameTextView.text = card.holderName?.let {
                context.getString(R.string.deutschlandticket_holder_format, it)
            } ?: ""

            ticketNumberTextView.isVisible = !card.ticketNumber.isNullOrBlank()
            ticketNumberTextView.text = card.ticketNumber?.let {
                context.getString(R.string.deutschlandticket_ticket_number_format, it)
            } ?: ""

            validityTextView.isVisible = !card.validity.isNullOrBlank()
            validityTextView.text = card.validity.orEmpty()

            birthDateTextView.isVisible = !card.birthDate.isNullOrBlank()
            birthDateTextView.text = card.birthDate?.let {
                context.getString(R.string.deutschlandticket_birth_date_format, it)
            } ?: ""

            statusTextView.isVisible = !card.status.isNullOrBlank()
            statusTextView.text = card.status?.let {
                context.getString(R.string.deutschlandticket_status_format, it)
            } ?: ""

            providerTextView.isVisible = !card.provider.isNullOrBlank()
            providerTextView.text = card.provider?.let {
                context.getString(R.string.deutschlandticket_provider_format, it)
            } ?: ""

            customerNumberTextView.isVisible = !card.customerNumber.isNullOrBlank()
            customerNumberTextView.text = card.customerNumber?.let {
                context.getString(R.string.deutschlandticket_customer_number_format, it)
            } ?: ""

            expirationTextView.isVisible = !card.expirationDate.isNullOrBlank()
            expirationTextView.text = card.expirationDate?.let {
                context.getString(R.string.deutschlandticket_expiration_format, it)
            } ?: ""

            qrAltTextView.isVisible = !card.qrAltText.isNullOrBlank()
            qrAltTextView.text = card.qrAltText?.let {
                context.getString(R.string.deutschlandticket_alt_text_format, it)
            } ?: ""

            val barcodeBitmap = generateBarcode(card.qrMessage, card.qrFormat)
            qrImageView.isVisible = barcodeBitmap != null
            qrImageView.setImageBitmap(barcodeBitmap)
            qrImageView.contentDescription = context.getString(R.string.deutschlandticket_barcode_description)

            itemView.setOnClickListener { onItemClick(card) }
        }

        private fun parseRgbColor(rgb: String?): Int? {
            if (rgb.isNullOrBlank()) return null
            val match = RGB_REGEX.find(rgb.lowercase()) ?: return null
            val (rString, gString, bString) = match.destructured
            return try {
                val r = rString.toInt().coerceIn(0, 255)
                val g = gString.toInt().coerceIn(0, 255)
                val b = bString.toInt().coerceIn(0, 255)
                Color.rgb(r, g, b)
            } catch (_: NumberFormatException) {
                null
            }
        }

        private fun generateBarcode(message: String, format: String): Bitmap? {
            return runCatching { BarcodeUtil.generateCode(message, format, size = 600) }.getOrNull()
        }
    }

    companion object {
        private const val VIEW_TYPE_STUDENT = 0
        private const val VIEW_TYPE_DEUTSCHLAND_TICKET = 1
        private const val DEFAULT_BACKGROUND_COLOR = "#303030"
        private val RGB_REGEX = Regex("rgb\\((\\d{1,3}),\\s*(\\d{1,3}),\\s*(\\d{1,3})\\)")
    }
}
