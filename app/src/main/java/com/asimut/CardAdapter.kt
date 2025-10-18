// CardAdapter.kt

package com.asimut

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

data class Card(
    val firstName: String,
    val lastName: String,
    val matrikelnummer: String,
    val birthDate: String
)

class CardAdapter(private val cards: List<Card>, private val onDeleteClick: (Card) -> Unit) :
    RecyclerView.Adapter<CardAdapter.CardViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CardViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_card, parent, false)
        return CardViewHolder(view)
    }

    override fun onBindViewHolder(holder: CardViewHolder, position: Int) {
        val card = cards[position]
        holder.bind(card, onDeleteClick)
    }

    override fun getItemCount() = cards.size

    class CardViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val firstNameTextView: TextView = itemView.findViewById(R.id.card_first_name)
        private val lastNameTextView: TextView = itemView.findViewById(R.id.card_last_name)
        private val matrikelnummerTextView: TextView = itemView.findViewById(R.id.card_matrikelnummer)
        private val birthDateTextView: TextView = itemView.findViewById(R.id.card_birth_date)

        fun bind(card: Card, onDeleteClick: (Card) -> Unit) {
            firstNameTextView.text = card.firstName
            lastNameTextView.text = card.lastName
            matrikelnummerTextView.text = card.matrikelnummer
            birthDateTextView.text = card.birthDate

            itemView.setOnLongClickListener {
                onDeleteClick(card)
                true
            }
        }
    }
}