package com.example.compilation.printer

import com.example.compilation.models.InternalPrinterCommand
import com.example.compilation.models.InternalPrinterCommand.*
import java.io.File

class ASCIIPrinter {
    private val commands = mutableListOf<InternalPrinterCommand>()
    private val receiptWidth = 40
    private val outputLines = mutableListOf<String>()
    
    private var currentAlignment = "LEFT"
    private var currentBold = false
    private var currentSize = "NORMAL"
    private var currentUnderline = false
    
    fun addText(text: String) {
        commands.add(AddText(text))
        processText(text)
    }
    
    fun addTextStyle(bold: Boolean, size: String, underline: Boolean) {
        commands.add(AddTextStyle(bold, size, underline))
        currentBold = bold
        currentSize = size
        currentUnderline = underline
    }
    
    fun addTextAlign(alignment: String) {
        commands.add(AddTextAlign(alignment))
        currentAlignment = alignment
    }
    
    fun addQRCode(data: String, size: Int) {
        commands.add(AddQRCode(data, size))
        renderQRCode(data)
    }
    
    fun addBarcode(data: String, type: String) {
        commands.add(AddBarcode(data, type))
        renderBarcode(data)
    }
    
    fun addFeedLine(lines: Int) {
        commands.add(AddFeedLine(lines))
        repeat(lines) { outputLines.add("") }
    }
    
    fun cutPaper() {
        commands.add(CutPaper)
        outputLines.add("")
        outputLines.add("═".repeat(receiptWidth))
        outputLines.add("         ✂ CUT HERE ✂")
        outputLines.add("═".repeat(receiptWidth))
    }
    
    private fun processText(text: String) {
        val lines = wrapText(text, receiptWidth)
        
        lines.forEach { line ->
            val formattedLine = when (currentAlignment) {
                "CENTER" -> centerText(line, receiptWidth)
                "RIGHT" -> rightAlignText(line, receiptWidth)
                else -> line
            }
            
            // Apply size effect (double height for LARGE)
            if (currentSize == "LARGE") {
                outputLines.add(formattedLine)
                outputLines.add(formattedLine) // Double the line for large text
            } else {
                outputLines.add(formattedLine)
            }
        }
    }
    
    private fun renderQRCode(data: String) {
        outputLines.add("┌────────────────────────┐")
        outputLines.add("│  [QR: ${data.take(18)}...]  │")
        outputLines.add("│  ████ █████ ██▄ ████  │")
        outputLines.add("│  ████ █   █ ███ ████  │")
        outputLines.add("│  ████ █▄▄▄█ ▄▄█ ████  │")
        outputLines.add("└────────────────────────┘")
    }
    
    private fun renderBarcode(data: String) {
        outputLines.add("║║║║║║║║║║║║║║║║║║║║║║║║")
        outputLines.add(centerText(data, receiptWidth))
    }
    
    private fun wrapText(text: String, width: Int): List<String> {
        if (text.length <= width) return listOf(text)
        
        val lines = mutableListOf<String>()
        var currentLine = ""
        
        text.split(" ").forEach { word ->
            if (currentLine.isEmpty()) {
                currentLine = word
            } else if ((currentLine.length + 1 + word.length) <= width) {
                currentLine += " $word"
            } else {
                lines.add(currentLine)
                currentLine = word
            }
        }
        
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine)
        }
        
        return lines
    }
    
    private fun centerText(text: String, width: Int): String {
        val padding = (width - text.length) / 2
        return if (padding > 0) {
            " ".repeat(padding) + text
        } else {
            text
        }
    }
    
    private fun rightAlignText(text: String, width: Int): String {
        return if (text.length < width) {
            text.padStart(width)
        } else {
            text
        }
    }
    
    fun getCommands(): List<InternalPrinterCommand> = commands.toList()
    
    fun renderToFile(filepath: String) {
        File(filepath).writeText(outputLines.joinToString("\n"))
    }
    
    fun renderToString(): String {
        return outputLines.joinToString("\n")
    }
}