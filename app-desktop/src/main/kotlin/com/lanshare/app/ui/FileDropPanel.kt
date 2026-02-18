package com.lanshare.app.ui

import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetAdapter
import java.awt.dnd.DropTargetDropEvent
import java.nio.file.Path
import javax.swing.BorderFactory
import javax.swing.JLabel
import javax.swing.JPanel

class FileDropPanel(
    private val onFilesDropped: (List<Path>) -> Unit
) : JPanel(BorderLayout()) {
    init {
        border = BorderFactory.createDashedBorder(Color(0x1E, 0x88, 0xE5), 2f, 6f)
        background = Color(0xF1, 0xF7, 0xFF)

        val label = JLabel("Trascina file/cartelle/video qui").apply {
            horizontalAlignment = JLabel.CENTER
            foreground = Color(0x0D, 0x47, 0xA1)
            font = Font("SansSerif", Font.BOLD, 15)
        }
        add(label, BorderLayout.CENTER)

        DropTarget(this, DnDConstants.ACTION_COPY, object : DropTargetAdapter() {
            override fun drop(event: DropTargetDropEvent) {
                event.acceptDrop(DnDConstants.ACTION_COPY)
                val transfer = event.transferable
                val files = if (transfer.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                    @Suppress("UNCHECKED_CAST")
                    (transfer.getTransferData(DataFlavor.javaFileListFlavor) as List<java.io.File>)
                        .map { it.toPath() }
                } else {
                    emptyList()
                }

                if (files.isNotEmpty()) {
                    onFilesDropped(files)
                }
                event.dropComplete(true)
            }
        }, true)
    }
}
