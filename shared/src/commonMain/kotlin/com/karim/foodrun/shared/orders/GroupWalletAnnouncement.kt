package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.orderJson

internal class GroupWalletAnnouncement(private val c: GroupController) {
    private val storageName = "wallet-announcement-v1"
    private val seen = runCatching {
        c.platform.read(storageName).takeIf { it.isNotBlank() }?.let { orderJson.decodeFromString<Set<String>>(it) } ?: emptySet()
    }.getOrDefault(emptySet()).toMutableSet()
    private fun tr(en: String, ar: String) = if (c.library.language == "ar") ar else en
    fun dismiss() {
        val userId = c.library.home?.profile?.userId.orEmpty()
        if (userId.isBlank()) return
        check(c.platform.write(storageName, orderJson.encodeToString(seen + userId))) { "Could not save your preference. Check device storage and retry." }
        seen += userId
    }
    fun card(): GroupCard? {
        val userId = c.library.home?.profile?.userId.orEmpty()
        if (userId.isBlank() || userId in seen) return null
        return GroupCard("wallet-announcement", tr("Your wallet is here!", "محفظتك وصلت!"),
            tr("Keep money with a trusted wallet holder and use your balance to pay for order orders.\n1. Choose a wallet holder and request a top-up.\n2. Transfer the money outside Intrvioo. Your balance updates after the holder confirms receipt.\n3. Choose Pay with wallet when paying your share.",
                "خلّي رصيدك عند شخص تثق فيه، واستخدمه لدفع حصتك في طلبات الطلبات.\n١. اختار صاحب المحفظة واطلب شحن الرصيد.\n٢. حوّل الفلوس خارج إنترفيوو. الرصيد بيتضاف بعد ما صاحب المحفظة يؤكد الاستلام.\n٣. اختار الدفع بالمحفظة وقت دفع حصتك."),
            buttons = listOf(GroupButton(tr("Open wallet", "افتح المحفظة"), GroupAction.OPEN_WALLET_ANNOUNCEMENT, primary = true),
                GroupButton(tr("Got it", "تمام، فهمت"), GroupAction.DISMISS_WALLET_ANNOUNCEMENT)))
    }
}
