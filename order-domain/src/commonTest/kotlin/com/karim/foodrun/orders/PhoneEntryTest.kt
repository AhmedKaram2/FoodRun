package com.karim.foodrun.orders

import kotlin.test.*

class PhoneEntryTest {
    @Test fun aaniAcceptsArabicPersianLocalAndInternationalMobileNumbers() {
        for (phone in listOf("050 123 4567", "50 123 4567", "+971501234567", "+971 (0)50 123 4567", "00971 50 123 4567", "٠٥٠ ١٢٣ ٤٥٦٧", "+٩٧١ ٥٠ ١٢٣ ٤٥٦٧", "۰۵۰۱۲۳۴۵۶۷"))
            assertEquals("+971501234567", UaePhone.normalize(phone, true))
        assertFailsWith<IllegalArgumentException> { UaePhone.normalize("+201012345678", true) }
        assertFailsWith<IllegalArgumentException> { UaePhone.normalize("+97141234567", true) }
    }
    @Test fun countrySelectionNormalizesNationalNumbersAndPreservesSignificantItalianZero() {
        assertEquals("+201012345678", CountryDialCodes.combine("EG", "٠١٠١٢٣٤٥٦٧٨"))
        assertEquals("+971501234567", CountryDialCodes.combine("AE", "0501234567"))
        assertEquals("+442079460000", CountryDialCodes.combine("GB", "02079460000"))
        assertEquals("+390212345678", CountryDialCodes.combine("IT", "0212345678"))
        assertEquals("EG" to "1012345678", CountryDialCodes.split("+201012345678"))
        assertEquals("CA" to "4161234567", CountryDialCodes.split("+14161234567", "CA"))
        assertEquals("", CountryDialCodes.combine("AE", ""))
    }
    @Test fun namedGoogleProfilesCanSaveWithoutAContactPhoneButInvalidSuppliedNumbersStillFail() {
        FoodProfile(name = "Google User").normalized().validate()
        FoodProfile(name = "Google User", payment = ReceivingAccount("aani", "Google User", "Aani", "٠٥٠١٢٣٤٥٦٧", method = PaymentMethod.AANI)).normalized().validate()
        assertFailsWith<IllegalArgumentException> { FoodProfile(name = "Google User", phone = "123").normalized().validate() }
        assertFailsWith<IllegalArgumentException> { FoodProfile().normalized().validate() }
    }
}
