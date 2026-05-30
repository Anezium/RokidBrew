package com.rokidbrew.phone

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

internal fun decodeSampledBitmap(bytes: ByteArray, maxDimensionPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
}

internal fun decodeSampledBitmap(file: File, maxDimensionPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxDimensionPx)
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    return BitmapFactory.decodeFile(file.absolutePath, options)
}

private fun sampleSizeFor(width: Int, height: Int, maxDimensionPx: Int): Int {
    var sampleSize = 1
    val largestDimension = maxOf(width, height)
    while (largestDimension / (sampleSize * 2) >= maxDimensionPx) {
        sampleSize *= 2
    }
    return sampleSize
}
