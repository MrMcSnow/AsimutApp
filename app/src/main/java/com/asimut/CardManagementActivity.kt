package com.asimut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class CardManagementActivity : AppCompatActivity() {

    private lateinit var addCardButton: Button
    private lateinit var backButton: ImageButton
    private lateinit var cardRecyclerView: RecyclerView
    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private var nfcTechList: Array<Array<String>> = arrayOf(arrayOf(Ndef::class.java.name))

    private val cards = mutableListOf<Card>()
    private lateinit var cardAdapter: CardAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_card_management)

        addCardButton = findViewById(R.id.add_card_button)
        backButton = findViewById(R.id.back_button)
        cardRecyclerView = findViewById(R.id.card_recycler_view)

        cardRecyclerView.layoutManager = LinearLayoutManager(this)
        cardAdapter = CardAdapter(cards) { card ->
            showDeleteCardDialog(card)
        }
        cardRecyclerView.adapter = cardAdapter

        backButton.setOnClickListener {
            finish()
        }

        addCardButton.setOnClickListener {
            showAddCardDialog()
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE
        )
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED)
        intentFiltersArray = arrayOf(ndef)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, nfcTechList)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val ndef = Ndef.get(tag)
                val ndefMessage = ndef.cachedNdefMessage
                val records = ndefMessage.records
                val nfcData = String(records[0].payload)
                saveNfcData(nfcData)
            }
        }
    }

    private fun showAddCardDialog() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_card, null)
        builder.setView(view)
        val dialog = builder.create()

        val firstNameEditText: EditText = view.findViewById(R.id.first_name_edit_text)
        val lastNameEditText: EditText = view.findViewById(R.id.last_name_edit_text)
        val matrikelnummerEditText: EditText = view.findViewById(R.id.matrikelnummer_edit_text)
        val birthDateEditText: EditText = view.findViewById(R.id.birth_date_edit_text)
        val doneButton: Button = view.findViewById(R.id.done_button)

        doneButton.setOnClickListener {
            val firstName = firstNameEditText.text.toString()
            val lastName = lastNameEditText.text.toString()
            val matrikelnummer = matrikelnummerEditText.text.toString()
            val birthDate = birthDateEditText.text.toString()

            saveCardData(firstName, lastName, matrikelnummer, birthDate)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun saveNfcData(nfcData: String) {
        // Save NFC data to a file or shared preferences
        Toast.makeText(this, "NFC data saved: $nfcData", Toast.LENGTH_SHORT).show()
    }

    private fun saveCardData(firstName: String, lastName: String, matrikelnummer: String, birthDate: String) {
        val card = Card(firstName, lastName, matrikelnummer, birthDate)
        cards.add(card)
        cardAdapter.notifyItemInserted(cards.size - 1)

        val sharedPreferences = getSharedPreferences("cards", Context.MODE_PRIVATE)
        val editor = sharedPreferences.edit()
        editor.putStringSet("card_$matrikelnummer", setOf(firstName, lastName, matrikelnummer, birthDate))
        editor.apply()
    }

    private fun showDeleteCardDialog(card: Card) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Delete Card")
        builder.setMessage("Are you sure you want to delete this card?")
        builder.setPositiveButton("Yes") { dialog, _ ->
            deleteCardData(card)
            dialog.dismiss()
        }
        builder.setNegativeButton("No") { dialog, _ ->
            dialog.dismiss()
        }
        builder.show()
    }

    private fun deleteCardData(card: Card) {
        val position = cards.indexOf(card)
        if (position != -1) {
            cards.removeAt(position)
            cardAdapter.notifyItemRemoved(position)

            val sharedPreferences = getSharedPreferences("cards", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.remove("card_${card.matrikelnummer}")
            editor.apply()
        }
    }
}