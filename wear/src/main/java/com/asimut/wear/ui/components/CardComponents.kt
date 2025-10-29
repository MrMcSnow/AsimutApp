package com.asimut.wear.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import com.asimut.core.model.PassPayload
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

fun androidx.wear.compose.material.ScalingLazyListScope.StudentCardDetails(payload: PassPayload.StudentCard) {
    val handledKeys = mutableSetOf<String>()
    val name = listOfNotNull(payload.firstName, payload.lastName)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    if (name.isNotEmpty()) {
        item("student_name") {
            CardField(title = stringResource(id = R.string.student_card_title), value = name)
        }
    }
    if (payload.matrikelnummer.isNotBlank()) {
        handledKeys += "matrikelnummer"
        item("student_matrikel") { CardField(title = "Matrikelnummer", value = payload.matrikelnummer) }
    }
    if (payload.birthDate.isNotBlank()) {
        handledKeys += "birthDate"
        item("student_birth") { CardField(title = "Birth date", value = payload.birthDate) }
    }
    payload.status?.takeIf { it.isNotBlank() }?.let {
        handledKeys += "status"
        item("student_status") { CardField(title = "Status", value = it) }
    }
    payload.fields.filterKeys { it !in handledKeys }.forEach { (key, value) ->
        item("student_$key") {
            CardField(title = key.humanize(), value = value)
        }
    }
}

fun androidx.wear.compose.material.ScalingLazyListScope.DeutschlandTicketDetails(payload: PassPayload.DeutschlandTicket) {
    payload.holder?.takeIf { it.isNotBlank() }?.let {
        item("dt_holder") { CardField(title = "Holder", value = it) }
    }
    payload.validFrom?.takeIf { it.isNotBlank() }?.let {
        item("dt_from") { CardField(title = "Valid from", value = it) }
    }
    payload.validTo?.takeIf { it.isNotBlank() }?.let {
        item("dt_until") { CardField(title = "Valid until", value = it) }
    }
    payload.expirationDate?.takeIf { it.isNotBlank() }?.let {
        item("dt_expiration") { CardField(title = "Expires", value = it) }
    }
    payload.fields.forEach { (key, value) ->
        item("dt_$key") { CardField(title = key.humanize(), value = value) }
    }
}

fun androidx.wear.compose.material.ScalingLazyListScope.MensaCardDetails(payload: PassPayload.MensaCard) {
    payload.balance?.takeIf { it.isNotBlank() }?.let {
        item("mensa_balance") { CardField(title = "Balance", value = it) }
    }
    payload.cardNumber.takeIf { it.isNotBlank() }?.let {
        item("mensa_card") { CardField(title = "Card number", value = it) }
    }
    payload.lastTransaction?.takeIf { it.isNotBlank() }?.let {
        item("mensa_transaction") { CardField(title = "Last transaction", value = it) }
    }
    payload.lastUpdated?.takeIf { it.isNotBlank() }?.let {
        item("mensa_updated") { CardField(title = "Updated", value = it) }
    }
    payload.fields.forEach { (key, value) ->
        item("mensa_$key") { CardField(title = key.humanize(), value = value) }
    }
}

@Composable
fun BarcodeImage(bytes: ByteArray, modifier: Modifier = Modifier) {
    val bitmap = remember(bytes) {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    bitmap?.let {
        Image(
            bitmap = it,
            contentDescription = stringResource(id = R.string.qr_content_description),
            modifier = modifier
                .padding(horizontal = 12.dp)
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
        )
    }
}

@Composable
fun QrCodeView(
    data: String,
    format: PassPayload.Barcode.Format,
    modifier: Modifier = Modifier
) {
    val bitmap = remember(data, format) { generateBarcodeBitmap(data, format) }
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

private fun generateBarcodeBitmap(data: String, format: PassPayload.Barcode.Format): Bitmap? {
    return try {
        val zxingFormat = when (format) {
            PassPayload.Barcode.Format.QR_CODE -> BarcodeFormat.QR_CODE
            PassPayload.Barcode.Format.PDF_417 -> BarcodeFormat.PDF_417
            PassPayload.Barcode.Format.CODE_128 -> BarcodeFormat.CODE_128
            PassPayload.Barcode.Format.AZTEC -> BarcodeFormat.AZTEC
            PassPayload.Barcode.Format.UNKNOWN -> BarcodeFormat.QR_CODE
        }
        val writer = MultiFormatWriter()
        val size = 512
        val bitMatrix = writer.encode(data, zxingFormat, size, size)
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

private fun String.humanize(): String {
    val spaced = replace('_', ' ')
    return spaced.replaceFirstChar { char ->
        if (char.isLowerCase()) char.titlecase() else char.toString()
    }
}
