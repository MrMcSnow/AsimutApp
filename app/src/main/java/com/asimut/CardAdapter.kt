package com.asimut

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.asimut.models.Dticket
import com.asimut.models.StudentCard
import com.asimut.util.BarcodeUtil

sealed class CardListItem {
    data class Student(val card: StudentCard) : CardListItem()
    data class Ticket(val ticket: Dticket) : CardListItem()
}

class CardAdapter(
    private val items: List<CardListItem>,
    private val onCardClick: (CardListItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_TICKET -> DticketViewHolder(inflater.inflate(R.layout.item_dticket, parent, false), onCardClick)
            else -> StudentViewHolder(inflater.inflate(R.layout.item_card, parent, false), onCardClick)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (holder) {
            is StudentViewHolder -> holder.bind((items[position] as CardListItem.Student).card)
            is DticketViewHolder -> holder.bind((items[position] as CardListItem.Ticket).ticket)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int {
        return when (items[position]) {
            is CardListItem.Student -> VIEW_TYPE_STUDENT
            is CardListItem.Ticket -> VIEW_TYPE_TICKET
        }
    }

    private class StudentViewHolder(
        itemView: View,
        private val onCardClick: (CardListItem) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val firstNameTextView: TextView = itemView.findViewById(R.id.card_first_name)
        private val lastNameTextView: TextView = itemView.findViewById(R.id.card_last_name)
        private val matrikelnummerTextView: TextView = itemView.findViewById(R.id.card_matrikelnummer)
        private val birthDateTextView: TextView = itemView.findViewById(R.id.card_birth_date)

        fun bind(card: StudentCard) {
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

    companion object {
        private const val VIEW_TYPE_STUDENT = 0
        private const val VIEW_TYPE_TICKET = 1
    }
}
