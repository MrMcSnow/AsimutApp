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
import androidx.wear.compose.material.ScalingLazyListScope
import com.asimut.core.model.PassPayload
import com.asimut.wear.R
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

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

fun ScalingLazyListScope.studentCardDetails(payload: PassPayload.StudentCard) {
    if (payload.firstName.isNotBlank() || payload.lastName.isNotBlank()) {
        item("student_name") {
            CardField(
                title = stringResource(id = R.string.student_card_title),
                value = listOf(payload.firstName, payload.lastName)
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            )
        }
    }
    if (payload.matrikelnummer.isNotBlank()) {
        item("student_matrikel") {
            CardField(title = stringResource(id = R.string.student_card_number), value = payload.matrikelnummer)
        }
    }
    if (payload.birthDate.isNotBlank()) {
        item("student_birth") {
            CardField(title = stringResource(id = R.string.student_card_birthdate), value = payload.birthDate)
        }
    }
    payload.nfcTagId?.takeIf { it.isNotBlank() }?.let { tagId ->
        item("student_nfc_status") {
            CardField(
                title = stringResource(id = R.string.student_card_nfc_status),
                value = stringResource(id = R.string.student_card_nfc_ready)
            )
        }
        item("student_nfc_tag") {
            CardField(
                title = stringResource(id = R.string.student_card_nfc_tag_id),
                value = tagId
            )
        }
    }
}

fun ScalingLazyListScope.deutschlandTicketDetails(payload: PassPayload.DeutschlandTicket) {
    payload.holderName.takeIf { it.isNotBlank() }?.let { holder ->
        item("dt_holder") {
            CardField(title = stringResource(id = R.string.deutschlandticket_holder), value = holder)
        }
    }
    payload.validFrom.formatDate()?.let { from ->
        item("dt_valid_from") {
            CardField(title = stringResource(id = R.string.deutschlandticket_valid_from), value = from)
        }
    }
    payload.validTo.formatDate()?.let { to ->
        item("dt_valid_to") {
            CardField(title = stringResource(id = R.string.deutschlandticket_valid_to), value = to)
        }
    }
}

fun ScalingLazyListScope.mensaCardDetails(payload: PassPayload.MensaCard) {
    payload.holderName.takeIf { it.isNotBlank() }?.let { holder ->
        item("mensa_holder") {
            CardField(title = stringResource(id = R.string.mensa_holder), value = holder)
        }
    }
    item("mensa_balance") {
        CardField(
            title = stringResource(id = R.string.mensa_balance),
            value = formatCurrency(payload.balance)
        )
    }
    payload.lastUpdated.formatDateTime()?.let { updated ->
        item("mensa_updated") {
            CardField(title = stringResource(id = R.string.mensa_last_updated), value = updated)
        }
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
fun QrCodeView(data: String, modifier: Modifier = Modifier) {
    val bitmap = remember(data) { generateQrBitmap(data) }
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

private fun generateQrBitmap(data: String): Bitmap? {
    return try {
        val writer = MultiFormatWriter()
        val size = 512
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
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

private fun Long.formatDate(): String? {
    if (this <= 0L) return null
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}

private fun Long.formatDateTime(): String? {
    if (this <= 0L) return null
    val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault())
    return formatter.format(Instant.ofEpochMilli(this))
}

private fun formatCurrency(value: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.GERMANY)
    return formatter.format(value)
}
