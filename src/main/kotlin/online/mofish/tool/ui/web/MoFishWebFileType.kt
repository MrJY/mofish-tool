package online.mofish.tool.ui.web

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object MoFishWebFileType : FileType {
    /**
     * 返回组件、列或文件类型的展示名称。
     * @return 处理后的结果或当前状态。
     */
    override fun getName(): String = "MoFish Web"

    /**
     * 返回组件、动作或文件类型的说明文本。
     * @return 处理后的结果或当前状态。
     */
    override fun getDescription(): String = "MoFish web editor"

    /**
     * 获取默认Extension。
     * @return 处理后的结果或当前状态。
     */
    override fun getDefaultExtension(): String = "mofishweb"

    /**
     * 返回当前类型或组件使用的图标。
     * @return 处理后的结果或当前状态。
     */
    override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

    /**
     * 判断是否满足Binary条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isBinary(): Boolean = true

    /**
     * 判断是否满足ReadOnly条件。
     * @return 处理后的结果或当前状态。
     */
    override fun isReadOnly(): Boolean = true

    /**
     * 获取Charset。
     * @param file 文件。
     * @param content 需要渲染或包装的内容。
     * @return 处理后的结果或当前状态。
     */
    override fun getCharset(file: VirtualFile, content: ByteArray): String = Charsets.UTF_8.name()
}
