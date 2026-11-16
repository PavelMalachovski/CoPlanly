@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.gate

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGRectMake
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

/** A photo for the gate to upload as a record's (part C, Task 11), drawn rather than shipped. */
internal object GateImages {

    /** Writes a plain JPEG of [width] × [height] pixels to [file], creating its folder; its path. */
    fun writeJpeg(file: Path, width: Int, height: Int): String {
        val space = CGColorSpaceCreateDeviceRGB()
        val context = CGBitmapContextCreate(
            null,
            width.toULong(),
            height.toULong(),
            BITS_PER_COMPONENT,
            0u,
            space,
            CGImageAlphaInfo.kCGImageAlphaNoneSkipLast.value
        )
        CGContextSetRGBFillColor(context, 0.9, 0.5, 0.2, 1.0)
        CGContextFillRect(context, CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()))
        val image = CGBitmapContextCreateImage(context)
        val data = checkNotNull(UIImageJPEGRepresentation(UIImage.imageWithCGImage(image), QUALITY)) {
            "The gate's photo could not be encoded"
        }
        CGImageRelease(image)
        CGContextRelease(context)
        CGColorSpaceRelease(space)
        val bytes = ByteArray(data.length.toInt())
        if (bytes.isNotEmpty()) bytes.usePinned { memcpy(it.addressOf(0), data.bytes, data.length) }
        file.parent?.let { SystemFileSystem.createDirectories(it) }
        SystemFileSystem.sink(file).buffered().use { it.write(bytes) }
        return file.toString()
    }

    private const val BITS_PER_COMPONENT = 8uL
    private const val QUALITY = 0.9
}
