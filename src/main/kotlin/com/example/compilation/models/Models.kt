package com.example.compilation.models

import kotlinx.serialization.Serializable

@Serializable
data class CompileRequest(
    val teamId: String,
    val teamName: String? = null,
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
    val jsonData: String,
    val round: Int = 0
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
    val text: String? = null,
    val alignment: String? = null,
    val bold: Boolean? = null,
    val size: String? = null,
    val underline: Boolean? = null,
    val data: String? = null,
    val qrSize: Int? = null,
    val lines: Int? = null
)

@Serializable
data class CacheStatusResponse(
    val cache_size: Int,
    val teams: List<TeamCacheInfo>
)

@Serializable
data class TeamCacheInfo(
    val teamId: String,
    val compiledAt: String
)

// Order data models
@Serializable
data class Order(
    val orderId: String,
    val storeNumber: String,
    val storeName: String,
    val timestamp: Long,
    val items: List<OrderItem>,
    val subtotal: Double,
    val taxRate: Double,
    val taxAmount: Double,
    val totalAmount: Double,
    val itemPromotions: List<ItemPromotion> = emptyList(),
    val orderPromotions: List<OrderPromotion> = emptyList(),
    val customerInfo: CustomerInfo? = null,
    val paymentMethod: String? = null
)

@Serializable
data class OrderItem(
    val name: String,
    val quantity: Int,
    val unitPrice: Double,
    val totalPrice: Double,
    val sku: String? = null,
    val category: String? = null
)

@Serializable
data class ItemPromotion(
    val itemSku: String,
    val promotionName: String,
    val discountAmount: Double
)

@Serializable
data class OrderPromotion(
    val promotionName: String,
    val discountAmount: Double,
    val promotionType: String // "PERCENTAGE" or "FIXED"
)

@Serializable
data class CustomerInfo(
    val customerId: String,
    val name: String,
    val memberStatus: String? = null,
    val loyaltyPoints: Int = 0,
    val memberSince: String? = null
)

// Internal models for printer commands
sealed class InternalPrinterCommand {
    data class AddText(val text: String) : InternalPrinterCommand()
    data class AddTextStyle(val bold: Boolean, val size: String, val underline: Boolean) : InternalPrinterCommand()
    data class AddTextAlign(val alignment: String) : InternalPrinterCommand()
    data class AddQRCode(val data: String, val size: Int) : InternalPrinterCommand()
    data class AddBarcode(val data: String, val type: String) : InternalPrinterCommand()
    data class AddFeedLine(val lines: Int) : InternalPrinterCommand()
    object CutPaper : InternalPrinterCommand()
    
    fun toSerializable(): PrinterCommand {
        return when (this) {
            is AddText -> PrinterCommand(
                type = "ADD_TEXT",
                text = text
            )
            is AddTextStyle -> PrinterCommand(
                type = "ADD_TEXT_STYLE",
                bold = bold,
                size = size,
                underline = underline
            )
            is AddTextAlign -> PrinterCommand(
                type = "ADD_TEXT_ALIGN",
                alignment = alignment
            )
            is AddQRCode -> PrinterCommand(
                type = "ADD_QR_CODE",
                data = data,
                qrSize = size
            )
            is AddBarcode -> PrinterCommand(
                type = "ADD_BARCODE",
                data = data,
                text = type // Using text field for barcode type
            )
            is AddFeedLine -> PrinterCommand(
                type = "ADD_FEED_LINE",
                lines = lines
            )
            is CutPaper -> PrinterCommand(
                type = "CUT_PAPER"
            )
        }
    }
}