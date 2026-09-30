package app.offgrow.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import app.offgrow.R
import app.offgrow.data.WidgetInfo
import kotlin.math.max
import kotlin.math.min

/** Draws the whole widget as one picture, so it matches the app's look exactly. */
object WidgetArt {
    private val PAPER = 0xFFF3EEE3.toInt()
    private val PAPER_94 = 0xF0F3EEE3.toInt()
    private val INK = 0xFF1D2B22.toInt()
    private val INK_SOFT = 0xFF3A443C.toInt()
    private val MUTED = 0xFF5C6159.toInt()
    private val TRACK = 0xFFDDD5C6.toInt()
    private val MOSS = 0xFF2F6B45.toInt()
    private val AMBER = 0xFFD99A1E.toInt()
    private val WILT = 0xFFB8894A.toInt()

    private var display: Typeface? = null
    private var bodyBold: Typeface? = null
    private var bodySemi: Typeface? = null

    private fun fonts(context: Context) {
        if (display != null) return
        display = safeFont(context, R.font.bricolage_800, Typeface.DEFAULT_BOLD)
        bodyBold = safeFont(context, R.font.instrument_700, Typeface.DEFAULT_BOLD)
        bodySemi = safeFont(context, R.font.instrument_600, Typeface.DEFAULT)
    }

    private fun safeFont(context: Context, id: Int, fallback: Typeface): Typeface =
        try {
            context.resources.getFont(id)
        } catch (_: Exception) {
            fallback
        }

    fun barColor(band: String?): Int = when (band) {
        "HOLDING" -> AMBER
        "WILTING" -> WILT
        else -> MOSS
    }

    /**
     * @param s pixels per dp for this bitmap.
     */
    fun draw(
        context: Context,
        w: Int,
        h: Int,
        s: Float,
        info: WidgetInfo?,
        garden: Bitmap?,
        preferWide: Boolean,
    ): Bitmap {
        fonts(context)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(out)
        val radius = 24f * s
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val wide = w.toFloat() / h >= 1.5f

        if (info != null && info.onboarded && garden == null) {
            // Garden picture not drawn yet: show today's status on paper.
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
            c.drawRoundRect(full, radius, radius, bg)
            val left = 16f * s
            val width = w - 32f * s
            val t = textPaint(display, 20f * s, INK)
            fitText(t, info.title, width)
            val st = textPaint(bodyBold, 14f * s, INK)
            fitText(st, info.status, width)
            var y = h / 2f - 18f * s
            c.drawText(info.title, left, y, t)
            y += 24f * s
            c.drawText(info.status, left, y, st)
            y += 14f * s
            drawBar(c, RectF(left, y, w - 16f * s, y + 6f * s), info)
            return out
        }

        if (garden == null || info == null || !info.onboarded) {
            val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
            c.drawRoundRect(full, radius, radius, bg)
            val t = textPaint(display, 20f * s, INK)
            fitText(t, "offgrow", w - 32f * s)
            c.drawText("offgrow", 16f * s, h / 2f - 4f * s, t)
            val sub = textPaint(bodySemi, 13f * s, MUTED)
            fitText(sub, "Tap to plant your garden", w - 32f * s)
            c.drawText("Tap to plant your garden", 16f * s, h / 2f + 18f * s, sub)
            return out
        }

        if (wide && preferWide || wide && w / s >= 220f) {
            drawWide(c, w, h, s, radius, info, garden)
        } else {
            drawSquare(c, w, h, s, radius, info, garden)
        }
        return out
    }

    private fun drawSquare(c: Canvas, w: Int, h: Int, s: Float, radius: Float, info: WidgetInfo, garden: Bitmap) {
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val small = min(w, h) / s < 200f
        drawImageCover(c, garden, full, radius, focusY = if (small) 0.62f else 0.5f)

        if (small) {
            val label = info.vitality.toString()
            val tp = textPaint(bodyBold, 13f * s, INK)
            val tw = tp.measureText(label)
            val padX = 10f * s
            val ph = 24f * s
            val r = RectF(10f * s, h - 10f * s - ph, 10f * s + tw + padX * 2, h - 10f * s)
            c.drawRoundRect(r, 12f * s, 12f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER_94 })
            c.drawText(label, r.left + padX, r.centerY() - (tp.ascent() + tp.descent()) / 2f, tp)
            return
        }

        val inset = 12f * s
        val padX = 14f * s
        val padY = 10f * s
        val titleP = textPaint(display, 17f * s, INK)
        val statusP = textPaint(bodyBold, 13f * s, INK_SOFT)
        val barH = 6f * s
        val gap = 7f * s
        val titleH = -titleP.ascent() + titleP.descent()
        val pillH = padY * 2 + titleH + gap + barH
        val pill = RectF(inset, h - inset - pillH, w - inset, h - inset)
        c.drawRoundRect(pill, 18f * s, 18f * s, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER_94 })

        val inner = pill.width() - padX * 2
        val baseline = pill.top + padY - titleP.ascent()
        fitText(statusP, info.status, inner * 0.52f)
        val statusW = statusP.measureText(info.status)
        fitText(titleP, info.title, inner - statusW - 8f * s)
        c.drawText(info.title, pill.left + padX, baseline, titleP)
        c.drawText(info.status, pill.right - padX - statusW, baseline, statusP)

        val barTop = baseline + titleP.descent() + gap
        drawBar(c, RectF(pill.left + padX, barTop, pill.right - padX, barTop + barH), info)
    }

    private fun drawWide(c: Canvas, w: Int, h: Int, s: Float, radius: Float, info: WidgetInfo, garden: Bitmap) {
        val full = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER }
        c.drawRoundRect(full, radius, radius, bg)

        val imgW = min(w * 0.52f, h * 1.15f)
        val save = c.save()
        c.clipRect(0f, 0f, imgW, h.toFloat())
        drawImageCover(c, garden, RectF(0f, 0f, imgW, h.toFloat()), radius, focusY = 0.62f, squareRight = true)
        c.restoreToCount(save)

        val left = imgW + 14f * s
        val right = w - 16f * s
        val width = max(40f, right - left)
        val titleP = textPaint(display, 20f * s, INK)
        val statusP = textPaint(bodyBold, 14f * s, INK)
        val footP = textPaint(bodySemi, 12f * s, MUTED)
        fitText(titleP, info.title, width)
        fitText(statusP, info.status, width)
        fitText(footP, info.footer, width)

        val titleH = -titleP.ascent() + titleP.descent()
        val statusH = -statusP.ascent() + statusP.descent()
        val footH = -footP.ascent() + footP.descent()
        val barH = 6f * s
        val g = 8f * s
        val total = titleH + g + statusH + g + barH + g + footH
        var y = (h - total) / 2f

        c.drawText(info.title, left, y - titleP.ascent(), titleP); y += titleH + g
        c.drawText(info.status, left, y - statusP.ascent(), statusP); y += statusH + g
        drawBar(c, RectF(left, y, right, y + barH), info); y += barH + g
        c.drawText(info.footer, left, y - footP.ascent(), footP)
    }

    private fun drawBar(c: Canvas, r: RectF, info: WidgetInfo) {
        val rad = r.height() / 2f
        c.drawRoundRect(r, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = TRACK })
        val frac = (info.vitality.coerceIn(0, 100)) / 100f
        if (frac > 0f) {
            val fill = RectF(r.left, r.top, r.left + max(r.height(), r.width() * frac), r.bottom)
            c.drawRoundRect(fill, rad, rad, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = barColor(info.band) })
        }
    }

    /** Centre-crop the garden into [dst] with rounded corners. */
    private fun drawImageCover(
        c: Canvas,
        img: Bitmap,
        dst: RectF,
        radius: Float,
        focusY: Float,
        squareRight: Boolean = false,
    ) {
        val scale = max(dst.width() / img.width, dst.height() / img.height)
        val sw = img.width * scale
        val sh = img.height * scale
        val dx = dst.left + (dst.width() - sw) / 2f
        val dy = dst.top + (dst.height() - sh) * focusY
        val m = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        val shader = BitmapShader(img, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply { setLocalMatrix(m) }
        val p = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply { this.shader = shader }
        if (squareRight) {
            // Rounded on the left only: draw a wider rounded rect and let the clip cut the right side.
            c.drawRoundRect(RectF(dst.left, dst.top, dst.right + radius, dst.bottom), radius, radius, p)
        } else {
            c.drawRoundRect(dst, radius, radius, p)
        }
    }

    private fun textPaint(face: Typeface?, size: Float, color: Int) =
        Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = face
            textSize = size
            this.color = color
        }

    /** Shrink text until it fits the width. */
    private fun fitText(p: Paint, text: String, maxW: Float) {
        if (text.isEmpty() || maxW <= 0f) return
        var guard = 0
        while (p.measureText(text) > maxW && p.textSize > 8f && guard < 40) {
            p.textSize *= 0.94f
            guard++
        }
    }
}
