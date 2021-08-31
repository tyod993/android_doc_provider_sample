package com.enfold.android_file_open_sandbox

import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.lang.IllegalStateException
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap


/**
 * This class is a Kotlin revised version of an example DocumentsProvider provided by google here
 * [https://android.googlesource.com/platform/frameworks/base/+/4ec9739/packages/ExternalStorageProvider/src/com/android/externalstorage/ExternalStorageProvider.java]
 *
 * TODO Write out more documentation and do the logging for this class.
 */

class DocProvider : DocumentsProvider() {

        private class RootInfo {
            var rootId: String? = null
            var flags = 0
            var icon = 0
            var title: String? = null
            var docId: String? = null
        }

        private var mRoots: ArrayList<RootInfo>? = null
        private var mIdToRoot: HashMap<String, RootInfo>? = null
        private var mIdToPath: HashMap<String, File>? = null

        override fun onCreate(): Boolean {
            mRoots = arrayListOf()
            mIdToRoot = hashMapOf()
            mIdToPath = hashMapOf()
            try {
                val rootId = "primary" //Make sure this is moved to strings
                //TODO Keep this in mind as it needs to be matched up with the Dart directory
                val path: File = requireContext().filesDir
                mIdToPath!![rootId] = path
                val root = RootInfo()
                mRoots!!.add(root)
                mIdToRoot!![rootId] = root
            } catch (e: FileNotFoundException) {
                throw IllegalStateException(e)
            }
            return true
        }


        @Throws(FileNotFoundException::class)
        private fun getDocIdForFile(file: File?): String {
            var path: String? = file?.absolutePath
            // Find the most-specific root path
            var mostSpecific: Map.Entry<String, File>? = null
            for (root in mIdToPath!!.iterator()) {
                val rootPath: String = root.value.path
                if (path != null) {
                    if (path.startsWith(rootPath) && (mostSpecific == null
                                || rootPath.length > mostSpecific.value.path.length)
                    ) {
                        mostSpecific = root
                    }
                }
            }
            if (mostSpecific == null) {
                throw FileNotFoundException("Failed to find root that contains $path")
            }
            // Start at first char of path under root
            val rootPath: String = mostSpecific.value.path
            path = if (rootPath == path) {
                ""
            } else if (rootPath.endsWith("/")) {
                path!!.substring(rootPath.length) //TODO This null assertion is a bad idea
            } else {
                path!!.substring(rootPath.length + 1)
            }
            return mostSpecific.key + ':' + path
        }

        @Throws(FileNotFoundException::class)
        private fun getFileForDocId(docId: String): File {
            val splitIndex = docId.indexOf(':', 1)
            val tag = docId.substring(0, splitIndex)
            val path = docId.substring(splitIndex + 1)
            var target: File? = mIdToPath!![tag] ?: throw FileNotFoundException("No root for $tag")
            target = File(target, path)
            if (!target.exists()) {
                throw FileNotFoundException("Missing file for $docId at $target")
            }
            return target
        }

        @Throws(FileNotFoundException::class)
        private fun includeFile(result: MatrixCursor, docId: String?, file: File?) {
            var mDocId = docId
            var mFile: File? = file
            if (mDocId == null) {
                mDocId = getDocIdForFile(mFile)
            } else {
                mFile = getFileForDocId(mDocId)
            }
            //TODO Im not sure this is the way flags in intended to be used
            var flags = 0
            //At this point an exception would have been thrown if file was null
            flags = flags or DocumentsContract.Root.FLAG_SUPPORTS_SEARCH or DocumentsContract.Root.FLAG_SUPPORTS_RECENTS
            flags = flags or DocumentsContract.Document.FLAG_DIR_SUPPORTS_CREATE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_WRITE
            flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_DELETE
            val displayName: String = mFile!!.name
            val mimeType = getTypeForFile(mFile)
            if (mimeType.startsWith("image/")) {
                flags = flags or DocumentsContract.Document.FLAG_SUPPORTS_THUMBNAIL
            }
            val row = result.newRow()
            row.add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, mDocId)
            row.add(DocumentsContract.Document.COLUMN_DISPLAY_NAME, displayName)
            row.add(DocumentsContract.Document.COLUMN_SIZE, mFile.length())
            row.add(DocumentsContract.Document.COLUMN_MIME_TYPE, mimeType)
            row.add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, mFile.lastModified())
            row.add(DocumentsContract.Document.COLUMN_FLAGS, flags)
        }

        @Throws(FileNotFoundException::class)
        override fun queryRoots(projection: Array<String>): Cursor {
            val result = MatrixCursor(resolveRootProjection(projection))
            for (rootId in mIdToPath!!.iterator()) {
                val root : RootInfo? = mIdToRoot!![rootId.key]
                val path: File = rootId.value
                val row = result.newRow()
                row.add(DocumentsContract.Root.COLUMN_ROOT_ID, root!!.rootId)
                row.add(DocumentsContract.Root.COLUMN_FLAGS, root.flags)
                row.add(DocumentsContract.Root.COLUMN_ICON, root.icon)
                row.add(DocumentsContract.Root.COLUMN_TITLE, root.title)
                row.add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, root.docId)
                row.add(DocumentsContract.Root.COLUMN_AVAILABLE_BYTES, path.freeSpace)
            }
            return result
        }

        @Throws(FileNotFoundException::class)
        override fun createDocument(docId: String, mimeType: String, displayName: String): String {
            val parent: File = getFileForDocId(docId)
            val name: String = validateDisplayName(mimeType, displayName)
            val file = File(parent, name)
            if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                check(file.mkdir()) { "Failed to mkdir $file" }
            } else {
                try {
                    check(file.createNewFile()) { "Failed to touch $file" }
                } catch (e: IOException) {
                    throw IllegalStateException("Failed to touch $file: $e")
                }
            }
            return getDocIdForFile(file)
        }

        @Throws(FileNotFoundException::class)
        override fun deleteDocument(docId: String) {
            val file: File = getFileForDocId(docId)
            check(file.delete()) { "Failed to delete $file" }
        }

        @Throws(FileNotFoundException::class)
        override fun queryDocument(documentId: String, projection: Array<String>): Cursor {
            val result = MatrixCursor(resolveDocumentProjection(projection))
            includeFile(result, documentId, null)
            return result
        }

        @Throws(FileNotFoundException::class)
        override fun queryChildDocuments(
            parentDocumentId: String, projection: Array<String>, sortOrder: String
        ): Cursor {
            val result = MatrixCursor(resolveDocumentProjection(projection))
            val parent: File = getFileForDocId(parentDocumentId)
            //TODO this should be null checked instead of asserted.
            for (file : File in (parent.listFiles() as? Array<File>)!!) {
                includeFile(result, null, file)
            }
            return result
        }

        @Throws(FileNotFoundException::class)
        override fun querySearchDocuments(
            parentDocumentId: String,
            query: String,
            projection: Array<String>
        ): Cursor {
            val result = MatrixCursor(resolveDocumentProjection(projection))
            val parent: File = getFileForDocId(parentDocumentId)
            val pending: LinkedList<File> = LinkedList<File>()
            pending.add(parent)
            while (!pending.isEmpty() && result.count < 24) {
                val file: File = pending.removeFirst()
                if (file.isDirectory) {
                    //TODO this should be null checked instead of asserted.
                    for (child in (file.listFiles())!!) {
                        pending.add(child)
                    }
                }
                if (file.name.lowercase(Locale.getDefault()).contains(query)) {
                    includeFile(result, null, file)
                }
            }
            return result
        }

        @Throws(FileNotFoundException::class)
        override fun getDocumentType(documentId: String): String {
            val file: File = getFileForDocId(documentId)
            return getTypeForFile(file)
        }

        @Throws(FileNotFoundException::class)
        override fun openDocument(
            documentId: String, mode: String, signal: CancellationSignal?
        ): ParcelFileDescriptor {
            val file: File = getFileForDocId(documentId)
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_WRITE)
        }

        companion object {
            //TODO I dont think these are needed according to the docs here https://developer.android.com/guide/topics/providers/create-document-provider#kotlin

            // docId format: root:path/to/file
            private val DEFAULT_ROOT_PROJECTION = arrayOf(
                DocumentsContract.Root.COLUMN_ROOT_ID,
                DocumentsContract.Root.COLUMN_FLAGS,
                DocumentsContract.Root.COLUMN_ICON,
                DocumentsContract.Root.COLUMN_TITLE,
                DocumentsContract.Root.COLUMN_SUMMARY,
                DocumentsContract.Root.COLUMN_DOCUMENT_ID,
                DocumentsContract.Root.COLUMN_AVAILABLE_BYTES
            )
            private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_FLAGS,
                DocumentsContract.Document.COLUMN_SIZE
            )

            private fun resolveRootProjection(projection: Array<String>?): Array<String> {
                return projection ?: DEFAULT_ROOT_PROJECTION
            }

            private fun resolveDocumentProjection(projection: Array<String>?): Array<String> {
                return projection ?: DEFAULT_DOCUMENT_PROJECTION
            }

            private fun getTypeForFile(file: File?): String {
                return if(file == null) {
                    ""
                } else if (file.isDirectory) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    getTypeForName(file.name)
                }
            }

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

            private fun validateDisplayName(mimeType: String, displayName: String): String {
                var mDisplayName = displayName
                return if (DocumentsContract.Document.MIME_TYPE_DIR == mimeType) {
                    mDisplayName
                } else {
                    // Try appending meaningful extension if needed
                    if (mimeType != getTypeForName(mDisplayName)) {
                        val extension = MimeTypeMap.getSingleton()
                            .getExtensionFromMimeType(mimeType)
                        if (extension != null) {
                            mDisplayName += ".$extension"
                        }
                    }
                    mDisplayName
                }
            }
        }
    }