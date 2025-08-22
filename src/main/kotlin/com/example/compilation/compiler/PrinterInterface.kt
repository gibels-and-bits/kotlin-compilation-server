package com.example.compilation.compiler

import com.example.compilation.models.InternalPrinterCommand

// Mirror of the EpsonPrinter interface from Android
interface EpsonPrinter {
    fun addText(text: String)
    fun addTextStyle(style: TextStyle)
    fun addTextAlign(alignment: Alignment)
    fun addQRCode(data: String, options: QRCodeOptions? = null)
    fun addFeedLine(lines: Int)
    fun cutPaper()
}

// Supporting classes
data class TextStyle(
    val bold: Boolean = false,
    val underline: Boolean = false,
    val size: TextSize = TextSize.NORMAL
)

enum class TextSize {
    SMALL, NORMAL, LARGE, XLARGE
}

enum class Alignment {
    LEFT, CENTER, RIGHT
}

data class QRCodeOptions(
    val size: Int = 3,
    val errorCorrection: QRErrorCorrection = QRErrorCorrection.M
)

enum class QRErrorCorrection {
    L, M, Q, H
}

// Command capture implementation
class CommandCapturePrinter : EpsonPrinter {
    private val commands = mutableListOf<InternalPrinterCommand>()
    
    override fun addText(text: String) {
        commands.add(InternalPrinterCommand.AddText(text))
    }
    
    override fun addTextStyle(style: TextStyle) {
        commands.add(InternalPrinterCommand.AddTextStyle(
            bold = style.bold,
            size = style.size.name,
            underline = style.underline
        ))
    }
    
    override fun addTextAlign(alignment: Alignment) {
        commands.add(InternalPrinterCommand.AddTextAlign(alignment.name))
    }
    
    override fun addQRCode(data: String, options: QRCodeOptions?) {
        commands.add(InternalPrinterCommand.AddQRCode(
            data = data,
            size = options?.size ?: 3
        ))
    }
    
    override fun addFeedLine(lines: Int) {
        commands.add(InternalPrinterCommand.AddFeedLine(lines))
    }
    
    override fun cutPaper() {
        commands.add(InternalPrinterCommand.CutPaper)
    }
    
    fun getCommands(): List<InternalPrinterCommand> = commands.toList()
    fun clearCommands() = commands.clear()
}