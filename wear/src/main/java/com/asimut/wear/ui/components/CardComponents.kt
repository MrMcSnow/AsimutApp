package com.asimut.wear.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.asimut.core.sync.PassPayload
import com.asimut.wear.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix

@Composable
fun Title(text: String, subtitle: String?) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = text,
            style = MaterialTheme.typography.title2,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center
        )
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.caption1,
                color = MaterialTheme.colors.onBackground.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

@Composable
fun CardField(title: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.caption2,
            color = MaterialTheme.colors.onBackground.copy(alpha = 0.6f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.body2,
            fontWeight = FontWeight.Medium
        )
    }
}

fun androidx.wear.compose.material.ScalingLazyListScope.StudentCardDetails(payload: PassPayload) {
    val fields = payload.fields
    val name = listOfNotNull(fields["firstName"], fields["lastName"]).joinToString(" ").trim()
    if (name.isNotEmpty()) {
        item("student_name") {
            CardField(title = stringResource(id = R.string.student_card_title), value = name)
        }
    }
    fields["matrikelnummer"]?.let {
        item("student_matrikel") { CardField(title = "Matrikelnummer", value = it) }
    }
    fields["birthDate"]?.let {
        item("student_birth") { CardField(title = "Birth date", value = it) }
    }
    fields["status"]?.let {
        item("student_status") { CardField(title = "Status", value = it) }
    }
}

fun androidx.wear.compose.material.ScalingLazyListScope.DeutschlandTicketDetails(payload: PassPayload) {
    val fields = payload.fields
    fields["holder"]?.let {
        item("dt_holder") { CardField(title = "Holder", value = it) }
    }
    fields["validFrom"]?.let {
        item("dt_from") { CardField(title = "Valid from", value = it) }
    }
    fields["validUntil"]?.let {
        item("dt_until") { CardField(title = "Valid until", value = it) }
    }
    fields["subscriptionId"]?.let {
        item("dt_subscription") { CardField(title = "Subscription", value = it) }
    }
}

fun androidx.wear.compose.material.ScalingLazyListScope.MensaCardDetails(payload: PassPayload) {
    val fields = payload.fields
    fields["balance"]?.let {
        item("mensa_balance") { CardField(title = "Balance", value = it) }
    }
    fields["cardNumber"]?.let {
        item("mensa_card") { CardField(title = "Card number", value = it) }
    }
    fields["lastTransaction"]?.let {
        item("mensa_transaction") { CardField(title = "Last transaction", value = it) }
    }
}

@Composable
fun QrCodeView(barcode: PassPayload.Barcode, modifier: Modifier = Modifier) {
    val bitmap = remember(barcode) { generateBarcodeBitmap(barcode) }
    bitmap?.let {
        Image(
            bitmap = it.asImageBitmap(),
            contentDescription = stringResource(id = R.string.qr_content_description),
            modifier = modifier
                .padding(horizontal = 12.dp)
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

private fun generateBarcodeBitmap(barcode: PassPayload.Barcode): Bitmap? {
    return try {
        val format = when (barcode.format) {
            PassPayload.Barcode.Format.QR_CODE -> BarcodeFormat.QR_CODE
            PassPayload.Barcode.Format.PDF_417 -> BarcodeFormat.PDF_417
            PassPayload.Barcode.Format.CODE_128 -> BarcodeFormat.CODE_128
        }
        val writer = MultiFormatWriter()
        val size = 512
        val bitMatrix = writer.encode(barcode.data, format, size, size)
        bitMatrix.toBitmap()
    } catch (error: WriterException) {
        null
    }
}

private fun BitMatrix.toBitmap(): Bitmap {
    val width = width
    val height = height
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    for (x in 0 until width) {
        for (y in 0 until height) {
            bitmap.setPixel(x, y, if (get(x, y)) Color.BLACK else Color.TRANSPARENT)
        }
    }
    return bitmap
}
