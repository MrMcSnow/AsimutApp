package com.asimut

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.asimut.models.Dticket
import com.asimut.models.StudentCard
import com.asimut.util.BarcodeUtil
import kotlin.LazyThreadSafetyMode

sealed class CardListItem {
    data class Student(val card: StudentCard) : CardListItem()
    data class Ticket(val ticket: Dticket) : CardListItem()
}

class CardAdapter(
    private val items: List<CardListItem>,
    private val onCardToggled: (position: Int, expanded: Boolean) -> Unit,
    private val onTicketOpen: (Dticket) -> Unit
) : RecyclerView.Adapter<CardAdapter.BaseCardViewHolder>() {

    private var expandedItemId: String? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseCardViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val holder = when (viewType) {
            VIEW_TYPE_TICKET -> DticketViewHolder(inflater.inflate(R.layout.item_dticket, parent, false))
            else -> StudentViewHolder(inflater.inflate(R.layout.item_card, parent, false))
        }
        holder.attachToggleListener(::handleToggle)
        return holder
    }

    override fun onBindViewHolder(holder: BaseCardViewHolder, position: Int) {
        val item = items[position]
        holder.bindContent(item)
        holder.applyExpansionState(
            position = position,
            expanded = isExpanded(position),
            animate = false,
            expandedIndex = getExpandedIndex()
        )
    }

    override fun onBindViewHolder(holder: BaseCardViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.contains(PAYLOAD_EXPANSION)) {
            holder.applyExpansionState(
                position = position,
                expanded = isExpanded(position),
                animate = true,
                expandedIndex = getExpandedIndex()
            )
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun getItemCount(): Int = items.size

    override fun getItemViewType(position: Int): Int =
        when (items[position]) {
            is CardListItem.Student -> VIEW_TYPE_STUDENT
            is CardListItem.Ticket -> VIEW_TYPE_TICKET
        }

    fun collapseExpanded() {
        val previousId = expandedItemId ?: return
        val previousIndex = items.indexOfFirst { it.uniqueId() == previousId }
        expandedItemId = null
        if (previousIndex >= 0) {
            notifyItemChanged(previousIndex, PAYLOAD_EXPANSION)
        }
    }

    private fun handleToggle(position: Int) {
        val item = items.getOrNull(position) ?: return
        val clickedId = item.uniqueId()
        val isCurrentlyExpanded = expandedItemId == clickedId
        val previousId = expandedItemId

        expandedItemId = if (isCurrentlyExpanded) null else clickedId

        notifyItemChanged(position, PAYLOAD_EXPANSION)

        previousId?.takeIf { it != clickedId }?.let { prevId ->
            val previousIndex = items.indexOfFirst { it.uniqueId() == prevId }
            if (previousIndex >= 0) {
                notifyItemChanged(previousIndex, PAYLOAD_EXPANSION)
            }
        }

        onCardToggled(position, !isCurrentlyExpanded)
    }

    private fun isExpanded(position: Int): Boolean =
        expandedItemId != null && items[position].uniqueId() == expandedItemId

    private fun getExpandedIndex(): Int {
        val id = expandedItemId ?: return RecyclerView.NO_POSITION
        val index = items.indexOfFirst { it.uniqueId() == id }
        return if (index >= 0) index else RecyclerView.NO_POSITION
    }

    private abstract class BaseCardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private var toggleListener: ((Int) -> Unit)? = null

        private val cardView: CardView
            get() = itemView as CardView

        private val liftOffset by lazy(LazyThreadSafetyMode.NONE) {
            itemView.resources.getDimension(R.dimen.card_stack_lift)
        }
        private val siblingOffset by lazy(LazyThreadSafetyMode.NONE) {
            itemView.resources.getDimension(R.dimen.card_stack_sibling_offset)
        }
        private val collapsedElevation by lazy(LazyThreadSafetyMode.NONE) {
            itemView.resources.getDimension(R.dimen.card_collapsed_elevation)
        }
        private val expandedElevation by lazy(LazyThreadSafetyMode.NONE) {
            itemView.resources.getDimension(R.dimen.card_expanded_elevation)
        }

        init {
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    toggleListener?.invoke(position)
                }
            }
        }

        fun attachToggleListener(listener: (Int) -> Unit) {
            toggleListener = listener
        }

        abstract fun bindContent(item: CardListItem)

        open fun onExpansionChanged(expanded: Boolean) = Unit

        fun applyExpansionState(position: Int, expanded: Boolean, animate: Boolean, expandedIndex: Int) {
            val hasAnyExpanded = expandedIndex != RecyclerView.NO_POSITION
            onExpansionChanged(expanded)
            val targetScale = if (expanded) 1.05f else 1f
            val targetTranslationY = when {
                expanded -> -liftOffset
                hasAnyExpanded && position > expandedIndex -> siblingOffset
                else -> 0f
            }
            val targetElevation = if (expanded) expandedElevation else collapsedElevation

            cardView.cardElevation = targetElevation
            if (animate) {
                cardView.animate().cancel()
                cardView.animate()
                    .scaleX(targetScale)
                    .scaleY(targetScale)
                    .translationY(targetTranslationY)
                    .translationZ(targetElevation)
                    .setDuration(ANIMATION_DURATION)
                    .withStartAction {
                        if (expanded) {
                            cardView.bringToFront()
                        }
                    }
                    .start()
            } else {
                cardView.animate().cancel()
                cardView.scaleX = targetScale
                cardView.scaleY = targetScale
                cardView.translationY = targetTranslationY
                ViewCompat.setTranslationZ(cardView, targetElevation)
                if (expanded) {
                    cardView.bringToFront()
                }
            }
        }
    }

    private class StudentViewHolder(itemView: View) : BaseCardViewHolder(itemView) {
        private val firstNameTextView: TextView = itemView.findViewById(R.id.card_first_name)
        private val lastNameTextView: TextView = itemView.findViewById(R.id.card_last_name)
        private val matrikelnummerTextView: TextView = itemView.findViewById(R.id.card_matrikelnummer)
        private val birthDateTextView: TextView = itemView.findViewById(R.id.card_birth_date)

        override fun bindContent(item: CardListItem) {
            val card = (item as CardListItem.Student).card
            firstNameTextView.text = card.firstName
            lastNameTextView.text = card.lastName
            matrikelnummerTextView.text = card.matrikelnummer
            birthDateTextView.text = card.birthDate
        }
    }

    private inner class DticketViewHolder(itemView: View) : BaseCardViewHolder(itemView) {
        private val titleText: TextView = itemView.findViewById(R.id.text_title)
        private val subtitleText: TextView = itemView.findViewById(R.id.text_subtitle)
        private val barcodeImage: ImageView = itemView.findViewById(R.id.image_barcode)
        private val expirationText: TextView = itemView.findViewById(R.id.text_expiration)
        private val altText: TextView = itemView.findViewById(R.id.text_alt)
        private val expandedSection: LinearLayout = itemView.findViewById(R.id.expanded_section)
        private val actionOpen: View = itemView.findViewById(R.id.button_open_fullscreen)

        override fun bindContent(item: CardListItem) {
            val ticket = (item as CardListItem.Ticket).ticket
            val context = itemView.context

            titleText.text = ticket.title.ifBlank { context.getString(R.string.deutschlandticket_title_fallback) }
            subtitleText.isVisible = !ticket.subtitle.isNullOrBlank()
            subtitleText.text = ticket.subtitle ?: ""

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
                expirationText.text = ""
            }

            altText.text = ticket.holder?.let { context.getString(R.string.deutschlandticket_holder_format, it) } ?: ""
            altText.isVisible = !ticket.holder.isNullOrBlank()

            val bitmap = BarcodeUtil.generateCode(ticket.barcodeMessage, ticket.barcodeFormat)
            barcodeImage.setImageBitmap(bitmap)
            barcodeImage.contentDescription = context.getString(R.string.deutschlandticket_barcode_description)

            actionOpen.setOnClickListener { onTicketOpen(ticket) }
        }

        override fun onExpansionChanged(expanded: Boolean) {
            expandedSection.isVisible = expanded
            actionOpen.isVisible = expanded
        }
    }

    private fun CardListItem.uniqueId(): String = when (this) {
        is CardListItem.Student -> card.id
        is CardListItem.Ticket -> ticket.id
    }

    companion object {
        private const val VIEW_TYPE_STUDENT = 0
        private const val VIEW_TYPE_TICKET = 1
        private const val PAYLOAD_EXPANSION = "payload_expansion"
        private const val ANIMATION_DURATION = 250L
    }
}
