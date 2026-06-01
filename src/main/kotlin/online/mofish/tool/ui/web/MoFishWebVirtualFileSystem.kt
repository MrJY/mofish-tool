package online.mofish.tool.ui.web

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileSystem
import java.io.IOException

object MoFishWebVirtualFileSystem : VirtualFileSystem() {
    const val protocol: String = "mofish-web"

    /**
     * 获取Protocol。
     * @return 处理后的结果或当前状态。
     */
    override fun getProtocol(): String = protocol

    /**
     * 处理 findFileByPath 相关逻辑，并返回调用方需要的结果。
     * @param path path。
     * @return 处理后的结果或当前状态。
     */
    override fun findFileByPath(path: String): VirtualFile? = null

    /**
     * 处理 refresh 相关逻辑，并返回调用方需要的结果。
     * @param asynchronous asynchronous。
     * @return 处理后的结果或当前状态。
     */
    override fun refresh(asynchronous: Boolean) = Unit

    /**
     * 处理 refreshAndFindFileByPath 相关逻辑，并返回调用方需要的结果。
     * @param path path。
     * @return 处理后的结果或当前状态。
     */
    override fun refreshAndFindFileByPath(path: String): VirtualFile? = null

    /**
     * 添加虚拟文件Listener。
     * @param listener listener。
     * @return 处理后的结果或当前状态。
     */
    override fun addVirtualFileListener(listener: com.intellij.openapi.vfs.VirtualFileListener) = Unit

    /**
     * 删除虚拟文件Listener。
     * @param listener listener。
     * @return 处理后的结果或当前状态。
     */
    override fun removeVirtualFileListener(listener: com.intellij.openapi.vfs.VirtualFileListener) = Unit

    /**
     * 处理 deleteFile 相关逻辑，并返回调用方需要的结果。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param vFile v文件。
     */
    @Throws(IOException::class)
    override fun deleteFile(requestor: Any?, vFile: VirtualFile) {
        (vFile as? MoFishWebVirtualFile)?.invalidate()
    }

    /**
     * 处理 moveFile 相关逻辑，并返回调用方需要的结果。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param vFile v文件。
     * @param newParent newParent。
     */
    @Throws(IOException::class)
    override fun moveFile(requestor: Any?, vFile: VirtualFile, newParent: VirtualFile) {
        throw IOException("MoFish web editor files cannot be moved.")
    }

    /**
     * 处理 renameFile 相关逻辑，并返回调用方需要的结果。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param vFile v文件。
     * @param newName new名称。
     */
    @Throws(IOException::class)
    override fun renameFile(requestor: Any?, vFile: VirtualFile, newName: String) {
        throw IOException("MoFish web editor files cannot be renamed.")
    }

    /**
     * 创建Child文件实例或展示内容。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param vDir vDir。
     * @param fileName 文件名称。
     * @return 处理后的结果或当前状态。
     */
    @Throws(IOException::class)
    override fun createChildFile(requestor: Any?, vDir: VirtualFile, fileName: String): VirtualFile {
        throw IOException("MoFish web editor files cannot create children.")
    }

    /**
     * 创建ChildDirectory实例或展示内容。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param vDir vDir。
     * @param dirName dir名称。
     * @return 处理后的结果或当前状态。
     */
    @Throws(IOException::class)
    override fun createChildDirectory(requestor: Any?, vDir: VirtualFile, dirName: String): VirtualFile {
        throw IOException("MoFish web editor files cannot create children.")
    }

    /**
     * 处理 copyFile 相关逻辑，并返回调用方需要的结果。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param virtualFile 虚拟文件。
     * @param newParent newParent。
     * @param copyName copy名称。
     * @return 处理后的结果或当前状态。
     */
    @Throws(IOException::class)
    override fun copyFile(
        requestor: Any?,
        virtualFile: VirtualFile,
        newParent: VirtualFile,
        copyName: String,
    ): VirtualFile {
        throw IOException("MoFish web editor files cannot be copied.")
    }

    /**
     * 判断是否满足ReadOnly条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isReadOnly(): Boolean = true
}
