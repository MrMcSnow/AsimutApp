package com.asimut

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.asimut.data.StudentCardStorage
import com.google.android.material.button.MaterialButton

class StudentCardNfcCaptureActivity : AppCompatActivity(), NfcAdapter.ReaderCallback {

    private lateinit var instructionTextView: TextView
    private lateinit var statusTextView: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var cancelButton: MaterialButton

    private var nfcAdapter: NfcAdapter? = null
    private lateinit var studentCardStorage: StudentCardStorage
    private val handler = Handler(Looper.getMainLooper())

    private var cardId: String? = null
    private var firstName: String? = null
    private var lastName: String? = null
    private var resultPosted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_student_card_nfc_capture)

        instructionTextView = findViewById(R.id.nfc_instruction_text)
        statusTextView = findViewById(R.id.nfc_status_text)
        progressBar = findViewById(R.id.nfc_progress)
        cancelButton = findViewById(R.id.nfc_cancel_button)

        cardId = intent.getStringExtra(EXTRA_CARD_ID)
        firstName = intent.getStringExtra(EXTRA_FIRST_NAME)
        lastName = intent.getStringExtra(EXTRA_LAST_NAME)

        if (cardId.isNullOrBlank()) {
            finish()
            return
        }

        studentCardStorage = StudentCardStorage(this)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)

        title = getString(R.string.student_card_nfc_scan_title)
        instructionTextView.text = getString(R.string.student_card_nfc_scan_instruction)
        statusTextView.text = getString(R.string.student_card_nfc_scan_waiting)

        if (nfcAdapter == null) {
            progressBar.isVisible = false
            statusTextView.text = getString(R.string.student_card_nfc_not_supported_instruction)
        }

        cancelButton.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.enableReaderMode(
            this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or
                NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_NFC_F or
                NfcAdapter.FLAG_READER_NFC_V or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableReaderMode(this)
    }

    override fun onTagDiscovered(tag: Tag) {
        val currentCardId = cardId ?: return
        val tagId = tag.id?.joinToString(separator = "") { byte ->
            "%02X".format(byte)
        } ?: return
        val payload = tag.techList.joinToString(separator = ",")

        studentCardStorage.updateCardNfcData(currentCardId, tagId, payload)
        studentCardStorage.ensureDefaultCardId(currentCardId)

        postSuccessResult(currentCardId)
    }

    private fun postSuccessResult(cardId: String) {
        if (resultPosted) return
        resultPosted = true
        handler.post {
            progressBar.isVisible = false
            statusTextView.text = getString(R.string.student_card_nfc_saved_toast)
            val resultIntent = Intent().apply {
                putExtra(EXTRA_CARD_ID, cardId)
                putExtra(EXTRA_STATUS_MESSAGE, getString(R.string.student_card_nfc_saved_toast))
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }

    companion object {
        const val EXTRA_CARD_ID = "extra_card_id"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        private const val EXTRA_FIRST_NAME = "extra_first_name"
        private const val EXTRA_LAST_NAME = "extra_last_name"

        fun createIntent(context: Context, cardId: String, firstName: String?, lastName: String?): Intent {
            return Intent(context, StudentCardNfcCaptureActivity::class.java).apply {
                putExtra(EXTRA_CARD_ID, cardId)
                putExtra(EXTRA_FIRST_NAME, firstName)
                putExtra(EXTRA_LAST_NAME, lastName)
            }
        }
    }
}
