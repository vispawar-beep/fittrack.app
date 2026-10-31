package app.cleanyourtv.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View

/**
 * The crystal (frosted glass) look. Android TV 11 has no live blur for views, so glass is drawn as
 * a translucent white fill that is brighter at the top, a thin bright rim, and a soft highlight,
 * over a deep wallpaper with glowing colour orbs that show through it.
 */
object Crystal {
    const val TEXT = Color.WHITE
    val TEXT_SOFT = Color.argb(200, 255, 255, 255)
    val TEXT_FAINT = Color.argb(150, 255, 255, 255)
    val FOCUS = Color.argb(255, 170, 225, 255)

    fun dp(context: Context, v: Float) = v * context.resources.displayMetrics.density

    /** A glass panel: rounded, translucent, lighter at the top, with a bright rim. */
    fun panel(context: Context, radiusDp: Float, strength: Int = 1): GradientDrawable =
        GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(Color.argb(56 + 18 * strength, 255, 255, 255), Color.argb(18 + 8 * strength, 255, 255, 255)),
        ).apply {
            cornerRadius = dp(context, radiusDp)
            setStroke(dp(context, 1f).toInt().coerceAtLeast(1), Color.argb(90, 255, 255, 255))
        }

    /** iOS-style rounded square ("squircle") path, approximated with a 22.5% corner radius. */
    fun squircle(rect: RectF): Path = Path().apply {
        val r = rect.width() * 0.225f
        addRoundRect(rect, r, r, Path.Direction.CW)
    }
}

/** Deep night-sky wallpaper with soft colour orbs, like a crystal lit from behind. */
class CrystalWallpaper : Drawable() {
    private val base = Paint(Paint.ANTI_ALIAS_FLAG)
    private val orb = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class Orb(val x: Float, val y: Float, val r: Float, val color: Int)

    private val orbs = listOf(
        Orb(0.12f, 0.18f, 0.55f, Color.argb(150, 64, 120, 255)),   // blue, top left
        Orb(0.88f, 0.12f, 0.45f, Color.argb(130, 170, 90, 255)),   // violet, top right
        Orb(0.70f, 0.92f, 0.60f, Color.argb(120, 40, 210, 200)),   // teal, bottom right
        Orb(0.25f, 0.95f, 0.40f, Color.argb(100, 255, 90, 160)),   // pink, bottom left
    )

    override fun draw(canvas: Canvas) {
        val b = bounds
        val w = b.width().toFloat()
        val h = b.height().toFloat()
        base.shader = LinearGradient(0f, 0f, w, h,
            intArrayOf(Color.rgb(8, 14, 40), Color.rgb(22, 16, 58), Color.rgb(6, 30, 44)), null, Shader.TileMode.CLAMP)
        canvas.drawRect(b, base)
        for (o in orbs) {
            val cx = b.left + o.x * w
            val cy = b.top + o.y * h
            val r = o.r * maxOf(w, h)
            orb.shader = RadialGradient(cx, cy, r, o.color, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            canvas.drawCircle(cx, cy, r, orb)
        }
    }

    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.OPAQUE
}

/**
 * An app icon in an iOS-style glass squircle: glass fill, the app's own icon, a top shine and a
 * bright rim. When [highlighted], a light ring shows focus.
 */
class IconTile(context: Context) : View(context) {
    var icon: Drawable? = null
        set(value) { field = value; invalidate() }

    /** Set for the dock: a solid colour gradient instead of clear glass, with a white glyph. */
    var tint: IntArray? = null
        set(value) { field = value; invalidate() }

    var highlighted = false
        set(value) { field = value; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shine = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val rect = RectF()

    override fun onDraw(canvas: Canvas) {
        val pad = Crystal.dp(context, 4f)
        rect.set(pad, pad, width - pad, height - pad)
        val path = Crystal.squircle(rect)

        val t = tint
        fill.shader = if (t != null) {
            LinearGradient(0f, rect.top, 0f, rect.bottom, t, null, Shader.TileMode.CLAMP)
        } else {
            LinearGradient(0f, rect.top, 0f, rect.bottom,
                Color.argb(90, 255, 255, 255), Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        canvas.drawPath(path, fill)

        icon?.let { d ->
            val inset = if (t != null) rect.width() * 0.22f else rect.width() * 0.14f
            canvas.save()
            canvas.clipPath(path)
            d.setBounds((rect.left + inset).toInt(), (rect.top + inset).toInt(), (rect.right - inset).toInt(), (rect.bottom - inset).toInt())
            d.draw(canvas)
            canvas.restore()
        }

        // Shine across the top half, as on a glass bead.
        canvas.save()
        canvas.clipPath(path)
        shine.shader = LinearGradient(0f, rect.top, 0f, rect.top + rect.height() * 0.5f,
            Color.argb(80, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + rect.height() * 0.5f, shine)
        canvas.restore()

        rim.strokeWidth = Crystal.dp(context, 1f)
        rim.color = Color.argb(120, 255, 255, 255)
        canvas.drawPath(path, rim)

        if (highlighted) {
            ring.strokeWidth = Crystal.dp(context, 3f)
            ring.color = Crystal.FOCUS
            val o = Crystal.dp(context, 2f)
            canvas.drawPath(Crystal.squircle(RectF(rect.left - o, rect.top - o, rect.right + o, rect.bottom + o)), ring)
        }
    }
}
