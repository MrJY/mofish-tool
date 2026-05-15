package online.mofish.tool.ui.web

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.IOException

object MoFishWebVirtualFileSystem : VirtualFileSystem() {
    const val protocol: String = "mofish-web"

    override fun getProtocol(): String = protocol

    override fun findFileByPath(path: String): VirtualFile? = null

    override fun refresh(asynchronous: Boolean) = Unit

    override fun refreshAndFindFileByPath(path: String): VirtualFile? = null

    override fun addVirtualFileListener(listener: com.intellij.openapi.vfs.VirtualFileListener) = Unit

    override fun removeVirtualFileListener(listener: com.intellij.openapi.vfs.VirtualFileListener) = Unit

    @Throws(IOException::class)
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        (vFile as? MoFishWebVirtualFile)?.invalidate()
    }

    @Throws(IOException::class)
    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        throw IOException("MoFish web editor files cannot be moved.")
    }

    @Throws(IOException::class)
    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        throw IOException("MoFish web editor files cannot be renamed.")
    }

    @Throws(IOException::class)
    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        throw IOException("MoFish web editor files cannot create children.")
    }

    @Throws(IOException::class)
    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        throw IOException("MoFish web editor files cannot create children.")
    }

    @Throws(IOException::class)
    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String,
    ): VirtualFile {
        throw IOException("MoFish web editor files cannot be copied.")
    }

    override fun isReadOnly(): Boolean = true
}
