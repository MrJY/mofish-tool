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

    /**
     * 返回组件、列或文件类型的展示名称。
     * @return 处理后的结果或当前状态。
     */
    override fun getName(): String = tabName

    /**
     * 获取文件System。
     * @return 处理后的结果或当前状态。
     */
    override fun getFileSystem(): VirtualFileSystem = MoFishWebVirtualFileSystem

    /**
     * 获取Path。
     * @return 处理后的结果或当前状态。
     */
    override fun getPath(): String = "${MoFishWebVirtualFileSystem.protocol}://$tabName"

    /**
     * 获取文件Type。
     * @return 处理后的结果或当前状态。
     */
    override fun getFileType(): FileType = MoFishWebFileType

    /**
     * 判断是否满足Writable条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isWritable(): Boolean = false

    /**
     * 判断是否满足Directory条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isDirectory(): Boolean = false

    /**
     * 判断是否满足Valid条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isValid(): Boolean = valid

    /**
     * 获取Parent。
     * @return 处理后的结果或当前状态。
     */
    override fun getParent(): VirtualFile? = null

    /**
     * 获取Children。
     * @return 处理后的结果或当前状态。
     */
    override fun getChildren(): Array<VirtualFile> = EMPTY_ARRAY

    /**
     * 获取OutputStream。
     * @param requestor 发起虚拟文件系统访问的调用方对象。
     * @param newModificationStamp newModificationStamp。
     * @param newTimeStamp new时间Stamp。
     * @return 处理后的结果或当前状态。
     */
    override fun getOutputStream(requestor: Any?, newModificationStamp: Long, newTimeStamp: Long): OutputStream {
        throw UnsupportedOperationException("MoFish web editor files are read-only.")
    }

    /**
     * 处理 contentsToByteArray 相关逻辑，并返回调用方需要的结果。
     * @return 处理后的结果或当前状态。
     */
    override fun contentsToByteArray(): ByteArray = ByteArray(0)

    /**
     * 获取时间Stamp。
     * @return 处理后的结果或当前状态。
     */
    override fun getTimeStamp(): Long = createdAt

    /**
     * 获取Length。
     * @return 处理后的结果或当前状态。
     */
    override fun getLength(): Long = 0

    /**
     * 处理 refresh 相关逻辑，并返回调用方需要的结果。
     * @param asynchronous asynchronous。
     * @param recursive 是否递归处理子节点。
     * @param postRunnable postRunnable。
     */
    override fun refresh(asynchronous: Boolean, recursive: Boolean, postRunnable: Runnable?) {
        postRunnable?.run()
    }

    /**
     * 获取InputStream。
     * @return 处理后的结果或当前状态。
     */
    override fun getInputStream(): InputStream = ByteArrayInputStream(ByteArray(0))

    /**
     * 处理 invalidate 相关逻辑，并返回调用方需要的结果。
     */
    fun invalidate() {
        valid = false
    }
}
