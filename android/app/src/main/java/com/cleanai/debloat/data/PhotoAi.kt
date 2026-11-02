package com.cleanai.debloat.data

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipInputStream
import kotlin.coroutines.coroutineContext
import kotlin.math.cbrt
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * On-phone photo AI, all open-source models run with LiteRT (TensorFlow Lite); photos never leave the phone.
 *  - Enhance: Real-ESRGAN General x4v3 (bundled, 5 MB) sharpens and makes the photo 4x larger.
 *  - Restore: Real-ESRGAN x4plus (bundled, 17 MB) is stronger on old, blurry and damaged photos and faces.
 *  - Colorize: DDColor (one-time 206 MB download) adds colour to black-and-white photos.
 */
object PhotoAi {
    enum class Mode(val label: String, val hint: String) {
        ENHANCE("Enhance HD", "Sharpen & 4x resolution"),
        RESTORE("Restore old photo", "Fix blur, noise & faces"),
        COLORIZE("Colorize", "Add colour to B&W"),
    }

    // ---------------------------------------------------------------- upscaling (Enhance / Restore)

    private const val TILE = 128
    private const val PAD = 10

    /** Upscales 4x, tile by tile with overlapping edges so no seams show. [progress] gets 0..1. */
    suspend fun upscale(ctx: Context, mode: Mode, source: Bitmap, progress: (Float) -> Unit): Bitmap = withContext(Dispatchers.Default) {
        // Keep the work bounded: the result is at most ~2400 px (Enhance) or ~1600 px (Restore, a heavier model).
        val limit = if (mode == Mode.RESTORE) 400 else 600
        val src = fit(source, limit)
        val interpreter = interpreter(ctx, if (mode == Mode.RESTORE) "restore_x4.tflite" else "enhance_x4.tflite")
        try {
            val scale = 4
            val out = Bitmap.createBitmap(src.width * scale, src.height * scale, Bitmap.Config.ARGB_8888)
            val step = TILE - 2 * PAD
            val cols = (src.width + step - 1) / step
            val rows = (src.height + step - 1) / step
            val inT = interpreter.getInputTensor(0)
            val outT = interpreter.getOutputTensor(0)
            val inBuf = ByteBuffer.allocateDirect(inT.numBytes()).order(ByteOrder.nativeOrder())
            val outBuf = ByteBuffer.allocateDirect(outT.numBytes()).order(ByteOrder.nativeOrder())
            val tilePx = IntArray(TILE * TILE)
            val outSize = TILE * scale
            val outPx = IntArray(outSize * outSize)
            var done = 0
            for (r in 0 until rows) for (c in 0 until cols) {
                coroutineContext.ensureActive()
                // The tile covers [x0, x0+TILE) around the cell, clamped inside the image (edges are repeated).
                val cx = c * step
                val cy = r * step
                val x0 = cx - PAD
                val y0 = cy - PAD
                for (y in 0 until TILE) for (x in 0 until TILE) {
                    val sx = (x0 + x).coerceIn(0, src.width - 1)
                    val sy = (y0 + y).coerceIn(0, src.height - 1)
                    tilePx[y * TILE + x] = src.getPixel(sx, sy)
                }
                fill(inBuf, inT.dataType(), tilePx)
                outBuf.rewind()
                interpreter.run(inBuf, outBuf)
                read(outBuf, outT.dataType(), outPx)
                // Copy only the cell's centre (skipping the padded border) into the result.
                val w = min(step, src.width - cx) * scale
                val h = min(step, src.height - cy) * scale
                out.setPixels(outPx, PAD * scale * outSize + PAD * scale, outSize, cx * scale, cy * scale, w, h)
                done++
                progress(done.toFloat() / (rows * cols))
            }
            out
        } finally {
            interpreter.close()
        }
    }

    private fun fill(buf: ByteBuffer, type: DataType, px: IntArray) {
        buf.rewind()
        for (p in px) {
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (type == DataType.FLOAT32) { buf.putFloat(r / 255f); buf.putFloat(g / 255f); buf.putFloat(b / 255f) }
            else { buf.put(r.toByte()); buf.put(g.toByte()); buf.put(b.toByte()) } // uint8, scale 1/255
        }
        buf.rewind()
    }

    private fun read(buf: ByteBuffer, type: DataType, px: IntArray) {
        buf.rewind()
        for (i in px.indices) {
            val (r, g, b) = if (type == DataType.FLOAT32) Triple(c(buf.float), c(buf.float), c(buf.float))
            else Triple(buf.get().toInt() and 0xFF, buf.get().toInt() and 0xFF, buf.get().toInt() and 0xFF)
            px[i] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
    }

    private fun c(v: Float) = (v * 255f).roundToInt().coerceIn(0, 255)

    // ---------------------------------------------------------------- colorize (DDColor)

    private const val DDCOLOR_URL =
        "https://qaihub-public-assets.s3.us-west-2.amazonaws.com/qai-hub-models/models/ddcolor/releases/v0.65.0/ddcolor-tflite-float.zip"
    const val COLORIZE_SIZE = "206 MB"

    private fun dir(ctx: Context) = File(ctx.getExternalFilesDir(null) ?: ctx.filesDir, "photo").apply { mkdirs() }
    private fun ddcolorZip(ctx: Context) = File(dir(ctx), "ddcolor.zip")
    private fun ddcolorModel(ctx: Context) = File(dir(ctx), "ddcolor.tflite")

    fun colorizeReady(ctx: Context) = ddcolorModel(ctx).let { it.exists() && it.length() > 100_000_000 }

    /** Starts the one-time download; returns the DownloadManager id. */
    fun startColorizeDownload(ctx: Context): Long {
        ddcolorZip(ctx).delete()
        val req = DownloadManager.Request(Uri.parse(DDCOLOR_URL))
            .setTitle("Mobile Handler photo colorizer")
            .setDescription("Colorize model ($COLORIZE_SIZE)")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(ddcolorZip(ctx)))
        return ctx.getSystemService(DownloadManager::class.java).enqueue(req)
    }

    /** 0..1 while downloading, null once finished or failed. */
    fun downloadProgress(ctx: Context, id: Long): Float? {
        ctx.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id)).use { c ->
            if (!c.moveToFirst()) return null
            val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status == DownloadManager.STATUS_SUCCESSFUL || status == DownloadManager.STATUS_FAILED) return null
            val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)).takeIf { it > 0 } ?: 206_000_000L
            return (done.toFloat() / total).coerceIn(0f, 1f)
        }
    }

    /** Pulls the .tflite out of the downloaded zip. Returns true when the model is ready. */
    suspend fun unpackColorize(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        val zip = ddcolorZip(ctx)
        if (!zip.exists()) return@withContext colorizeReady(ctx)
        runCatching {
            ZipInputStream(zip.inputStream().buffered()).use { z ->
                generateSequence { z.nextEntry }.firstOrNull { it.name.endsWith(".tflite") }?.let {
                    ddcolorModel(ctx).outputStream().use { out -> z.copyTo(out) }
                }
            }
        }
        zip.delete()
        colorizeReady(ctx)
    }

    fun deleteColorize(ctx: Context) {
        ddcolorZip(ctx).delete()
        ddcolorModel(ctx).delete()
    }

    /**
     * DDColor predicts the a/b colour channels from a grey image; they're stretched back to full size and joined with
     * the photo's own lightness, so details stay sharp.
     */
    suspend fun colorize(ctx: Context, source: Bitmap, progress: (Float) -> Unit): Bitmap = withContext(Dispatchers.Default) {
        val src = fit(source, 2048)
        val interpreter = Interpreter(ddcolorModel(ctx), Interpreter.Options().setNumThreads(4))
        try {
            progress(0.1f)
            val inShape = interpreter.getInputTensor(0).shape() // [1, H, W, 3] or [1, 3, H, W]
            val nchwIn = inShape[1] == 3
            val mh = if (nchwIn) inShape[2] else inShape[1]
            val mw = if (nchwIn) inShape[3] else inShape[2]
            val small = Bitmap.createScaledBitmap(src, mw, mh, true)
            val inBuf = ByteBuffer.allocateDirect(4 * 3 * mw * mh).order(ByteOrder.nativeOrder())
            val grey = FloatArray(mw * mh)
            for (y in 0 until mh) for (x in 0 until mw) grey[y * mw + x] = greyFromL(lab(small.getPixel(x, y))[0])
            if (nchwIn) repeat(3) { grey.forEach { v -> inBuf.putFloat(v) } } else grey.forEach { v -> repeat(3) { inBuf.putFloat(v) } }
            inBuf.rewind()

            val outShape = interpreter.getOutputTensor(0).shape() // [1, h, w, 2] or [1, 2, h, w]
            val nchwOut = outShape[1] == 2
            val oh = if (nchwOut) outShape[2] else outShape[1]
            val ow = if (nchwOut) outShape[3] else outShape[2]
            val outBuf = ByteBuffer.allocateDirect(4 * 2 * oh * ow).order(ByteOrder.nativeOrder())
            interpreter.run(inBuf, outBuf)
            progress(0.7f)
            outBuf.rewind()
            val a = FloatArray(oh * ow)
            val b = FloatArray(oh * ow)
            if (nchwOut) { for (i in a.indices) a[i] = outBuf.float; for (i in b.indices) b[i] = outBuf.float }
            else for (i in a.indices) { a[i] = outBuf.float; b[i] = outBuf.float }

            val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
            val row = IntArray(src.width)
            for (y in 0 until src.height) {
                coroutineContext.ensureActive()
                for (x in 0 until src.width) {
                    val l = lab(src.getPixel(x, y))[0]
                    val fx = (x + 0.5f) * ow / src.width - 0.5f
                    val fy = (y + 0.5f) * oh / src.height - 0.5f
                    row[x] = labToRgb(l, sample(a, ow, oh, fx, fy), sample(b, ow, oh, fx, fy))
                }
                out.setPixels(row, 0, src.width, 0, y, src.width, 1)
            }
            progress(1f)
            out
        } finally {
            interpreter.close()
        }
    }

    private fun sample(m: FloatArray, w: Int, h: Int, fx: Float, fy: Float): Float {
        val x0 = fx.toInt().coerceIn(0, w - 1); val y0 = fy.toInt().coerceIn(0, h - 1)
        val x1 = min(x0 + 1, w - 1); val y1 = min(y0 + 1, h - 1)
        val tx = (fx - x0).coerceIn(0f, 1f); val ty = (fy - y0).coerceIn(0f, 1f)
        val top = m[y0 * w + x0] * (1 - tx) + m[y0 * w + x1] * tx
        val bot = m[y1 * w + x0] * (1 - tx) + m[y1 * w + x1] * tx
        return top * (1 - ty) + bot * ty
    }

    // sRGB <-> CIE Lab (D65), the same float ranges OpenCV uses: L 0..100, a/b roughly -128..127.
    private fun lin(c: Float) = if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).pow(2.4f)
    private fun gam(c: Float) = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1 / 2.4f) - 0.055f
    private fun f(t: Float) = if (t > 0.008856f) cbrt(t) else 7.787f * t + 16f / 116f
    private fun fi(t: Float) = if (t > 0.206893f) t * t * t else (t - 16f / 116f) / 7.787f

    private fun lab(p: Int): FloatArray {
        val r = lin(((p shr 16) and 0xFF) / 255f); val g = lin(((p shr 8) and 0xFF) / 255f); val b = lin((p and 0xFF) / 255f)
        val x = (0.412453f * r + 0.357580f * g + 0.180423f * b) / 0.950456f
        val y = 0.212671f * r + 0.715160f * g + 0.072169f * b
        val z = (0.019334f * r + 0.119193f * g + 0.950227f * b) / 1.088754f
        val fy = f(y)
        return floatArrayOf(116f * fy - 16f, 500f * (f(x) - fy), 200f * (fy - f(z)))
    }

    /** The grey value (0..1) of a Lab colour with no colour (a = b = 0). */
    private fun greyFromL(l: Float) = gam(fi((l + 16f) / 116f)).coerceIn(0f, 1f)

    private fun labToRgb(l: Float, a: Float, b: Float): Int {
        val fy = (l + 16f) / 116f
        val x = 0.950456f * fi(fy + a / 500f); val y = fi(fy); val z = 1.088754f * fi(fy - b / 200f)
        val r = 3.240479f * x - 1.537150f * y - 0.498535f * z
        val g = -0.969256f * x + 1.875992f * y + 0.041556f * z
        val bl = 0.055648f * x - 0.204043f * y + 1.057311f * z
        fun ch(v: Float) = (gam(max(0f, v)) * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (ch(r) shl 16) or (ch(g) shl 8) or ch(bl)
    }

    // ---------------------------------------------------------------- helpers

    private fun interpreter(ctx: Context, asset: String): Interpreter {
        val bytes = ctx.assets.open(asset).use { it.readBytes() }
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).put(bytes)
        buf.rewind()
        return Interpreter(buf, Interpreter.Options().setNumThreads(4))
    }

    /** Shrinks so the longest side is at most [limit] px (never enlarges). */
    private fun fit(b: Bitmap, limit: Int): Bitmap {
        val soft = if (b.config == Bitmap.Config.HARDWARE) b.copy(Bitmap.Config.ARGB_8888, false) else b
        val longest = max(soft.width, soft.height)
        if (longest <= limit) return soft
        val s = limit.toFloat() / longest
        return Bitmap.createScaledBitmap(soft, (soft.width * s).roundToInt(), (soft.height * s).roundToInt(), true)
    }
}
