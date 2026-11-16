@file:OptIn(ExperimentalForeignApi::class)

package com.coparently.app.data.files

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.io.buffered
import kotlinx.io.files.Path
import kotlinx.io.files.SystemFileSystem
import kotlinx.io.readByteArray
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [IosPickedFiles] (MON-24 phase 3, part C, Task 11): the copy a picker keeps of what was picked,
 * its name, its type and its bytes. Each test works in a Caches folder of its own under the
 * temporary directory.
 */
class IosPickedFilesTest {

    private val caches = NSTemporaryDirectory().trimEnd('/') + "/coplanly-picked-" + NSUUID().UUIDString

    private val files = IosPickedFiles(caches)

    @AfterTest
    fun removeTheFolder() {
        NSFileManager.defaultManager.removeItemAtPath(caches, error = null)
    }

    @Test
    fun `a name with a slash and accents is kept readable and stays inside picked`() {
        val bytes = "%PDF-1.4\n%%EOF\n".encodeToByteArray()
        val source = write("source.pdf", bytes)

        val copy = files.keep(source, "Účet / září.pdf")

        val picked = "$caches/${IosPickedFiles.PICKED_DIRECTORY}/"
        assertTrue(copy.startsWith(picked), "The copy $copy is not under $picked")
        val inside = copy.removePrefix(picked)
        assertEquals(2, inside.split('/').size, "The copy $copy is not one folder deep under picked")
        assertEquals("Účet _ září.pdf", inside.substringAfter('/'))
        assertEquals("Účet / září.pdf", files.displayNameOf(copy))
        assertEquals("Účet / září.pdf", files.displayNameOf("file://" + copy.replace(" ", "%20")))
        val read = SystemFileSystem.source(Path(copy)).buffered().use { it.readByteArray() }
        assertContentEquals(bytes, read)
        val opened = files.open(copy).buffered().use { it.readByteArray() }
        assertContentEquals(bytes, opened)
    }

    @Test
    fun `a name of dots or of nothing kept is still one file inside its folder`() {
        assertEquals(IosPickedFiles.FALLBACK_NAME, IosPickedFiles.sanitise(".."))
        assertEquals(IosPickedFiles.FALLBACK_NAME, IosPickedFiles.sanitise(""))
        assertEquals(".._etc_passwd", IosPickedFiles.sanitise("../etc/passwd"))
        assertEquals("a_b_c.txt", IosPickedFiles.sanitise("a:b*c.txt"))
    }

    @Test
    fun `typeOf maps pdf - jpeg and heic`() {
        assertEquals("application/pdf", files.typeOf("/any/where/order.pdf"))
        assertEquals("image/jpeg", files.typeOf("/any/where/receipt.jpg"))
        assertEquals("image/jpeg", files.typeOf("/any/where/receipt.JPEG"))
        assertEquals("image/heic", files.typeOf("/any/where/IMG_0001.heic"))
        assertNull(files.typeOf("/any/where/no-extension"))
    }

    @Test
    fun `a file not kept by the picker is named by its last segment`() {
        assertEquals("photo-1.jpg", files.displayNameOf("/somewhere/shared_files/capture/photo-1.jpg"))
    }

    private fun write(name: String, bytes: ByteArray): String {
        val folder = Path(caches, "source")
        SystemFileSystem.createDirectories(folder)
        val file = Path(folder, name)
        SystemFileSystem.sink(file).buffered().use { it.write(bytes) }
        return file.toString()
    }
}
