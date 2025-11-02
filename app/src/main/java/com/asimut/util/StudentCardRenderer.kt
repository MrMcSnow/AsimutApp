package com.asimut.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.util.TypedValue
import androidx.core.content.ContextCompat
import com.asimut.R
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class StudentCardRenderer(context: Context) {

    private val resources = context.resources

    private val templateWidth = min(resources.displayMetrics.widthPixels, MAX_TEMPLATE_WIDTH_PX).coerceAtLeast(1)
    private val templateHeight = (templateWidth * TEMPLATE_ASPECT_RATIO_HEIGHT / TEMPLATE_ASPECT_RATIO_WIDTH).toInt().coerceAtLeast(1)
    private val templateRect = RectF(0f, 0f, templateWidth.toFloat(), templateHeight.toFloat())

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_background_primary)
        style = Paint.Style.FILL
    }

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_label_color)
        textSize = sp(12f)
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.LEFT
    }

    private val valuePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.student_card_value_color)
        textSize = sp(18f)
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

    private val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.ic_asimut)
        ?: throw IllegalStateException("Unable to decode ic_asimut drawable")

    private val logoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        colorFilter = PorterDuffColorFilter(ContextCompat.getColor(context, R.color.black), PorterDuff.Mode.SRC_IN)
    }

    private val outerPadding = dp(24f)
    private val columnSpacing = dp(16f)
    private val rowSpacing = dp(12f)
    private val cornerRadius = dp(32f)
    private val badgeHorizontalTextPadding = dp(12f)
    private val badgeVerticalTextPadding = dp(6f)
    private val logoSize = dp(72f)
    private val headerSpacing = dp(24f)

    private val rowAscent = max(-labelPaint.ascent(), -valuePaint.ascent())
    private val rowDescent = max(labelPaint.descent(), valuePaint.descent())
    private val rowHeight = rowAscent + rowDescent

    fun render(
        firstName: String,
        lastName: String,
        matrikelnummer: String,
        birthDate: String,
        showDefaultBadge: Boolean
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(templateWidth, templateHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        canvas.drawRoundRect(templateRect, cornerRadius, cornerRadius, backgroundPaint)

        val logoRect = RectF(
            templateRect.right - outerPadding - logoSize,
            templateRect.bottom - outerPadding - logoSize,
            templateRect.right - outerPadding,
            templateRect.bottom - outerPadding
        )
        canvas.drawBitmap(logoBitmap, null, logoRect, logoPaint)

        val badgeTop = outerPadding
        val badgeHeight = badgeHeight()
        val badgesBottom = if (showDefaultBadge) badgeTop + badgeHeight else badgeTop

        if (showDefaultBadge) {
            drawBadge(
                canvas = canvas,
                text = resources.getString(R.string.student_card_default_badge).uppercase(Locale.getDefault()),
                isStartAligned = true,
                horizontalPadding = outerPadding,
                verticalPadding = badgeTop,
                canvasWidth = templateRect.width(),
                backgroundPaint = badgeDefaultBackgroundPaint
            )
        }

        val labels = listOf(
            resources.getString(R.string.student_card_label_last_name) to lastName,
            resources.getString(R.string.student_card_label_first_name) to firstName,
            resources.getString(R.string.student_card_label_matrikel) to matrikelnummer,
            resources.getString(R.string.student_card_label_birth_date) to birthDate
        )

        val maxLabelWidth = labels.maxOf { labelPaint.measureText(it.first) }
        val labelX = outerPadding
        val valueX = labelX + maxLabelWidth + columnSpacing

        val tableTop = badgesBottom + headerSpacing
        var currentBaseline = tableTop + rowAscent

        labels.forEach { (label, value) ->
            canvas.drawText(label, labelX, currentBaseline, labelPaint)
            canvas.drawText(value, valueX, currentBaseline, valuePaint)
            currentBaseline += rowHeight + rowSpacing
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
        val textWidth = badgeTextPaint.measureText(text)
        val badgeWidth = textWidth + badgeHorizontalTextPadding * 2
        val badgeHeight = badgeHeight()
        val top = verticalPadding
        val left = if (isStartAligned) {
            horizontalPadding
        } else {
            canvasWidth - horizontalPadding - badgeWidth
        }
        val rect = RectF(left, top, left + badgeWidth, top + badgeHeight)
        val radius = badgeHeight / 2f
        canvas.drawRoundRect(rect, radius, radius, backgroundPaint)
        val baseline = rect.top + badgeVerticalTextPadding - badgeTextPaint.ascent()
        val textX = rect.left + badgeWidth / 2f
        canvas.drawText(text, textX, baseline, badgeTextPaint)
    }

    private fun badgeHeight(): Float {
        val metrics = badgeTextPaint.fontMetrics
        return metrics.bottom - metrics.top + badgeVerticalTextPadding * 2
    }

    private fun dp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics)
    }

    private fun sp(value: Float): Float {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, resources.displayMetrics)
    }

    companion object {
        private const val TEMPLATE_ASPECT_RATIO_WIDTH = 1087f
        private const val TEMPLATE_ASPECT_RATIO_HEIGHT = 696f
        private const val MAX_TEMPLATE_WIDTH_PX = 1400
    }
}
