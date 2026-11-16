package com.coparently.app.presentation.platform

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize
import com.coparently.app.data.files.SharedFileCache
import com.coparently.app.data.files.SharedFileStorage
import com.coparently.app.data.files.StorageBucket
import com.coparently.app.data.platform.AppDirectories
import com.coparently.app.domain.family.FamilyKey
import com.coparently.app.domain.files.RecordPhoto
import com.coparently.app.domain.files.RecordPhotoCodec
import com.coparently.app.domain.files.RecordPhotoKind
import com.coparently.app.domain.files.RecordPhotoPaths
import com.coparently.app.domain.files.ViewablePhoto
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The iPhone's images (MON-24 phase 3, part C, Task 9): a record photograph is fetched only for
 * one of the two parents its path names (Legal item 8), and anything that is not an image — or not
 * there — draws the placeholder rather than a broken image.
 */
class IosImagesTest {

    private val root = Path(NSTemporaryDirectory(), "ios-images-${NSUUID().UUIDString}")
    private val bucket = CountingBucket()
    private val directories = object : AppDirectories {
        override val files: Path = Path(root, "files")
        override val cache: Path = Path(root, "cache")
        override val noBackup: Path = Path(root, "no_backup")
    }
    private val cache = SharedFileCache(directories, SharedFileStorage(bucket))
    private val family = FamilyKey.of(ALICE, BOB)
    private val familyPath = RecordPhotoPaths.build(RecordPhotoKind.PET, family, ALICE, RECORD, "a.jpg")
    private val reference = RecordPhotoCodec.encode(RecordPhoto(familyPath, "image/jpeg", 10, SHA256))

    private val memory = IosImageMemory(IosImageMemory.BUDGET_BYTES)

    private fun loader(viewer: String?) = IosImageLoader(
        photoCache = { cache },
        viewerUid = { viewer },
        memory = memory
    )

    @Test
    fun `a ph1 reference outside the viewer's family resolves to nothing`() = runTest {
        val outsider = loader(CAROL)
        assertNull(outsider.sourceOf(reference))
        assertNull(outsider.sourceOf(ViewablePhoto(listOf(familyPath), "image/jpeg", SHA256)))
        assertNull(outsider.loadModel(reference, IntSize(100, 100), fullResolution = false))
        assertNull(outsider.loadModel(ViewablePhoto(listOf(familyPath), "image/jpeg", SHA256), null, true))
        assertEquals(0, bucket.downloads)
    }

    @Test
    fun `a ph1 reference of the viewer's family is fetched as them and fails quietly`() = runTest {
        val parent = loader(BOB)
        val source = assertIs<IosImageSource.StoredPhoto>(parent.sourceOf(reference))
        assertEquals(listOf(familyPath), source.paths)
        // The bucket refuses (as the rules refuse a stranger): no image, no throw.
        assertNull(parent.loadModel(reference, IntSize(100, 100), fullResolution = false))
        assertEquals(1, bucket.downloads)
    }

    @Test
    fun `a legacy value or a web address is never fetched`() = runTest {
        val parent = loader(ALICE)
        assertNull(parent.sourceOf("https://firebasestorage.googleapis.com/v0/b/x/o/pet_photos%2Fa.jpg"))
        assertNull(parent.sourceOf("pet_photos/$ALICE/$RECORD/a.jpg"))
        assertNull(parent.sourceOf(null))
        assertEquals(0, bucket.downloads)
    }

    @Test
    fun `a file that is not an image draws the placeholder`() = runTest {
        val file = write("not-an-image.jpg", "This is a receipt's text, not its photograph.".encodeToByteArray())
        val images = loader(ALICE)
        assertIs<IosImageSource.LocalFile>(images.sourceOf(file))
        assertNull(images.loadModel(file, IntSize(64, 64), fullResolution = false))
        assertNull(images.loadModel(file.toString(), null, fullResolution = true))
        assertNull(images.loadModel("file://$file", null, fullResolution = true))
        assertNull(images.loadModel(Path(root, "missing.png"), null, fullResolution = true))
    }

    @Test
    fun `an image is scaled down to its frame unless shown at full resolution`() = runTest {
        val matrix = assertNotNull(IosQrCodes(UnavailableShareSheet).encode("CoPlanly"))
        val file = write("code.png", assertNotNull(QrPicture.png(matrix)))
        val images = loader(ALICE)
        val whole = assertNotNull(images.loadModel(file, null, fullResolution = true))
        val thumbnail = assertNotNull(images.loadModel(file, IntSize(THUMBNAIL, THUMBNAIL), fullResolution = false))
        assertTrue(whole.width > THUMBNAIL)
        assertEquals(THUMBNAIL, thumbnail.width)
        assertEquals(THUMBNAIL, thumbnail.height)
    }

    @Test
    fun `images past the byte budget evict the oldest`() {
        // 100 by 100 is 40 000 decoded bytes; two fit in 100 000, a third pushes the first out.
        val small = IosImageMemory(budgetBytes = 100_000)
        small["a"] = ImageBitmap(100, 100)
        small["b"] = ImageBitmap(100, 100)
        assertEquals(80_000, small.sizeBytes)
        small["c"] = ImageBitmap(100, 100)
        assertNull(small["a"])
        assertNotNull(small["b"])
        assertNotNull(small["c"])
        assertEquals(80_000, small.sizeBytes)
        // An image larger than the whole budget is not kept at all.
        small["huge"] = ImageBitmap(200, 200)
        assertNull(small["huge"])
        assertEquals(2, small.count)
        small.clear()
        assertEquals(0, small.count)
        assertEquals(0, small.sizeBytes)
    }

    @Test
    fun `a full-resolution load is not retained`() = runTest {
        val matrix = assertNotNull(IosQrCodes(UnavailableShareSheet).encode("CoPlanly"))
        val file = write("whole.png", assertNotNull(QrPicture.png(matrix)))
        val images = loader(ALICE)
        assertNotNull(images.loadModel(file, IntSize(THUMBNAIL, THUMBNAIL), fullResolution = true))
        assertEquals(0, memory.count)
        val source = assertNotNull(images.sourceOf(file))
        assertNull(images.cached(source, IntSize(THUMBNAIL, THUMBNAIL), fullResolution = true))
        assertNotNull(images.loadModel(file, IntSize(THUMBNAIL, THUMBNAIL), fullResolution = false))
        assertEquals(1, memory.count)
        assertNotNull(images.cached(source, IntSize(THUMBNAIL, THUMBNAIL), fullResolution = false))
    }

    @OptIn(ExperimentalEncodingApi::class)
    @Test
    fun `a photo turned by its EXIF orientation is drawn upright`() {
        // An 8 by 4 JPEG whose EXIF orientation 6 says "turn a quarter clockwise": 4 by 8 on screen.
        val jpeg = Base64.decode(ORIENTATION_6_JPEG)
        val drawn = assertNotNull(IosImageDecoder.decode(jpeg, bound = null))
        assertEquals(4, drawn.width)
        assertEquals(8, drawn.height)
    }

    private fun write(name: String, bytes: ByteArray): Path {
        SystemFileSystem.createDirectories(root)
        val file = Path(root, name)
        SystemFileSystem.sink(file).buffered().use { it.write(bytes) }
        return file
    }

    /** A bucket that counts downloads and refuses each, as an offline or unbound one would. */
    private class CountingBucket : StorageBucket {
        var downloads = 0

        override suspend fun customMetadata(path: String, key: String): String? = null

        override suspend fun putFile(
            path: String,
            file: Path,
            contentType: String,
            customMetadata: Map<String, String>,
            onProgress: (Float) -> Unit
        ) = throw IOException("not in this test")

        override suspend fun putBytes(
            path: String,
            bytes: ByteArray,
            contentType: String,
            customMetadata: Map<String, String>
        ) = throw IOException("not in this test")

        override suspend fun getFile(path: String, target: Path) {
            downloads++
            throw IOException("refused")
        }

        override suspend fun delete(path: String) = Unit
    }

    private object UnavailableShareSheet : ShareSheet {
        override val available: Boolean = false

        override fun shareText(text: String, chooserTitle: String) = Unit

        override fun shareFile(contentUri: String, mimeType: String, chooserTitle: String) = Unit
    }

    private companion object {
        const val ALICE = "alice-uid"
        const val BOB = "bob-uid"
        const val CAROL = "carol-uid"
        const val RECORD = "pet-1"
        const val SHA256 = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
        const val THUMBNAIL = 48

        /** An 8 by 4 JPEG with EXIF orientation 6, made with Pillow 12.3 (`exif[0x0112] = 6`). */
        const val ORIENTATION_6_JPEG =
            "/9j/4AAQSkZJRgABAQAAAQABAAD/4QAiRXhpZgAATU0AKgAAAAgAAQESAAMAAAABAAYAAAAAAAD/2wBDAAYEBQYFBAYGBQYH" +
                "BwYIChAKCgkJChQODwwQFxQYGBcUFhYaHSUfGhsjHBYWICwgIyYnKSopGR8tMC0oMCUoKSj/2wBDAQcHBwoIChMKChMoGhYa" +
                "KCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCgoKCj/wAARCAAEAAgDASIAAhEBAxEB/8QA" +
                "HwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUFBAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKB" +
                "kaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVWV1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWG" +
                "h4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi4+Tl5ufo6erx8vP09fb3+Pn6/8QA" +
                "HwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAECAxEEBSExBhJBUQdhcRMiMoEI" +
                "FEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVmZ2hpanN0dXZ3eHl6goOE" +
                "hYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq8vP09fb3+Pn6/9oA" +
                "DAMBAAIRAxEAPwDgKKKK+eP2E//Z"
    }
}
