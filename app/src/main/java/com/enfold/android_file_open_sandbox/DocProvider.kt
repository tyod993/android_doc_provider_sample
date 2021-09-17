package com.enfold.android_file_open_sandbox
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.annotation.RequiresApi
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.*

class DocProvider : DocumentsProvider() {

    companion object {
        private val DEFAULT_ROOT_PROJECTION =
            arrayOf(
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_MIME_TYPES,
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.COLUMN_ICON,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_SUMMARY,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
            )

        private val DEFAULT_DOCUMENT_PROJECTION =
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE
            )

        private const val MAX_SEARCH_RESULTS = 20
        private const val MAX_LAST_MODIFIED = 5

        private const val ROOT = "root"

        private val TAG = DocProvider::class.simpleName
    }

    private var mBaseDir: File? = null

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(): Boolean {
        Log.v(TAG, "onCreate")
        mBaseDir = context!!.filesDir
        return true
    }

    override fun queryRoots(projection: Array<String>?): Cursor {
        Log.v(TAG, "queryRoots")

        val result = MatrixCursor(resolveRootProjection(projection))

        val row = result.newRow()
        row.add(DocumentsContract.Root.COLUMN_ROOT_ID, ROOT)
        row.add(DocumentsContract.Root.COLUMN_SUMMARY, context!!.getString(R.string.app_name))
        row.add(
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.FLAG_SUPPORTS_CREATE or
                    DocumentsContract.Root.FLAG_SUPPORTS_RECENTS or
                    DocumentsContract.Root.FLAG_SUPPORTS_SEARCH
        )

        row.add(DocumentsContract.Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
        row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, getDocIdForFile(mBaseDir))
        row.add(DocumentsContract.Root.COLUMN_MIME_TYPES, getChildMimeTypes())
        row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, mBaseDir!!.freeSpace)
        row.add(DocumentsContract.Root.COLUMN_ICON, R.drawable.ic_launcher_foreground)

        return result
    }

    @Throws(FileNotFoundException::class)
    override fun queryRecentDocuments(rootId: String, projection: Array<String>?): Cursor {
        Log.v(TAG, "queryRecentDocuments")

        // Create a cursor with the requested projection, or the default projection.
        val result = MatrixCursor(resolveDocumentProjection(projection))
        val parent = getFileForDocId(rootId)

        // Create a queue to store the most recent documents, which orders by last modified.
        val lastModifiedFiles =
            PriorityQueue(5) { i: File?, j: File? ->
                i!!.lastModified().compareTo(j!!.lastModified())
            }

        val pending = LinkedList<File?>()

        // Start by adding the parent to the list of files to be processed
        pending.add(parent)

        // Do while we still have unexamined files
        while (!pending.isEmpty()) {
            // Take a file from the list of unprocessed files
            val file = pending.removeFirst()
            if (file!!.isDirectory) {
                // If it's a directory, add all its children to the unprocessed list
                Collections.addAll(pending, *Objects.requireNonNull(file.listFiles()))
            } else {
                // If it's a file, add it to the ordered queue.
                lastModifiedFiles.add(file)
            }
        }

        // Add the most recent files to the cursor, not exceeding the max number of results.
        var includedCount = 0
        while (includedCount < MAX_LAST_MODIFIED + 1 && !lastModifiedFiles.isEmpty()) {
            val file = lastModifiedFiles.remove()
            includeFile(result, null, file)
            includedCount++
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun querySearchDocuments(
        rootId: String,
        query: String?,
        projection: Array<String>?
    ): Cursor {
        Log.v(TAG, "querySearchDocuments")

        // Create a cursor with the requested projection, or the default projection.
        val result = MatrixCursor(resolveDocumentProjection(projection))
        val parent = getFileForDocId(rootId)

        // Iterate through all files in the file structure under the root until we reach the
        // desired number of matches.
        val pending = LinkedList<File?>()

        // Start by adding the parent to the list of files to be processed
        pending.add(parent)

        // Do while we still have unexamined files, and fewer than the max search results
        while (!pending.isEmpty() && result.count < MAX_SEARCH_RESULTS) {
            // Take a file from the list of unprocessed files
            val file = pending.removeFirst()
            if (file!!.isDirectory) {
                // If it's a directory, add all its children to the unprocessed list
                Collections.addAll(pending, *Objects.requireNonNull(file.listFiles()))
            } else {
                // If it's a file and it matches, add it to the result cursor.
                if (file.name.lowercase().contains(query!!)) {
                    includeFile(result, null, file)
                }
            }
        }
        return result
    }

    @Throws(FileNotFoundException::class)
    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point?,
        signal: CancellationSignal?
    ): AssetFileDescriptor {
        Log.v(TAG, "openDocumentThumbnail")
        val file = getFileForDocId(documentId)
        val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        return AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH)
    }

    @Throws(FileNotFoundException::class)
    override fun queryDocument(documentId: String?, projection: Array<String>?): Cursor {
        Log.v(TAG, "queryDocument")

        // Create a cursor with the requested projection, or the default projection.
        return MatrixCursor(resolveDocumentProjection(projection)).apply {
            includeFile(this, documentId, null)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<String>?,
        sortOrder: String
    ): Cursor {
        Log.v(
            TAG,
            "queryChildDocuments, parentDocumentId: " +
                    parentDocumentId +
                    " sortOrder: " +
                    sortOrder
        )
        return MatrixCursor(resolveDocumentProjection(projection)).apply {
            val parent = getFileForDocId(parentDocumentId)
            (parent!!.listFiles())!!.forEach { file -> includeFile(this, null, file) }
        }
    }

    @Throws(FileNotFoundException::class)
    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor? {
        Log.v(TAG, "openDocument, mode: $mode")

        val file = getFileForDocId(documentId)
        val accessMode = ParcelFileDescriptor.parseMode(mode)
        val isWrite = mode.indexOf('w') != -1
        return if (isWrite) {
            // Attach a close listener if the document is opened in write mode.
            try {
                val handler = Handler(context!!.mainLooper)
                ParcelFileDescriptor.open(file, accessMode, handler) {
                    Log.i(
                        TAG,
                        ("A file with id " +
                                documentId +
                                " has been closed!  Time to " +
                                "update the server.")
                    )
                }
            } catch (e: IOException) {
                throw FileNotFoundException(
                    ("Failed to open document with id " + documentId + " and mode " + mode)
                )
            }
        } else {
            ParcelFileDescriptor.open(file, accessMode)
        }
    }

    @Throws(FileNotFoundException::class)
    override fun createDocument(
        documentId: String,
        mimeType: String?,
        displayName: String
    ): String {
        Log.v(TAG, "createDocument")
        val parent = getFileForDocId(documentId)
        val file = File(parent!!.path, displayName)
        try {
            file.createNewFile()
            file.setWritable(true)
            file.setReadable(true)
        } catch (e: IOException) {
            throw FileNotFoundException(
                "Failed to create document with name " +
                        displayName +
                        " and documentId " +
                        documentId
            )
        }
        return getDocIdForFile(file)
    }

    @Throws(FileNotFoundException::class)
    override fun deleteDocument(documentId: String) {
        Log.v(TAG, "deleteDocument")
        val file = getFileForDocId(documentId)
        if (file!!.delete()) {
            Log.i(TAG, "Deleted file with id $documentId")
        } else {
            throw FileNotFoundException("Failed to delete document with id $documentId")
        }
    }

    @Throws(FileNotFoundException::class)
    override fun getDocumentType(documentId: String): String {
        return getTypeForFile(getFileForDocId(documentId))
    }

    /**
     * @param projection the requested root column projection
     * @return either the requested root column projection, or the default projection if the
     * requested projection is null.
     */
    private fun resolveRootProjection(projection: Array<String>?): Array<String> {
        return projection ?: DEFAULT_ROOT_PROJECTION
    }

    private fun resolveDocumentProjection(projection: Array<String>?): Array<String> {
        return projection ?: DEFAULT_DOCUMENT_PROJECTION
    }

    /**
     * Get a file's MIME type
     *
     * @param file the File object whose type we want
     * @return the MIME type of the file
     */
    private fun getTypeForFile(file: File?): String {
        return if (file!!.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            getTypeForName(file.name)
        }
    }

    /**
     * Get the MIME data type of a document, given its filename.
     *
     * @param name the filename of the document
     * @return the MIME data type of a document
     */
    private fun getTypeForName(name: String): String {
        val lastDot = name.lastIndexOf('.')
        if (lastDot >= 0) {
            val extension = name.substring(lastDot + 1)
            val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            if (mime != null) {
                return mime
            }
        }
        return "application/octet-stream"
    }

    /**
     * Gets a string of unique MIME data types a directory supports, separated by newlines. This
     * should not change.
     *
     * @return a string of the unique MIME data types the parent directory supports
     */
    private fun getChildMimeTypes(): String {
        val mimeTypes: MutableSet<String> = HashSet()
        mimeTypes.add("image/*")
        mimeTypes.add("text/*")
        mimeTypes.add("*")

        // Flatten the list into a string and insert newlines between the MIME type strings.
        val mimeTypesString = StringBuilder()
        for (mimeType: String in mimeTypes) {
            mimeTypesString.append(mimeType).append("\n")
        }
        return mimeTypesString.toString()
    }

    /**
     * Get the document ID given a File. The document id must be consistent across time. Other
     * applications may save the ID and use it to reference documents later.
     *
     * @param file the File whose document ID you want
     * @return the corresponding document ID
     */
    private fun getDocIdForFile(file: File?): String {
        var path = file!!.absolutePath

        // Start at first char of path under root
        val rootPath = mBaseDir!!.path
        if (rootPath == path) {
            path = ""
        } else if (rootPath.endsWith("/")) {
            path = path.substring(rootPath.length)
        } else {
            path = path.substring(rootPath.length + 1)
        }
        return "root:$path"
    }

    /**
     * Add a representation of a file to a cursor.
     *
     * @param result the cursor to modify
     * @param docId the document ID representing the desired file (may be null if given file)
     * @param file the File object representing the desired file (may be null if given docID)
     */
    @Throws(FileNotFoundException::class)
    private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
        val mDocId: String
        var mFile = file
        if (docId == null) {
            mDocId = getDocIdForFile(mFile)
        } else {
            mDocId = docId
            mFile = getFileForDocId(mDocId)
        }
        var flags = 0
        if (mFile!!.isDirectory) {
            if (mFile.isDirectory && mFile.canWrite()) {
                flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            }
        } else if (mFile.canWrite()) {
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
        }
        val displayName = mFile.name
        val mimeType = getTypeForFile(mFile)
        if (mimeType.startsWith("image/")) {
            // Allow the image to be represented by a thumbnail rather than an icon
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
        }
        val row = result.newRow()
        row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, mDocId)
        row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
        row.add(DocumentsContract.Document.COLUMN_SIZE, mFile.length())
        row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
        row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, mFile.lastModified())
        row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)

        // Add a custom icon
        row.add(DocumentsContract.Document.COLUMN_ICON, R.drawable.ic_launcher_foreground)
    }

    /**
     * Translate your custom URI scheme into a File object.
     *
     * @param docId the document ID representing the desired file
     * @return a File represented by the given document ID
     */
    @Throws(FileNotFoundException::class)
    private fun getFileForDocId(docId: String): File? {
        var target = mBaseDir
        if ((docId == ROOT)) {
            return target
        }
        val splitIndex = docId.indexOf(':', 1)
        if (splitIndex < 0) {
            throw FileNotFoundException("Missing root for $docId")
        } else {
            val path = docId.substring(splitIndex + 1)
            target = File(target, path)
            if (!target.exists()) {
                throw FileNotFoundException("Missing file for $docId at $target")
            }
            return target
        }
    }
}
