package com.example.compilation.orders

import com.example.compilation.models.*

object OrderRepository {
    
    fun getOrderForRound(round: Int): Order? {
        return when (round) {
            0 -> null // Practice round - no order
            1 -> createRound1Order() // Basic order
            2 -> createRound2Order() // Order with promotions
            3 -> createRound3Order() // Order with customer
            4 -> createRound4Order() // Complex order
            5 -> createRound5Order() // Final challenge
            else -> null
        }
    }
    
    // Round 1: Basic coffee shop order
    private fun createRound1Order(): Order {
        return Order(
            orderId = "A-0042",
            storeNumber = "001",
            storeName = "BYTE BURGERS",
            timestamp = System.currentTimeMillis(),
            items = listOf(
                OrderItem(
                    name = "Cheeseburger",
                    quantity = 2,
                    unitPrice = 8.99,
                    totalPrice = 17.98,
                    sku = "BURG-001",
                    category = "BURGERS"
                ),
                OrderItem(
                    name = "French Fries",
                    quantity = 1,
                    unitPrice = 3.99,
                    totalPrice = 3.99,
                    sku = "SIDE-001",
                    category = "SIDES"
                ),
                OrderItem(
                    name = "Soft Drink",
                    quantity = 2,
                    unitPrice = 2.99,
                    totalPrice = 5.98,
                    sku = "DRINK-001",
                    category = "BEVERAGES"
                )
            ),
            subtotal = 27.95,
            taxRate = 0.08,
            taxAmount = 2.24,
            totalAmount = 30.19
        )
    }
    
    // Round 2: Order with promotions
    private fun createRound2Order(): Order {
        return Order(
            orderId = "B-1337",
            storeNumber = "002",
            storeName = "JAVA JUNCTION",
            timestamp = System.currentTimeMillis(),
            items = listOf(
                OrderItem(
                    name = "Large Latte",
                    quantity = 2,
                    unitPrice = 5.99,
                    totalPrice = 11.98,
                    sku = "COFF-002",
                    category = "HOT DRINKS"
                ),
                OrderItem(
                    name = "Chocolate Croissant",
                    quantity = 2,
                    unitPrice = 4.50,
                    totalPrice = 9.00,
                    sku = "BAKE-003",
                    category = "BAKERY"
                ),
                OrderItem(
                    name = "Breakfast Sandwich",
                    quantity = 1,
                    unitPrice = 7.99,
                    totalPrice = 7.99,
                    sku = "FOOD-001",
                    category = "FOOD"
                )
            ),
            subtotal = 28.97,
            taxRate = 0.0875,
            taxAmount = 2.53,
            totalAmount = 26.50, // After promotions
            itemPromotions = listOf(
                ItemPromotion(
                    itemSku = "COFF-002",
                    promotionName = "Buy One Get One 50% Off",
                    discountAmount = 3.00
                )
            ),
            orderPromotions = listOf(
                OrderPromotion(
                    promotionName = "Morning Rush Special",
                    discountAmount = 2.00,
                    promotionType = "FIXED"
                )
            )
        )
    }
    
    // Round 3: Order with customer information
    private fun createRound3Order(): Order {
        return Order(
            orderId = "C-2024",
            storeNumber = "003",
            storeName = "PIZZA PALACE",
            timestamp = System.currentTimeMillis(),
            items = listOf(
                OrderItem(
                    name = "Large Pepperoni Pizza",
                    quantity = 1,
                    unitPrice = 18.99,
                    totalPrice = 18.99,
                    sku = "PIZZ-001",
                    category = "PIZZA"
                ),
                OrderItem(
                    name = "Garlic Breadsticks",
                    quantity = 2,
                    unitPrice = 6.99,
                    totalPrice = 13.98,
                    sku = "SIDE-005",
                    category = "SIDES"
                ),
                OrderItem(
                    name = "2-Liter Soda",
                    quantity = 1,
                    unitPrice = 3.99,
                    totalPrice = 3.99,
                    sku = "DRINK-003",
                    category = "BEVERAGES"
                )
            ),
            subtotal = 36.96,
            taxRate = 0.08,
            taxAmount = 2.96,
            totalAmount = 39.92,
            customerInfo = CustomerInfo(
                customerId = "CUST-8826",
                name = "John Doe",
                memberStatus = "GOLD",
                loyaltyPoints = 1247,
                memberSince = "2019-03-15"
            ),
            paymentMethod = "VISA ****1234"
        )
    }
    
    // Round 4: Complex order with everything
    private fun createRound4Order(): Order {
        return Order(
            orderId = "D-9999",
            storeNumber = "004",
            storeName = "TECH TREATS",
            timestamp = System.currentTimeMillis(),
            items = listOf(
                OrderItem(
                    name = "Quantum Quiche",
                    quantity = 2,
                    unitPrice = 12.99,
                    totalPrice = 25.98,
                    sku = "TECH-001",
                    category = "MAINS"
                ),
                OrderItem(
                    name = "Binary Brownie",
                    quantity = 3,
                    unitPrice = 4.50,
                    totalPrice = 13.50,
                    sku = "DESS-001",
                    category = "DESSERTS"
                ),
                OrderItem(
                    name = "Cloud Coffee",
                    quantity = 2,
                    unitPrice = 5.99,
                    totalPrice = 11.98,
                    sku = "DRINK-007",
                    category = "BEVERAGES"
                ),
                OrderItem(
                    name = "RAM Ramen",
                    quantity = 1,
                    unitPrice = 14.99,
                    totalPrice = 14.99,
                    sku = "TECH-003",
                    category = "MAINS"
                )
            ),
            subtotal = 66.45,
            taxRate = 0.09,
            taxAmount = 4.55,
            totalAmount = 55.05, // After all discounts
            itemPromotions = listOf(
                ItemPromotion(
                    itemSku = "TECH-001",
                    promotionName = "Tech Tuesday Special",
                    discountAmount = 5.00
                ),
                ItemPromotion(
                    itemSku = "DESS-001",
                    promotionName = "Sweet Deal",
                    discountAmount = 2.00
                )
            ),
            orderPromotions = listOf(
                OrderPromotion(
                    promotionName = "Member Appreciation",
                    discountAmount = 10.0,
                    promotionType = "PERCENTAGE"
                ),
                OrderPromotion(
                    promotionName = "App Order Discount",
                    discountAmount = 3.00,
                    promotionType = "FIXED"
                )
            ),
            customerInfo = CustomerInfo(
                customerId = "CUST-1337",
                name = "Ada Lovelace",
                memberStatus = "PLATINUM",
                loyaltyPoints = 3847,
                memberSince = "2018-01-01"
            ),
            paymentMethod = "APPLE PAY"
        )
    }
    
    // Round 5: Final challenge - Split payment and complex layout
    private fun createRound5Order(): Order {
        return Order(
            orderId = "SPLIT-8847",
            storeNumber = "777",
            storeName = "THE FINAL FEAST",
            timestamp = System.currentTimeMillis(),
            items = listOf(
                OrderItem(
                    name = "Wagyu Steak",
                    quantity = 1,
                    unitPrice = 89.99,
                    totalPrice = 89.99,
                    sku = "LUX-001",
                    category = "ENTREES",
                    modifiers = listOf("Medium Rare", "Extra Butter", "Side: Mashed Potatoes")
                ),
                OrderItem(
                    name = "Lobster Risotto",
                    quantity = 1,
                    unitPrice = 45.99,
                    totalPrice = 45.99,
                    sku = "LUX-002",
                    category = "ENTREES",
                    modifiers = listOf("Extra Parmesan", "Side: Asparagus")
                ),
                OrderItem(
                    name = "Caesar Salad",
                    quantity = 2,
                    unitPrice = 12.99,
                    totalPrice = 25.98,
                    sku = "APP-001",
                    category = "APPETIZERS",
                    modifiers = listOf("No Anchovies", "Extra Croutons")
                ),
                OrderItem(
                    name = "Truffle Fries",
                    quantity = 1,
                    unitPrice = 18.99,
                    totalPrice = 18.99,
                    sku = "APP-002",
                    category = "APPETIZERS"
                ),
                OrderItem(
                    name = "Chocolate Soufflé",
                    quantity = 2,
                    unitPrice = 14.99,
                    totalPrice = 29.98,
                    sku = "DES-001",
                    category = "DESSERTS",
                    modifiers = listOf("Extra Vanilla Ice Cream")
                ),
                OrderItem(
                    name = "Vintage Wine",
                    quantity = 1,
                    unitPrice = 125.00,
                    totalPrice = 125.00,
                    sku = "WINE-001",
                    category = "BEVERAGES",
                    modifiers = listOf("2019 Cabernet Sauvignon")
                )
            ),
            subtotal = 335.93,
            taxRate = 0.095,
            taxAmount = 31.91,
            totalAmount = 362.84,
            itemPromotions = listOf(
                ItemPromotion(
                    itemSku = "DES-001",
                    promotionName = "Dessert Happy Hour",
                    discountAmount = 5.00
                )
            ),
            customerInfo = CustomerInfo(
                customerId = "GROUP-4452",
                name = "Table 12 - Chen Party",
                memberStatus = "VIP",
                loyaltyPoints = 15420,
                memberSince = "2020-03-15"
            ),
            paymentMethod = "SPLIT",
            splitPayments = listOf(
                SplitPayment(
                    payerName = "Alice Chen",
                    amount = 156.43,
                    method = "VISA ****7823",
                    tip = 25.00,
                    items = listOf("Wagyu Steak", "Truffle Fries")
                ),
                SplitPayment(
                    payerName = "Bob Martinez",
                    amount = 89.54,
                    method = "MASTERCARD ****9921",
                    tip = 15.00,
                    items = listOf("Lobster Risotto", "Caesar Salad (1)")
                ),
                SplitPayment(
                    payerName = "Carol Wu",
                    amount = 121.87,
                    method = "AMEX ****3345",
                    tip = 20.00,
                    items = listOf("Vintage Wine", "Caesar Salad (1)", "Chocolate Soufflé (2)")
                )
            ),
            tableInfo = TableInfo(
                tableNumber = "12",
                serverName = "Jennifer K.",
                guestCount = 3,
                serviceRating = 5
            )
        )
    }
}