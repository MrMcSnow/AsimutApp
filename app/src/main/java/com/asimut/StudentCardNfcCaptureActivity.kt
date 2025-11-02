package com.asimut

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.nfc.tech.NfcA
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.asimut.data.StudentCardStorage
import com.google.android.material.button.MaterialButton
import org.json.JSONArray
import org.json.JSONObject

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
        val tagId = tag.id?.toHexString() ?: return
        val payload = buildNfcPayload(tag)

        studentCardStorage.updateCardNfcData(currentCardId, tagId, payload)
        studentCardStorage.ensureDefaultCardId(currentCardId)

        postSuccessResult(currentCardId)
    }

    private fun buildNfcPayload(tag: Tag): String? {
        return runCatching {
            val json = JSONObject()
            json.put(KEY_PAYLOAD_VERSION, PAYLOAD_VERSION)
            json.put(KEY_CAPTURED_AT, System.currentTimeMillis())
            tag.id?.toHexString()?.let { json.put(KEY_TAG_ID, it) }

            val techList = tag.techList
            if (techList.isNotEmpty()) {
                json.put(KEY_TECH_LIST, JSONArray().apply {
                    techList.forEach { put(it) }
                })
            }

            NfcA.get(tag)?.let { nfcA ->
                json.put(KEY_NFC_A, JSONObject().apply {
                    nfcA.atqa?.toHexString()?.let { put(KEY_NFC_A_ATQA, it) }
                    put(KEY_NFC_A_SAK, nfcA.sak.toInt().toHexString())
                    put(KEY_NFC_A_TIMEOUT, nfcA.timeout)
                    put(KEY_NFC_A_MAX_TRANSCEIVE, nfcA.maxTransceiveLength)
                })
            }

            IsoDep.get(tag)?.let { isoDep ->
                json.put(KEY_ISO_DEP, JSONObject().apply {
                    isoDep.historicalBytes?.toHexString()?.let { bytes ->
                        put(KEY_ISO_DEP_HISTORICAL, bytes)
                        put(KEY_ISO_DEP_ATS, bytes)
                    }
                    isoDep.hiLayerResponse?.toHexString()?.let { put(KEY_ISO_DEP_HI_LAYER, it) }
                    put(KEY_ISO_DEP_TIMEOUT, isoDep.timeout)
                    put(KEY_ISO_DEP_MAX_TRANSCEIVE, isoDep.maxTransceiveLength)
                    put(KEY_ISO_DEP_EXTENDED_APDU, isoDep.isExtendedLengthApduSupported)
                })
            }

            json.toString()
        }.getOrElse { error ->
            Log.e(TAG, "Failed to capture NFC payload for Mensa cloning", error)
            null
        }
    }

    private fun ByteArray.toHexString(): String = joinToString(separator = "") { byte ->
        "%02X".format(byte)
    }

    private fun Int.toHexString(): String = String.format("%02X", this and 0xFF)

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
        private const val TAG = "StudentCardNfcCapture"

        const val EXTRA_CARD_ID = "extra_card_id"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        private const val EXTRA_FIRST_NAME = "extra_first_name"
        private const val EXTRA_LAST_NAME = "extra_last_name"

        private const val PAYLOAD_VERSION = 1
        private const val KEY_PAYLOAD_VERSION = "version"
        private const val KEY_CAPTURED_AT = "capturedAt"
        private const val KEY_TAG_ID = "tagId"
        private const val KEY_TECH_LIST = "techList"

        private const val KEY_NFC_A = "nfcA"
        private const val KEY_NFC_A_ATQA = "atqa"
        private const val KEY_NFC_A_SAK = "sak"
        private const val KEY_NFC_A_TIMEOUT = "timeout"
        private const val KEY_NFC_A_MAX_TRANSCEIVE = "maxTransceiveLength"

        private const val KEY_ISO_DEP = "isoDep"
        private const val KEY_ISO_DEP_HISTORICAL = "historicalBytes"
        private const val KEY_ISO_DEP_ATS = "ats"
        private const val KEY_ISO_DEP_HI_LAYER = "hiLayerResponse"
        private const val KEY_ISO_DEP_TIMEOUT = "timeout"
        private const val KEY_ISO_DEP_MAX_TRANSCEIVE = "maxTransceiveLength"
        private const val KEY_ISO_DEP_EXTENDED_APDU = "extendedApdu"

        fun createIntent(context: Context, cardId: String, firstName: String?, lastName: String?): Intent {
            return Intent(context, StudentCardNfcCaptureActivity::class.java).apply {
                putExtra(EXTRA_CARD_ID, cardId)
                putExtra(EXTRA_FIRST_NAME, firstName)
                putExtra(EXTRA_LAST_NAME, lastName)
            }
        }
    }
}
