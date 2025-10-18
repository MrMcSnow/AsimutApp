package com.asimut.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.asimut.R
import java.util.Locale
import kotlin.math.min

class StudentCardRenderer(context: Context) {

    private val resources = context.resources

    private val templateBitmap: Bitmap = decodeTemplateBitmap()

    private val namePrimaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_text_primary)
        textSize = sp(14f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val nameSecondaryPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_text_primary)
        textSize = sp(14f)
        textAlign = Paint.Align.CENTER
    }

    private val detailsPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_text_secondary)
        textSize = sp(14f)
        textAlign = Paint.Align.LEFT
    }

    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_badge_text)
        textSize = sp(12f)
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val badgeDefaultBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_badge_background)
    }

    private val badgeNfcBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_nfc_badge_background)
    }

    private fun decodeTemplateBitmap(): Bitmap {
        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }
        BitmapFactory.decodeResource(resources, R.drawable.blank_card, options)

        if (options.outWidth <= 0 || options.outHeight <= 0) {
            return BitmapFactory.decodeResource(resources, R.drawable.blank_card)
                ?: throw IllegalStateException("Unable to decode blank card drawable")
        }

        val displayMetrics = resources.displayMetrics
        val requestedWidth = min(displayMetrics.widthPixels, MAX_TEMPLATE_WIDTH_PX)
            .coerceAtLeast(1)
        val requestedHeight = (requestedWidth.toFloat() / options.outWidth * options.outHeight)
            .toInt()
            .coerceAtLeast(1)

        val decodeOptions = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = false
            inSampleSize = calculateInSampleSize(
                options.outWidth,
                options.outHeight,
                requestedWidth,
                requestedHeight
            )
        }

        val decoded = BitmapFactory.decodeResource(resources, R.drawable.blank_card, decodeOptions)
            ?: throw IllegalStateException("Unable to decode blank card drawable")

        if (decoded.width <= requestedWidth && decoded.height <= requestedHeight) {
            return decoded
        }

        val scaled = Bitmap.createScaledBitmap(decoded, requestedWidth, requestedHeight, true)
        if (scaled != decoded) {
            decoded.recycle()
        }
        return scaled
    }

    private fun calculateInSampleSize(
        originalWidth: Int,
        originalHeight: Int,
        requestedWidth: Int,
        requestedHeight: Int
    ): Int {
        var inSampleSize = 1
        if (originalHeight > requestedHeight || originalWidth > requestedWidth) {
            var halfHeight = originalHeight / 2
            var halfWidth = originalWidth / 2
            while ((halfHeight / inSampleSize) >= requestedHeight &&
                (halfWidth / inSampleSize) >= requestedWidth
            ) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    fun render(
        firstName: String,
        lastName: String,
        matrikelnummer: String,
        birthDate: String,
        showDefaultBadge: Boolean,
        showNfcBadge: Boolean
    ): Bitmap {
        val bitmap = templateBitmap.copy(Bitmap.Config.ARGB_8888, true)

        val canvas = Canvas(bitmap)
        val width = bitmap.width.toFloat()
        val height = bitmap.height.toFloat()

        val horizontalPadding = dp(12f)
        val verticalPadding = dp(12f)
        val contentHeight = height - 2 * verticalPadding
        val centerX = width / 2f

        if (firstName.isNotBlank()) {
            val top = verticalPadding + FIRST_NAME_PERCENT * contentHeight
            drawTopAlignedText(canvas, firstName, centerX, top, namePrimaryPaint)
        }

        if (lastName.isNotBlank()) {
            val top = verticalPadding + LAST_NAME_PERCENT * contentHeight
            drawTopAlignedText(canvas, lastName, centerX, top, nameSecondaryPaint)
        }

        if (matrikelnummer.isNotBlank()) {
            val top = verticalPadding + MATRIKEL_PERCENT * contentHeight
            drawTopAlignedText(canvas, matrikelnummer, horizontalPadding, top, detailsPaint)
        }

        if (birthDate.isNotBlank()) {
            val top = verticalPadding + BIRTH_DATE_PERCENT * contentHeight
            val start = horizontalPadding + dp(16f)
            drawTopAlignedText(canvas, birthDate, start, top, detailsPaint)
        }

        if (showDefaultBadge) {
            drawBadge(
                canvas = canvas,
                text = resources.getString(R.string.student_card_default_badge).uppercase(Locale.getDefault()),
                isStartAligned = true,
                horizontalPadding = horizontalPadding,
                verticalPadding = verticalPadding,
                canvasWidth = width,
                backgroundPaint = badgeDefaultBackgroundPaint
            )
        }

        if (showNfcBadge) {
            drawBadge(
                canvas = canvas,
                text = resources.getString(R.string.student_card_nfc_active_badge).uppercase(Locale.getDefault()),
                isStartAligned = false,
                horizontalPadding = horizontalPadding,
                verticalPadding = verticalPadding,
                canvasWidth = width,
                backgroundPaint = badgeNfcBackgroundPaint
            )
        }

        return bitmap
    }

    private fun drawBadge(
        canvas: Canvas,
        text: String,
        isStartAligned: Boolean,
        horizontalPadding: Float,
        verticalPadding: Float,
        canvasWidth: Float,
        backgroundPaint: Paint
    ) {
        val horizontalTextPadding = dp(12f)
        val verticalTextPadding = dp(6f)
        val textWidth = badgeTextPaint.measureText(text)
        val badgeWidth = textWidth + horizontalTextPadding * 2
        val badgeHeight = badgeTextPaint.fontMetrics.let { it.bottom - it.top } + verticalTextPadding * 2
        val top = verticalPadding
        val left = if (isStartAligned) {
            horizontalPadding
        } else {
            canvasWidth - horizontalPadding - badgeWidth
        }
        val rect = RectF(left, top, left + badgeWidth, top + badgeHeight)
        val radius = badgeHeight / 2f
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        val baseline = rect.top + verticalTextPadding - badgeTextPaint.ascent()
        val textX = rect.left + badgeWidth / 2f
        canvas.drawText(text, textX, baseline, badgeTextPaint)
    }

    private fun drawTopAlignedText(canvas: Canvas, text: String, x: Float, top: Float, paint: Paint) {
        val baseline = top - paint.ascent()
        canvas.drawText(text, x, baseline, paint)
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }

    companion object {
        private const val FIRST_NAME_PERCENT = 0.58f
        private const val LAST_NAME_PERCENT = 0.66f
        private const val MATRIKEL_PERCENT = 0.76f
        private const val BIRTH_DATE_PERCENT = 0.84f
        private const val MAX_TEMPLATE_WIDTH_PX = 1400
    }
}
