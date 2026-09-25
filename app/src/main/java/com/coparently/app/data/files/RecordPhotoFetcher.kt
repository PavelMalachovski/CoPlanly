package com.coparently.app.data.files

import android.content.Context
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.domain.files.RecordPhotoPaths
import com.coparently.app.domain.files.ViewablePhoto
import kotlinx.coroutines.CancellationException
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads a [ViewablePhoto] for Coil (L-4): downloads it through the Storage SDK **as the signed-in
 * user** into [SharedFileCache], which keeps a copy only once its SHA-256 matches, and decodes
 * that copy. Never a download URL, so `storage.rules` decides who sees the photograph.
 *
 * The paths are tried in order — a record's family folder first, then, for the uploader only,
 * their own `solo_` folder (see `RecordPhotoPaths.candidatesFor`) — and each is checked again
 * against the uid signed in *now*: a model built for one account is never fetched for another.
 */
class RecordPhotoFetcher(
    private val photo: ViewablePhoto,
    private val cache: SharedFileCache,
    private val currentUid: () -> String?
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val uid = currentUid()
        var failure: Exception? = null
        for (path in photo.paths.filter { RecordPhotoPaths.mayRead(it, uid) }) {
            try {
                val file = cache.localCopy(path, RecordPhotoPaths.objectNameOf(path), photo.sha256)
                return SourceResult(
                    source = ImageSource(file = file.toOkioPath(), fileSystem = FileSystem.SYSTEM),
                    mimeType = photo.contentType,
                    dataSource = DataSource.DISK
                )
            } catch (e: CancellationException) {
                throw e
            } catch (
                // A missing object at the family path is expected before the move; try the next.
                @Suppress("TooGenericExceptionCaught") e: Exception
            ) {
                failure = e
            }
        }
        throw failure ?: IOException("No readable path for this photo")
    }

    /** Hands Coil a [RecordPhotoFetcher] for every [ViewablePhoto] model. */
    class Factory(
        private val cache: SharedFileCache,
        private val currentUid: () -> String?
    ) : Fetcher.Factory<ViewablePhoto> {
        override fun create(data: ViewablePhoto, options: Options, imageLoader: ImageLoader): Fetcher =
            RecordPhotoFetcher(data, cache, currentUid)
    }
}

/**
 * The memory-cache key of a [ViewablePhoto]: its digest, which names the bytes exactly, so the same
 * photograph on two screens is decoded once.
 */
class RecordPhotoKeyer : Keyer<ViewablePhoto> {
    override fun key(data: ViewablePhoto, options: Options): String = "record-photo:${data.sha256}"
}

/**
 * Builds the app's Coil [ImageLoader] with the record-photo fetcher registered (L-4). The
 * application hands it to Coil through `ImageLoaderFactory`, so every `AsyncImage` in the app can be
 * given a [ViewablePhoto].
 */
@Singleton
class RecordPhotoImageLoader @Inject constructor(
    private val cache: SharedFileCache,
    private val authService: FirebaseAuthService
) {

    /** A new loader for [context] that also knows how to fetch a [ViewablePhoto]. */
    fun build(context: Context): ImageLoader = ImageLoader.Builder(context)
        .components {
            add(RecordPhotoFetcher.Factory(cache) { authService.getCurrentUser()?.uid })
            add(RecordPhotoKeyer())
        }
        .build()
}
