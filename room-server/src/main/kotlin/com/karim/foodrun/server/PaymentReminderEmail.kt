package com.karim.foodrun.server

import com.karim.foodrun.orders.*

internal object PaymentReminderEmail {
    const val SUBJECT = "Intrvioo — Outstanding payment reminder | تذكير بالمبلغ المستحق"
    fun body(room: Room, receipt: Receipt): String {
        val payer = room.members.single { it.id == room.payerId }.name
        val amount = Money.format(receipt.balance, receipt.currency)
        return """
            Dear ${receipt.name},

            A friendly reminder: you still have $amount to pay to $payer for "${room.name}".

            Your case is currently with the Intrvioo Collections Committee. An amicable settlement is still on the table — and one transfer closes the case.

            Please open Intrvioo, pay using the collector's payment details, then mark your payment as sent. If you already paid, ask $payer to confirm it in the app.

            Thank you for settling your share.
            The Intrvioo Collection Team

            ────────────────────

            عزيزي ${receipt.name}،

            بنفكّرك إن المبلغ المتبقي عليك هو $amount، مستحق لـ $payer عن "${room.name}".

            ملفك حاليًا عند لجنة تحصيل Intrvioo، وباب التسوية الودية لسه مفتوح. تحويل واحد ونقفل القضية.

            افتح Intrvioo، حوّل المبلغ على بيانات المسؤول عن التحصيل، وبعدها علّم إنك دفعت. ولو دفعت خلاص، فكّر $payer يأكد الاستلام في التطبيق.

            شكرًا لتعاونك وسداد نصيبك.
            فريق تحصيل Intrvioo
        """.trimIndent()
    }
}
