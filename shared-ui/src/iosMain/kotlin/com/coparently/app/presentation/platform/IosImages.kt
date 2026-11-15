package com.coparently.app.presentation.platform

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.platform.logger
import com.coparently.app.data.remote.firebase.SignedInUser
import com.coparently.app.domain.files.RecordPhotoAccess
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoPaths
import com.coparently.app.domain.files.ViewablePhoto
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import org.jetbrains.skia.Codec
import org.jetbrains.skia.Data
import org.jetbrains.skia.EncodedOrigin
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import org.jetbrains.skia.impl.use
import org.koin.mp.KoinPlatform
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.UIKit.UIApplicationDidReceiveMemoryWarningNotification
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.posix.memcpy
import kotlin.math.max
import kotlin.math.roundToInt
import androidx.compose.foundation.Image as ComposeImage

/**
 * The iPhone's image loading (MON-24 phase 3, part C): what Coil 2 and the app's
 * `RecordPhotoFetcher` are on Android.
 *
 * It draws three kinds of model ([IosImageSources]): a local file (a kotlinx-io `Path`, an absolute
 * path or a `file://` address — a chat attachment's checked thumbnail, a photo just picked), and a
 * record photograph, as the [ViewablePhoto] a screen built with `rememberRecordPhoto` or as its
 * stored `ph1|…` reference. A record photograph is downloaded **as the signed-in parent** through
 * [SharedFileCache], which keeps a copy only when its digest matches, and every path is checked
 * again against the uid signed in now (Legal item 8): never a download URL, and nothing for a viewer
 * the path does not name. Anything else — a web address, a legacy value — is never fetched; the
 * placeholder stands in.
 *
 * The bytes are decoded by Skia on [Dispatchers.Default] and, unless `fullResolution` is asked for,
 * scaled down to the size the image is laid out at; UIKit decodes what Skia cannot (a HEIC picked
 * from the photo library) or what it would draw on its side (a photo whose EXIF orientation turns
 * it). Images decoded at their laid-out size are kept in memory, up to a budget of decoded bytes
 * ([IosImageMemory]); a whole photograph (the zoomable viewer's) is never kept.
 */
internal class IosImages(private val loader: IosImageLoader) : PlatformImages {

    override val available: Boolean = true

    @Composable
    override fun Image(
        model: Any?,
        contentDescription: String?,
        modifier: Modifier,
        contentScale: ContentScale,
        fullResolution: Boolean,
        placeholder: (@Composable () -> Unit)?,
    ) {
        val source = remember(model) { loader.sourceOf(model) }
        // The largest size this image has been laid out at, so a growing frame decodes again and a
        // shrinking one does not.
        var laidOut by remember(source) { mutableStateOf<IntSize?>(null) }
        var bitmap by remember(source, fullResolution) {
            mutableStateOf(source?.let { loader.cached(it, laidOut, fullResolution) })
        }
        LaunchedEffect(source, laidOut, fullResolution) {
            val target = laidOut
            if (source == null || (!fullResolution && target == null)) return@LaunchedEffect
            bitmap = loader.load(source, target, fullResolution) ?: bitmap
        }
        val shown = bitmap
        Box(
            modifier = modifier.onSizeChanged { size ->
                val before = laidOut
                if (before == null || size.width > before.width || size.height > before.height) {
                    laidOut = IntSize(max(size.width, before?.width ?: 0), max(size.height, before?.height ?: 0))
                }
            },
            contentAlignment = Alignment.Center,
            // The image fills a frame the caller sized, as Coil's does; a placeholder (an initial)
            // keeps its own size and is centred, as on Android.
            propagateMinConstraints = shown != null
        ) {
            if (shown != null) {
                ComposeImage(bitmap = shown, contentDescription = contentDescription, contentScale = contentScale)
            } else {
                placeholder?.invoke()
            }
        }
    }

    companion object {
        /**
         * The images the shell draws with: the [SharedFileCache] and the signed-in uid come from
         * the app's Koin graph, looked up at each load, so a session that starts after the shell
         * (or a test with no graph) draws placeholders rather than failing.
         */
        fun fromKoin(): IosImages = IosImages(
            IosImageLoader(
                photoCache = { koinOrNull { getOrNull<SharedFileCache>() } },
                viewerUid = { koinOrNull { getOrNull<SignedInUser>() }?.getCurrentUid() }
            )
        )

        private inline fun <T> koinOrNull(read: org.koin.core.Koin.() -> T?): T? =
            runCatching { KoinPlatform.getKoinOrNull()?.read() }.getOrNull()
    }
}

/** Where an image's bytes come from, once a model has been checked ([IosImageSources.of]). */
internal sealed interface IosImageSource {

    /** The memory cache's key: a file's path, a photograph's digest (which names its bytes). */
    val cacheKey: String

    /** A file on this phone. */
    data class LocalFile(val path: Path) : IosImageSource {
        override val cacheKey: String get() = "file:$path"
    }

    /**
     * A record photograph at one of [paths], each of which the viewer may read, checked against
     * [sha256] as it is downloaded.
     */
    data class StoredPhoto(val paths: List<String>, val sha256: String) : IosImageSource {
        override val cacheKey: String get() = "photo:$sha256"
    }
}

/** Which models the iPhone draws, and for whom (pure, so every rule is a test). */
internal object IosImageSources {

    /**
     * The source of [model] for [viewerUid], or null — draw the placeholder, fetch nothing — for a
     * model it does not read, and for a record photograph none of whose paths the viewer may read.
     */
    fun of(model: Any?, viewerUid: String?): IosImageSource? = when (model) {
        is Path -> IosImageSource.LocalFile(model)
        is ViewablePhoto -> storedPhoto(model.paths, model.sha256, viewerUid)
        is String -> ofText(model, viewerUid)
        else -> null
    }

    private fun ofText(text: String, viewerUid: String?): IosImageSource? = when {
        text.startsWith(RecordPhotoCodec.PREFIX) -> ofReference(text, viewerUid)
        text.startsWith(FILE_SCHEME) -> NSURL.URLWithString(text)?.path?.let { IosImageSource.LocalFile(Path(it)) }
        text.startsWith("/") -> IosImageSource.LocalFile(Path(text))
        else -> null
    }

    /**
     * A bare `ph1|…` reference, read as its own record's: the kind, record and family its path
     * names, through the same [RecordPhotoAccess.viewable] a screen's `rememberRecordPhoto` calls.
     */
    private fun ofReference(reference: String, viewerUid: String?): IosImageSource? =
        RecordPhotoCodec.decode(reference)
            ?.let { RecordPhotoPaths.parse(it.storagePath) }
            ?.let { path ->
                val family = path.owner.takeIf { path.soloUid == null }
                RecordPhotoAccess.viewable(reference, path.kind, path.recordId, family, viewerUid)
            }
            ?.let { storedPhoto(it.paths, it.sha256, viewerUid) }

    private fun storedPhoto(paths: List<String>, sha256: String, viewerUid: String?): IosImageSource? =
        paths.filter { RecordPhotoPaths.mayRead(it, viewerUid) }
            .takeIf { it.isNotEmpty() }
            ?.let { IosImageSource.StoredPhoto(it, sha256) }

    private const val FILE_SCHEME = "file://"
}

/**
 * Loads and decodes an [IosImageSource], as `RecordPhotoFetcher` and Coil's decoder do on Android.
 * Every failure — no graph, no bucket (`NoStorageBucket` until Storage is bound), a refused or
 * missing object, a wrong digest, bytes that are not an image — is null, which draws the
 * placeholder, and a later load tries again.
 *
 * @param photoCache The verified download cache, or null when there is none to ask
 * @param viewerUid The uid signed in now
 */
internal class IosImageLoader(
    private val photoCache: () -> SharedFileCache?,
    private val viewerUid: () -> String?,
    private val memory: IosImageMemory = IosImageMemory.shared,
) {

    /** [model]'s source for the parent signed in now ([IosImageSources.of]). */
    fun sourceOf(model: Any?): IosImageSource? = IosImageSources.of(model, viewerUid())

    /** [model] decoded at most at [target] (or whole), or null; resolves it first ([sourceOf]). */
    suspend fun loadModel(model: Any?, target: IntSize?, fullResolution: Boolean): ImageBitmap? =
        sourceOf(model)?.let { load(it, target, fullResolution) }

    /** What [memory] already holds for [source] at this size, without loading. */
    fun cached(source: IosImageSource, target: IntSize?, fullResolution: Boolean): ImageBitmap? =
        boundOf(target, fullResolution)?.takeIf { mayShow(source) }?.let { memory[keyOf(source, it)] }

    /** [source] decoded, scaled down to [target] unless [fullResolution], or null. */
    suspend fun load(source: IosImageSource, target: IntSize?, fullResolution: Boolean): ImageBitmap? {
        // A photograph decoded for one account is not shown to the next from memory either.
        if (!mayShow(source)) return null
        // A whole photograph (12 MP is about 48 MB decoded) is held by the screen showing it, never
        // by the cache: a few of them would have the phone end the app for its memory.
        val bound = boundOf(target, fullResolution) ?: return decoded(source, null)
        val key = keyOf(source, bound)
        return memory[key] ?: decoded(source, bound)?.also { memory[key] = it }
    }

    /** The size to decode at, or null for the whole image. */
    private fun boundOf(target: IntSize?, fullResolution: Boolean): IntSize? = target.takeUnless { fullResolution }

    private suspend fun decoded(source: IosImageSource, bound: IntSize?): ImageBitmap? =
        bytesOf(source)?.let { bytes -> withContext(Dispatchers.Default) { IosImageDecoder.decode(bytes, bound) } }

    private fun mayShow(source: IosImageSource): Boolean = when (source) {
        is IosImageSource.LocalFile -> true
        is IosImageSource.StoredPhoto -> {
            val uid = viewerUid()
            source.paths.any { RecordPhotoPaths.mayRead(it, uid) }
        }
    }

    /** The bytes behind [source], or null when they cannot be had. */
    internal suspend fun bytesOf(source: IosImageSource): ByteArray? = when (source) {
        is IosImageSource.LocalFile -> withContext(Dispatchers.Default) { readFile(source.path) }
        is IosImageSource.StoredPhoto -> downloadPhoto(source)
    }

    private suspend fun downloadPhoto(source: IosImageSource.StoredPhoto): ByteArray? {
        val cache = photoCache() ?: return null
        // Checked again against the uid signed in now: a model built for one account is never
        // fetched for another.
        val uid = viewerUid()
        for (path in source.paths.filter { RecordPhotoPaths.mayRead(it, uid) }) {
            try {
                val file = cache.localCopy(path, RecordPhotoPaths.objectNameOf(path), source.sha256)
                return withContext(Dispatchers.Default) { readFile(file) }
            } catch (e: CancellationException) {
                throw e
            } catch (
                // A missing object at the family path is expected before the move; try the next.
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                logger.w(TAG, "A record photo could not be loaded: ${e::class.simpleName}")
            }
        }
        return null
    }

    private fun readFile(path: Path): ByteArray? = runCatching {
        val size = SystemFileSystem.metadataOrNull(path)?.takeIf { it.isRegularFile }?.size ?: return null
        if (size > MAX_IMAGE_BYTES) return null
        SystemFileSystem.source(path).buffered().use { it.readByteArray() }
    }.getOrNull()

    private fun keyOf(source: IosImageSource, bound: IntSize): String =
        "${source.cacheKey}@${bound.width}x${bound.height}"

    private companion object {
        const val TAG = "IosImages"

        /** Above anything a record photo (under 10 MB) or a shared file (under 20 MB) can be. */
        const val MAX_IMAGE_BYTES = 32L * 1024L * 1024L
    }
}

/**
 * Decoded images, the most recently used of them up to [budgetBytes] decoded bytes (four per pixel).
 * Touched only on the main thread: a load reads and writes it from the composition's coroutine,
 * before and after the decode, and the memory warning clears it on the main queue.
 */
internal class IosImageMemory(private val budgetBytes: Long) {

    private val entries = LinkedHashMap<String, ImageBitmap>()

    /** The decoded bytes held now. */
    var sizeBytes: Long = 0
        private set

    /** How many images are held. */
    val count: Int get() = entries.size

    /** The image under [key], now the most recently used, or null. */
    operator fun get(key: String): ImageBitmap? = entries.remove(key)?.also { entries[key] = it }

    /**
     * Keeps [image] under [key], dropping the least recently used until the budget holds; an image
     * larger than the whole budget is not kept.
     */
    operator fun set(key: String, image: ImageBitmap) {
        entries.remove(key)?.let { sizeBytes -= bytesOf(it) }
        val size = bytesOf(image)
        if (size > budgetBytes) return
        entries[key] = image
        sizeBytes += size
        while (sizeBytes > budgetBytes) {
            val oldest = entries.keys.first()
            entries.remove(oldest)?.let { sizeBytes -= bytesOf(it) }
        }
    }

    /** Drops every image. */
    fun clear() {
        entries.clear()
        sizeBytes = 0
    }

    companion object {
        /** 64 MB: a screen's thumbnails and photo strips several times over. */
        const val BUDGET_BYTES: Long = 64L * 1024L * 1024L

        /** The app's one cache, emptied when iOS warns that memory is low. */
        val shared: IosImageMemory by lazy {
            IosImageMemory(BUDGET_BYTES).also { memory ->
                NSNotificationCenter.defaultCenter.addObserverForName(
                    name = UIApplicationDidReceiveMemoryWarningNotification,
                    `object` = null,
                    queue = NSOperationQueue.mainQueue
                ) { _ -> memory.clear() }
            }
        }

        private const val BYTES_PER_PIXEL = 4L

        /** What [image] holds decoded: four bytes a pixel, as Skia's N32 bitmaps do. */
        fun bytesOf(image: ImageBitmap): Long = image.width.toLong() * image.height.toLong() * BYTES_PER_PIXEL
    }
}

/** Bytes to an image: Skia first, UIKit for what Skia does not read (HEIC). */
internal object IosImageDecoder {

    /** [bytes] decoded, scaled down to cover [bound] (when given and smaller), or null. */
    fun decode(bytes: ByteArray, bound: IntSize?): ImageBitmap? {
        val image = (if (isUpright(bytes)) skiaImage(bytes) else null)
            ?: uiKitImage(bytes, bound)?.let { skiaImage(it) }
            ?: return null
        val scaled = runCatching { scaledDown(image, bound) }.getOrNull()
        // `toComposeImageBitmap` draws the image into a bitmap of its own, so the Skia image can
        // be closed once it has.
        val bitmap = scaled?.let { runCatching { it.toComposeImageBitmap() }.getOrNull() }
        scaled?.close()
        if (scaled !== image) image.close()
        return bitmap
    }

    private fun skiaImage(bytes: ByteArray): Image? = runCatching {
        Image.makeFromEncoded(bytes).takeIf { it.width > 0 && it.height > 0 }
    }.getOrNull()

    /**
     * Whether [bytes] draw as they are stored: no EXIF orientation that turns them. Skia's own
     * decoder is not relied on to apply one, so a turned photo goes through UIKit, which does.
     * Bytes Skia's codec cannot read count as upright, and Skia or UIKit then decide.
     */
    private fun isUpright(bytes: ByteArray): Boolean = runCatching {
        Data.makeFromBytes(bytes).use { data ->
            Codec.makeFromData(data).use { it.encodedOrigin == EncodedOrigin.TOP_LEFT }
        }
    }.getOrDefault(true)

    /**
     * [bytes] through `UIImage` (which reads HEIC and applies the photo's orientation), drawn at most
     * at [bound] and written back as PNG for Skia.
     */
    @Suppress("ReturnCount") // one guard per thing UIKit may not give back
    @OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
    private fun uiKitImage(bytes: ByteArray, bound: IntSize?): ByteArray? {
        if (bytes.isEmpty()) return null
        val data = bytes.usePinned { NSData.create(bytes = it.addressOf(0), length = bytes.size.toULong()) }
        val image = UIImage.imageWithData(data) ?: return null
        val (width, height) = image.size.useContents { width to height }
        if (width <= 0.0 || height <= 0.0) return null
        val factor = coverFactor(width, height, bound)
        val format = UIGraphicsImageRendererFormat.preferredFormat().apply { scale = 1.0 }
        val renderer = UIGraphicsImageRenderer(size = CGSizeMake(width * factor, height * factor), format = format)
        val png = renderer.PNGDataWithActions { _ ->
            image.drawInRect(CGRectMake(0.0, 0.0, width * factor, height * factor))
        }
        val length = png.length.toInt()
        if (length == 0) return null
        return ByteArray(length).also { out -> out.usePinned { memcpy(it.addressOf(0), png.bytes, png.length) } }
    }

    private fun scaledDown(image: Image, bound: IntSize?): Image {
        val factor = coverFactor(image.width.toDouble(), image.height.toDouble(), bound)
        if (factor >= 1.0) return image
        val width = max(1, (image.width * factor).roundToInt())
        val height = max(1, (image.height * factor).roundToInt())
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat()),
            SamplingMode.MITCHELL,
            null,
            true
        )
        val scaled = surface.makeImageSnapshot()
        surface.close()
        return scaled
    }

    /**
     * The scale that makes a [width] by [height] image just cover [bound] — so a cropped frame is
     * still filled — capped at 1: an image is never scaled up. 1 without a bound.
     */
    private fun coverFactor(width: Double, height: Double, bound: IntSize?): Double {
        if (bound == null || bound.width <= 0 || bound.height <= 0) return 1.0
        return minOf(1.0, max(bound.width / width, bound.height / height))
    }
}
