package com.onerouteandroid.oneroute.data.model

enum class PaymentKind { MASTERCARD, VISA, GOOGLE_PAY }

data class PaymentMethod(
    val id: String,
    val name: String,
    val detail: String,
    val kind: PaymentKind
)

data class WalletTransaction(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: Double,
    val statusLabel: String
)

data class FaqTopic(
    val title: String,
    val subtitle: String,
    val articleCount: Int
)

data class FaqArticle(
    val id: String,
    val category: String,
    val question: String,
    val popular: Boolean = false
)
