package com.asimut

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ResolveInfo
import androidx.test.core.app.ApplicationProvider
import com.asimut.data.DticketRepository
import com.asimut.data.TicketsRepository
import com.asimut.models.Dticket
import com.google.android.material.button.MaterialButton
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import androidx.core.content.FileProvider

@RunWith(RobolectricTestRunner::class)
class DticketDetailActivityTest {

    private lateinit var context: Context
    private lateinit var ticketsRepository: TicketsRepository
    private lateinit var passesDir: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        ticketsRepository = TicketsRepository(context)
        passesDir = File(context.filesDir, DticketRepository.PASSES_DIRECTORY).apply { mkdirs() }
    }

    @After
    fun tearDown() {
        passesDir.deleteRecursively()
        context.getSharedPreferences("deutschland_tickets", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
    }

    @Test
    fun openOriginal_launchesViewIntentWhenHandlerExists() {
        val passFile = File(passesDir, "test.pkpass").apply { writeText("content") }
        val ticket = Dticket(
            id = "ticket-id",
            title = "Test Ticket",
            subtitle = null,
            barcodeMessage = "1234567890",
            barcodeFormat = "QR",
            validFrom = null,
            validTo = null,
            expirationDate = null,
            holder = null,
            pkpassLocalPath = passFile.absolutePath
        )
        ticketsRepository.addTicket(ticket)

        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, passFile)

        val resolveInfo = ResolveInfo().apply {
            activityInfo = ActivityInfo().apply {
                packageName = "com.example.viewer"
                name = "ViewerActivity"
            }
        }
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.apple.pkpass")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            resolveInfo
        )

        val activityIntent = DticketDetailActivity.createIntent(context, ticket.id)
        val controller = Robolectric.buildActivity(DticketDetailActivity::class.java, activityIntent).setup()
        val activity = controller.get()

        val openButton: MaterialButton = activity.findViewById(R.id.detail_open_original)
        assertNotNull(openButton)

        openButton.performClick()

        val startedIntent = shadowOf(activity).nextStartedActivity
        assertNotNull(startedIntent)
        assertEquals(Intent.ACTION_VIEW, startedIntent.action)
        assertEquals(uri, startedIntent.data)
        assertEquals("application/vnd.apple.pkpass", startedIntent.type)
    }
}
