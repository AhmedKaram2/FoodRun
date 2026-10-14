package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class ArabicVariantsTest {
    @Test fun emiratiActionsUseLocalWordsWithoutChangingOtherArabicVariants() {
        for((key,word) in listOf("What would you like?" to "شو تبا تطلب؟","Mark paid" to "طرّشت المبلغ","Half" to "نص","Friends" to "الربع","Confirm received" to "هيه، استلمت")) {
            assertEquals(word,GroupUiText.translate(key,"ar-AE"))
            assertNotEquals(word,GroupUiText.translate(key,"ar-001"))
        }
    }
    @Test fun allArabicStylesUseRtlAndKeepRestaurantNamesArabic() {
        for((locale,word) in listOf("ar-EG" to "مبلغ تاني","ar-AE" to "مبلغ ثاني","ar-SD" to "مبلغ تاني","ar-001" to "مبلغ آخر")) {
            assertEquals(word,GroupUiText.translate("Another amount",locale))
            val restaurant=Restaurant("r","Kitchen",nameAr="المطبخ")
            assertEquals("المطبخ",restaurant.localizedName(locale))
            val c=GroupController(GroupSettlementPresentationTestDevice())
            c.library=c.library.copy(language=locale)
            assertTrue(c.state.rtl); assertEquals(locale,c.state.language)
        }
    }
    @Test fun sudaneseHomeActionsAndSavedPreferenceUseTheSameLocaleAfterRestart() {
        val device=GroupSettlementPresentationTestDevice()
        val c=GroupController(device)
        c.dispatch(GroupAction.SET_LANGUAGE,"ar-SD")
        assertEquals("ar-SD",c.library.languagePreference)
        val restored=GroupController(device)
        assertEquals("ar-SD",restored.state.language)
        assertTrue(restored.state.rtl)
        for((key,word) in listOf("Create a room" to "افتح غرفة","Join a room" to "ادخل غرفة",
            "What would you like?" to "داير تطلب شنو؟","Mark paid" to "حوّلت القروش",
            "Confirm received" to "استلمت القروش")) assertEquals(word,GroupUiText.translate(key,restored.state.language))
        FoodProfile(name="أمل",language="ar-SD").validate()
        assertFailsWith<IllegalArgumentException> { FoodProfile(name="أمل",language="unsupported").validate() }
    }
    @Test fun aSavedLocalLanguageChoiceSurvivesAccountRefresh() {
        val device=GroupSettlementPresentationTestDevice()
        val c=GroupController(device)
        c.dispatch(GroupAction.SET_LANGUAGE,"ar-AE")
        assertEquals("ar-AE",c.library.languagePreference)
        val restored=GroupController(device)
        assertEquals("ar-AE",restored.state.language)
        assertEquals("ar-AE",restored.library.languagePreference)
    }
    private class GroupSettlementPresentationTestDevice:GroupPlatform {
        val storage=mutableMapOf<String,String>()
        override fun read(key:String)=storage[key].orEmpty()
        override fun write(key:String,value:String):Boolean { storage[key]=value;return true }
        override fun now()=0L
        override fun uuid()="locale-test-command"
        override fun request(hub:HubPairing,body:String,callback:GroupReplyCallback)=Unit
        override fun watch(hub:HubPairing,body:String,callback:GroupReplyCallback)=object:GroupSubscription { override fun cancel()=Unit }
        override fun share(text:String,fileName:String)=Unit
        override fun openLink(url:String)=Unit
        override fun importMenu(callback:GroupReplyCallback)=Unit
        override fun scanPairing(callback:GroupReplyCallback)=Unit
        override fun discover(callback:GroupReplyCallback)=Unit
    }
}
