package com.example.compilation.models

import kotlinx.serialization.Serializable

@Serializable
data class CompileRequest(
    val teamId: String,
    val code: String
)

@Serializable
data class CompileResponse(
    val success: Boolean,
    val message: String? = null,
    val error: String? = null,
    val lineNumber: Int? = null
)

@Serializable
data class ExecuteRequest(
    val teamId: String,
    val jsonData: String
)

@Serializable
data class ExecuteResponse(
    val success: Boolean,
    val commands: List<PrinterCommand>? = null,
    val error: String? = null
)

@Serializable
data class PrinterCommand(
    val type: String,
    val params: Map<String, String> = emptyMap()
)

// Internal models for printer commands
sealed class InternalPrinterCommand {
    data class AddText(val text: String) : InternalPrinterCommand()
    data class AddTextStyle(val bold: Boolean, val size: String, val underline: Boolean) : InternalPrinterCommand()
    data class AddTextAlign(val alignment: String) : InternalPrinterCommand()
    data class AddQRCode(val data: String, val size: Int) : InternalPrinterCommand()
    data class AddFeedLine(val lines: Int) : InternalPrinterCommand()
    object CutPaper : InternalPrinterCommand()
    
    fun toSerializable(): PrinterCommand {
        return when (this) {
            is AddText -> PrinterCommand("ADD_TEXT", mapOf("text" to text))
            is AddTextStyle -> PrinterCommand("ADD_TEXT_STYLE", mapOf(
                "bold" to bold.toString(),
                "size" to size,
                "underline" to underline.toString()
            ))
            is AddTextAlign -> PrinterCommand("ADD_TEXT_ALIGN", mapOf("alignment" to alignment))
            is AddQRCode -> PrinterCommand("ADD_QR_CODE", mapOf(
                "data" to data,
                "size" to size.toString()
            ))
            is AddFeedLine -> PrinterCommand("ADD_FEED_LINE", mapOf("lines" to lines.toString()))
            is CutPaper -> PrinterCommand("CUT_PAPER")
        }
    }
}