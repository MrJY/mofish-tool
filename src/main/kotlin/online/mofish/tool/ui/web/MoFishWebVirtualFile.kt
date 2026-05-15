package online.mofish.tool.ui.web

import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream

class MoFishWebVirtualFile(
    private val tabName: String,
    val request: MoFishWebRequest,
) : VirtualFile() {
    private val createdAt = System.currentTimeMillis()
    private var valid = true

    override fun getName(): String = tabName

    override fun getFileSystem(): VirtualFileSystem = MoFishWebVirtualFileSystem

    override fun getPath(): String = "${MoFishWebVirtualFileSystem.protocol}://$tabName"

    override fun getFileType(): FileType = MoFishWebFileType

    override fun isWritable(): Boolean = false

    override fun isDirectory(): Boolean = false

    override fun isValid(): Boolean = valid

    override fun getParent(): VirtualFile? = null

    override fun getChildren(): Array<VirtualFile> = EMPTY_ARRAY

    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw UnsupportedOperationException("MoFish web editor files are read-only.")
    }

    override fun contentsToByteArray(): ByteArray = ByteArray(0)

    override fun getTimeStamp(): Long = createdAt

    override fun getLength(): Long = 0

    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        postRunnable?.run()
    }

    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    fun invalidate() {
        valid = false
    }
}
