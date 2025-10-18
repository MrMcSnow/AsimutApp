package com.asimut

import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.net.Uri
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

class CardManagementActivity : AppCompatActivity() {

    private lateinit var addCardFab: FloatingActionButton
    private lateinit var backButton: ImageButton
    private lateinit var cardRecyclerView: RecyclerView
    private var nfcAdapter: NfcAdapter? = null
    private lateinit var pendingIntent: PendingIntent
    private lateinit var intentFiltersArray: Array<IntentFilter>
    private val nfcTechList: Array<Array<String>> = arrayOf(arrayOf(Ndef::class.java.name))

    private val cards = mutableListOf<CardItem>()
    private lateinit var cardAdapter: CardAdapter
    private lateinit var sharedPreferences: SharedPreferences

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

        sharedPreferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadCards()

        backButton.setOnClickListener {
            finish()
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

        addCardButton.setOnClickListener {
            if (cards.size >= MAX_CARDS) {
                Toast.makeText(
                    this,
                    getString(R.string.card_limit_reached, MAX_CARDS),
                    Toast.LENGTH_LONG
                ).show()
            } else {
                showCardTypeSelectionDialog()
            }
        }
    }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, R.string.nfc_not_supported, Toast.LENGTH_LONG).show()
        }

        val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE
        } else {
            0
        }

        pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            pendingIntentFlags
        )

        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw IllegalStateException("Failed to add wildcard MIME type for NFC", e)
            }
        }
        val tagDiscovered = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        intentFiltersArray = arrayOf(ndef, tagDiscovered)
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, intentFiltersArray, nfcTechList)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action
        ) {
            val tag: Tag = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            } ?: return

            val ndef = Ndef.get(tag) ?: return
            val ndefMessage = ndef.cachedNdefMessage ?: return
            val payload = ndefMessage.records.firstOrNull()?.payload ?: return
            val nfcData = payload.decodeToString()
            saveNfcData(nfcData)
        }

        dialog.show()
    }

    private fun showCardTypeSelectionDialog() {
        val options = arrayOf(
            getString(R.string.card_type_student),
            getString(R.string.card_type_deutschland_ticket)
        )

        AlertDialog.Builder(this)
            .setTitle(R.string.card_type_prompt)
            .setItems(options) { dialog, which ->
                when (which) {
                    0 -> showAddStudentCardDialog()
                    1 -> launchDeutschlandTicketPicker()
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchDeutschlandTicketPicker() {
        pickDeutschlandTicketLauncher.launch(arrayOf("application/vnd.apple.pkpass", "application/zip"))
    }

    private fun showAddStudentCardDialog() {
        val builder = AlertDialog.Builder(this)
        val view = layoutInflater.inflate(R.layout.dialog_add_card, null)
        builder.setView(view)
        val dialog = builder.create()

        takePersistablePermission(uri)

        lifecycleScope.launch {
            val ticket = runCatching { importDeutschlandTicket(uri) }.getOrNull()

            saveStudentCardData(firstName, lastName, matrikelnummer, birthDate)
            dialog.dismiss()
            launchDeutschlandTicketPicker()
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

    private fun saveStudentCardData(
        firstName: String,
        lastName: String,
        matrikelnummer: String,
        birthDate: String
    ) {
        val card = CardItem.StudentCard(firstName, lastName, matrikelnummer, birthDate)
        cards.add(card)
        cardAdapter.notifyItemInserted(cards.lastIndex)
        persistCards()
        Toast.makeText(this, R.string.student_card_saved, Toast.LENGTH_SHORT).show()
    }

    private fun showDeleteCardDialog(card: CardItem) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.delete_card_title)
        builder.setMessage(R.string.delete_card_message)
        builder.setPositiveButton(R.string.delete_yes) { dialog, _ ->
            deleteCardData(card)
            dialog.dismiss()
        }
        builder.setNegativeButton(R.string.delete_no) { dialog, _ ->
            dialog.dismiss()
        }
    }

    private fun deleteCardData(card: CardItem) {
        val position = cards.indexOf(card)
        if (position != -1) {
            cards.removeAt(position)
            cardAdapter.notifyItemRemoved(position)
            if (card is CardItem.DeutschlandTicketCard) {
                runCatching { File(card.storedFilePath).takeIf { it.exists() }?.delete() }
            }
            persistCards()
        }
    }

    private fun loadCards() {
        val serializedCards = sharedPreferences.getString(KEY_CARD_ITEMS, null)
        if (serializedCards.isNullOrBlank()) {
            migrateLegacyCardsIfPresent()
            return
        }

        try {
            val jsonArray = JSONArray(serializedCards)
            if (jsonArray.length() == 0) return
            val loadedCards = mutableListOf<CardItem>()
            var missingPassFiles = false
            for (i in 0 until jsonArray.length()) {
                val jsonObject = jsonArray.getJSONObject(i)
                when (jsonObject.getString(JSON_TYPE_KEY)) {
                    JSON_TYPE_STUDENT -> {
                        val studentCard = CardItem.StudentCard(
                            firstName = jsonObject.optString(JSON_FIRST_NAME),
                            lastName = jsonObject.optString(JSON_LAST_NAME),
                            matrikelnummer = jsonObject.optString(JSON_MATRIKELNUMMER),
                            birthDate = jsonObject.optString(JSON_BIRTH_DATE)
                        )
                        loadedCards.add(studentCard)
                    }

                    JSON_TYPE_DEUTSCHLANDTICKET -> {
                        val storedPath = jsonObject.optString(JSON_STORED_FILE_PATH)
                        val storedFile = if (storedPath.isNullOrEmpty()) null else File(storedPath)
                        if (storedFile != null && storedFile.exists()) {
                            val card = CardItem.DeutschlandTicketCard(
                                title = jsonObject.optString(JSON_TITLE),
                                logoText = jsonObject.optString(JSON_LOGO_TEXT),
                                holderName = jsonObject.optString(JSON_HOLDER_NAME),
                                ticketNumber = jsonObject.optString(JSON_TICKET_NUMBER),
                                birthDate = jsonObject.optString(JSON_BIRTH_DATE),
                                validity = jsonObject.optString(JSON_VALIDITY),
                                status = jsonObject.optString(JSON_STATUS),
                                provider = jsonObject.optString(JSON_PROVIDER),
                                customerNumber = jsonObject.optString(JSON_CUSTOMER_NUMBER),
                                expirationDate = jsonObject.optString(JSON_EXPIRATION_DATE),
                                qrMessage = jsonObject.optString(JSON_QR_MESSAGE),
                                qrAltText = jsonObject.optString(JSON_QR_ALT_TEXT),
                                backgroundColor = jsonObject.optString(JSON_BACKGROUND_COLOR),
                                foregroundColor = jsonObject.optString(JSON_FOREGROUND_COLOR),
                                labelColor = jsonObject.optString(JSON_LABEL_COLOR),
                                serialNumber = jsonObject.optString(JSON_SERIAL_NUMBER),
                                storedFilePath = storedPath
                            )
                            loadedCards.add(card)
                        } else {
                            missingPassFiles = true
                        }
                    }
                }
            }

            if (loadedCards.isNotEmpty()) {
                cards.addAll(loadedCards)
                cardAdapter.notifyItemRangeInserted(0, loadedCards.size)
            }
            if (missingPassFiles) {
                Toast.makeText(this, R.string.deutschlandticket_file_missing, Toast.LENGTH_LONG).show()
            }
        } catch (exception: JSONException) {
            Toast.makeText(this, R.string.deutschlandticket_import_failed, Toast.LENGTH_SHORT).show()
        }
    }

    private fun migrateLegacyCardsIfPresent() {
        val allEntries = sharedPreferences.all
        if (allEntries.isEmpty()) return

        val legacyCards = mutableListOf<CardItem.StudentCard>()
        for ((key, value) in allEntries) {
            if (!key.startsWith(LEGACY_CARD_PREFIX)) continue
            val values = (value as? Set<*>)?.mapNotNull { it as? String } ?: continue
            if (values.isEmpty()) continue

            val matrikelnummer = values.firstOrNull { it.matches(MATRIKEL_REGEX) } ?: ""
            val birthDate = values.firstOrNull { it.matches(BIRTHDATE_REGEX) } ?: ""
            val remainingNames = values.filter { it != matrikelnummer && it != birthDate }
            val firstName = remainingNames.firstOrNull() ?: ""
            val lastName = remainingNames.drop(1).firstOrNull() ?: ""

            legacyCards.add(
                CardItem.StudentCard(
                    firstName = firstName,
                    lastName = lastName,
                    matrikelnummer = matrikelnummer,
                    birthDate = birthDate
                )
            )
        }

        if (legacyCards.isNotEmpty()) {
            cards.addAll(legacyCards)
            cardAdapter.notifyItemRangeInserted(0, legacyCards.size)
            persistCards()
            val editor = sharedPreferences.edit()
            for (card in legacyCards) {
                editor.remove("$LEGACY_CARD_PREFIX${card.matrikelnummer}")
            }
            editor.apply()
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

    private fun persistCards() {
        val jsonArray = JSONArray()
        cards.forEach { card ->
            val jsonObject = JSONObject()
            when (card) {
                is CardItem.StudentCard -> {
                    jsonObject.put(JSON_TYPE_KEY, JSON_TYPE_STUDENT)
                    jsonObject.put(JSON_FIRST_NAME, card.firstName)
                    jsonObject.put(JSON_LAST_NAME, card.lastName)
                    jsonObject.put(JSON_MATRIKELNUMMER, card.matrikelnummer)
                    jsonObject.put(JSON_BIRTH_DATE, card.birthDate)
                }

                is CardItem.DeutschlandTicketCard -> {
                    jsonObject.put(JSON_TYPE_KEY, JSON_TYPE_DEUTSCHLANDTICKET)
                    jsonObject.put(JSON_TITLE, card.title)
                    jsonObject.put(JSON_LOGO_TEXT, card.logoText)
                    jsonObject.put(JSON_HOLDER_NAME, card.holderName)
                    jsonObject.put(JSON_TICKET_NUMBER, card.ticketNumber)
                    jsonObject.put(JSON_BIRTH_DATE, card.birthDate)
                    jsonObject.put(JSON_VALIDITY, card.validity)
                    jsonObject.put(JSON_STATUS, card.status)
                    jsonObject.put(JSON_PROVIDER, card.provider)
                    jsonObject.put(JSON_CUSTOMER_NUMBER, card.customerNumber)
                    jsonObject.put(JSON_EXPIRATION_DATE, card.expirationDate)
                    jsonObject.put(JSON_QR_MESSAGE, card.qrMessage)
                    jsonObject.put(JSON_QR_ALT_TEXT, card.qrAltText)
                    jsonObject.put(JSON_BACKGROUND_COLOR, card.backgroundColor)
                    jsonObject.put(JSON_FOREGROUND_COLOR, card.foregroundColor)
                    jsonObject.put(JSON_LABEL_COLOR, card.labelColor)
                    jsonObject.put(JSON_SERIAL_NUMBER, card.serialNumber)
                    jsonObject.put(JSON_STORED_FILE_PATH, card.storedFilePath)
                }
            }
            jsonArray.put(jsonObject)
        }

        sharedPreferences.edit().putString(KEY_CARD_ITEMS, jsonArray.toString()).apply()
    }

    private fun handleDeutschlandTicketSelection(uri: Uri) {
        if (cards.size >= MAX_CARDS) {
            Toast.makeText(
                this,
                getString(R.string.card_limit_reached, MAX_CARDS),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        lifecycleScope.launch {
            val card = withContext(Dispatchers.IO) {
                try {
                    importDeutschlandTicket(uri)
                } catch (exception: Exception) {
                    null
                }
            }

            if (card == null) {
                Toast.makeText(this@CardManagementActivity, R.string.deutschlandticket_import_failed, Toast.LENGTH_LONG)
                    .show()
            } else {
                cards.add(card)
                cardAdapter.notifyItemInserted(cards.lastIndex)
                persistCards()
                Toast.makeText(this@CardManagementActivity, R.string.deutschlandticket_import_success, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }

    @Throws(IOException::class, JSONException::class)
    private fun importDeutschlandTicket(uri: Uri): CardItem.DeutschlandTicketCard? {
        val resolver = contentResolver
        val storageDir = File(filesDir, PASSES_DIRECTORY).apply { if (!exists()) mkdirs() }
        val tempFile = File.createTempFile("pass_", ".pkpass", storageDir)

        resolver.openInputStream(uri)?.use { input ->
            FileOutputStream(tempFile).use { output ->
                input.copyTo(output)
            }
        } ?: return null

        val parsedCard = parseDeutschlandTicket(tempFile) ?: run {
            tempFile.delete()
            return null
        }

        val safeSerial = parsedCard.serialNumber.ifBlank { UUID.randomUUID().toString() }
        val finalFile = File(storageDir, "$safeSerial.pkpass")
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

        return parsedCard.copy(storedFilePath = finalFile.absolutePath)
    }

    private fun parseDeutschlandTicket(passFile: File): CardItem.DeutschlandTicketCard? {
        var passJson: String? = null

        ZipInputStream(FileInputStream(passFile)).use { zipStream ->
            var entry = zipStream.nextEntry
            while (entry != null) {
                if (!entry.isDirectory && entry.name.equals("pass.json", ignoreCase = true)) {
                    val outputStream = ByteArrayOutputStream()
                    val buffer = ByteArray(BUFFER_SIZE)
                    var count: Int
                    while (zipStream.read(buffer).also { count = it } != -1) {
                        outputStream.write(buffer, 0, count)
                    }
                    passJson = outputStream.toString(Charsets.UTF_8.name())
                    outputStream.close()
                    zipStream.closeEntry()
                    break
                }
                entry = zipStream.nextEntry
            }
        }

        val jsonString = passJson ?: return null
        val jsonObject = JSONObject(jsonString)

        val serialNumber = jsonObject.optString("serialNumber")
        val barcodeObject = jsonObject.optJSONObject("barcode")
        val barcodesArray = jsonObject.optJSONArray("barcodes")
        val qrMessage = barcodeObject?.optString("message")
            ?: barcodesArray?.optJSONObject(0)?.optString("message")
        val qrAltText = barcodeObject?.optString("altText")
            ?: barcodesArray?.optJSONObject(0)?.optString("altText")

        val generic = jsonObject.optJSONObject("generic")
        val primaryFields = generic?.optJSONArray("primaryFields")
        val secondaryFields = generic?.optJSONArray("secondaryFields")
        val auxiliaryFields = generic?.optJSONArray("auxiliaryFields")

        val title = primaryFields?.findFieldValue("h1") ?: primaryFields?.optJSONObject(0)?.optString("label")
            ?: jsonObject.optString("organizationName")
        val logoText = jsonObject.optString("logoText")
        val holderName = secondaryFields?.findFieldValue("b1")
        val ticketNumber = secondaryFields?.findFieldValue("b2")
        val birthDate = secondaryFields?.findFieldValue("b3")
        val validity = secondaryFields?.findFieldValue("b4")
        val status = auxiliaryFields?.findFieldValue("a1")
        val provider = auxiliaryFields?.findFieldValue("a2")
        val customerNumber = auxiliaryFields?.findFieldValue("a3")
        val expirationDate = jsonObject.optString("expirationDate")

        return CardItem.DeutschlandTicketCard(
            title = title ?: "",
            logoText = logoText,
            holderName = holderName,
            ticketNumber = ticketNumber,
            birthDate = birthDate,
            validity = validity,
            status = status,
            provider = provider,
            customerNumber = customerNumber,
            expirationDate = expirationDate,
            qrMessage = qrMessage,
            qrAltText = qrAltText,
            backgroundColor = jsonObject.optString("backgroundColor"),
            foregroundColor = jsonObject.optString("foregroundColor"),
            labelColor = jsonObject.optString("labelColor"),
            serialNumber = serialNumber,
            storedFilePath = passFile.absolutePath
        )
    }

    private fun JSONArray.findFieldValue(key: String): String? {
        for (index in 0 until length()) {
            val field = optJSONObject(index) ?: continue
            if (field.optString("key") == key) {
                return field.optString("value")
            }
        }
        return null
    }

    companion object {
        private const val PREFS_NAME = "cards"
        private const val KEY_CARD_ITEMS = "card_items"
        private const val PASSES_DIRECTORY = "passes"
        private const val MAX_CARDS = 5
        private const val LEGACY_CARD_PREFIX = "card_"
        private const val BUFFER_SIZE = 4096

        private const val JSON_TYPE_KEY = "type"
        private const val JSON_TYPE_STUDENT = "student"
        private const val JSON_TYPE_DEUTSCHLANDTICKET = "deutschlandticket"
        private const val JSON_FIRST_NAME = "firstName"
        private const val JSON_LAST_NAME = "lastName"
        private const val JSON_MATRIKELNUMMER = "matrikelnummer"
        private const val JSON_BIRTH_DATE = "birthDate"
        private const val JSON_TITLE = "title"
        private const val JSON_LOGO_TEXT = "logoText"
        private const val JSON_HOLDER_NAME = "holderName"
        private const val JSON_TICKET_NUMBER = "ticketNumber"
        private const val JSON_VALIDITY = "validity"
        private const val JSON_STATUS = "status"
        private const val JSON_PROVIDER = "provider"
        private const val JSON_CUSTOMER_NUMBER = "customerNumber"
        private const val JSON_EXPIRATION_DATE = "expirationDate"
        private const val JSON_QR_MESSAGE = "qrMessage"
        private const val JSON_QR_ALT_TEXT = "qrAltText"
        private const val JSON_BACKGROUND_COLOR = "backgroundColor"
        private const val JSON_FOREGROUND_COLOR = "foregroundColor"
        private const val JSON_LABEL_COLOR = "labelColor"
        private const val JSON_SERIAL_NUMBER = "serialNumber"
        private const val JSON_STORED_FILE_PATH = "storedFilePath"

        private val MATRIKEL_REGEX = Regex("^[0-9]{4,}")
        private val BIRTHDATE_REGEX = Regex("\\d{2}\\.\\d{2}\\.\\d{4}")
    }
}
