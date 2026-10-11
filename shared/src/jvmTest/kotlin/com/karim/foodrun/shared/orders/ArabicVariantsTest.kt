package com.karim.foodrun.shared.orders

import com.karim.foodrun.orders.*
import kotlin.test.*

class ArabicVariantsTest {
    @Test fun allArabicStylesUseRtlAndKeepRestaurantNamesArabic() {
        for((locale,word) in listOf("ar-EG" to "مبلغ تاني","ar-AE" to "مبلغ ثاني","ar-001" to "مبلغ آخر")) {
            assertEquals(word,GroupUiText.translate("Another amount",locale))
            val restaurant=Restaurant("r","Kitchen",nameAr="المطبخ")
            assertEquals("المطبخ",restaurant.localizedName(locale))
            val c=GroupController(GroupSettlementPresentationTestDevice())
            c.library=c.library.copy(language=locale)
            assertTrue(c.state.rtl); assertEquals(locale,c.state.language)
        }
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
