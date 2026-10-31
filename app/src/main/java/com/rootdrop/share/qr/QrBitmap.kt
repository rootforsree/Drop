package com.rootdrop.share.qr

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/**
 * Encodes [content] as a QR code bitmap: black modules on a white
 * background, error correction level M, UTF-8 charset, quiet zone margin 2.
 * Pure function with no camera code and no UI code beyond [Bitmap].
 *
 * Throws [IllegalArgumentException] when the content is empty, the size is
 * not positive, or ZXing cannot encode the content.
 */
fun qrBitmap(
    content: String,
    sizePx: Int = 1024,
): Bitmap {
    require(content.isNotEmpty()) { "QR content must not be empty" }
    require(sizePx > 0) { "QR size must be positive" }
    val hints: Map<EncodeHintType, Any> = mapOf(
        EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        EncodeHintType.CHARACTER_SET to "UTF-8",
        EncodeHintType.MARGIN to 2,
    )
    val matrix: BitMatrix = try {
        QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    } catch (e: WriterException) {
        throw IllegalArgumentException("Unable to encode QR content", e)
    }
    val bitmap: Bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (x: Int in 0 until matrix.width) {
        for (y: Int in 0 until matrix.height) {
            val pixel: Int = if (matrix.get(x, y)) Color.BLACK else Color.WHITE
            bitmap.setPixel(x, y, pixel)
        }
    }
    return bitmap
}
