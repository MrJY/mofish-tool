package online.mofish.tool.ui.web

import com.intellij.icons.AllIcons
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object MoFishWebFileType : FileType {
    override fun getName(): String = "MoFish Web"

    override fun getDescription(): String = "MoFish web editor"

    override fun getDefaultExtension(): String = "mofishweb"

    override fun getIcon(): Icon = AllIcons.Nodes.PpWeb

    override fun isBinary(): Boolean = true

    override fun isReadOnly(): Boolean = true

    override fun getCharset(file: VirtualFile, content: ByteArray): String = Charsets.UTF_8.name()
}
