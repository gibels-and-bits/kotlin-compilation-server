package com.example.compilation.compiler

import com.example.compilation.models.InternalPrinterCommand

// Mirror of the EpsonPrinter interface from Android
interface EpsonPrinter {
    fun addText(text: String)
    fun addText(text: String, style: TextStyle?)
    fun addTextStyle(style: TextStyle)
    fun addTextAlign(alignment: Alignment)
    fun addBarcode(data: String, type: BarcodeType, options: BarcodeOptions? = null)
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

enum class BarcodeType {
    UPC_A, UPC_E, EAN13, EAN8,
    CODE39, ITF, CODABAR, CODE93, CODE128,
    GS1_128, GS1_DATABAR_OMNIDIRECTIONAL,
    GS1_DATABAR_TRUNCATED, GS1_DATABAR_LIMITED,
    GS1_DATABAR_EXPANDED
}

data class BarcodeOptions(
    val width: BarcodeWidth = BarcodeWidth.MEDIUM,
    val height: Int = 50,
    val hri: Boolean = true
)

enum class BarcodeWidth {
    THIN, MEDIUM, THICK
}

// Command capture implementation
class CommandCapturePrinter : EpsonPrinter {
    private val commands = mutableListOf<InternalPrinterCommand>()
    private var currentStyle: TextStyle = TextStyle()
    private var currentAlignment: Alignment = Alignment.LEFT
    
    override fun addText(text: String) {
        // Just add the text command - styles and alignment should be set separately
        commands.add(InternalPrinterCommand.AddText(text))
    }
    
    override fun addText(text: String, style: TextStyle?) {
        if (style != null) {
            // Add the style command first
            commands.add(InternalPrinterCommand.AddTextStyle(
                bold = style.bold,
                size = style.size.name,
                underline = style.underline
            ))
            currentStyle = style
        }
        // Then add the text
        commands.add(InternalPrinterCommand.AddText(text))
    }
    
    override fun addTextStyle(style: TextStyle) {
        currentStyle = style
        commands.add(InternalPrinterCommand.AddTextStyle(
            bold = style.bold,
            size = style.size.name,
            underline = style.underline
        ))
    }
    
    override fun addTextAlign(alignment: Alignment) {
        currentAlignment = alignment
        commands.add(InternalPrinterCommand.AddTextAlign(alignment.name))
    }
    
    override fun addBarcode(data: String, type: BarcodeType, options: BarcodeOptions?) {
        commands.add(InternalPrinterCommand.AddBarcode(
            data = data,
            type = type.name
        ))
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