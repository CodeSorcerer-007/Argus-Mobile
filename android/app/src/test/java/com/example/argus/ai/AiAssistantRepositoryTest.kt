package com.example.argus.ai

import com.example.argus.data.repository.AiAssistantRepository
import com.example.argus.data.repository.SmartContextType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAssistantRepositoryTest {

    private val repository = AiAssistantRepository()

    @Test
    fun testSmartContextPhoneExtraction() {
        val text = "Please call me back at +1-555-234-5678 regarding the security key."
        val items = repository.analyzeSmartContext(text)

        val phoneItem = items.firstOrNull { it.type == SmartContextType.PHONE_CALL }
        assertTrue(phoneItem != null)
        assertTrue(phoneItem!!.value.contains("555-234-5678"))
    }

    @Test
    fun testSmartContextCalendarMeetingExtraction() {
        val text = "Let's review the cryptographic audit tomorrow at 10 am in Lab 3."
        val items = repository.analyzeSmartContext(text)

        val calendarItem = items.firstOrNull { it.type == SmartContextType.DATE_CALENDAR }
        assertTrue(calendarItem != null)
        assertEquals("Add to Calendar", calendarItem!!.actionLabel)

        val addressItem = items.firstOrNull { it.type == SmartContextType.ADDRESS_MAPS }
        assertTrue(addressItem != null)
        assertEquals("Open in Maps", addressItem!!.actionLabel)
    }

    @Test
    fun testSmartContextUrlExtraction() {
        val text = "Check out the repo at https://github.com/argus-sec/messenger for whitepaper details."
        val items = repository.analyzeSmartContext(text)

        val urlItem = items.firstOrNull { it.type == SmartContextType.URL_LINK }
        assertTrue(urlItem != null)
        assertTrue(urlItem!!.value.startsWith("https://github.com"))
    }

    @Test
    fun testSummarization() {
        val messages = listOf(
            "Hello team, welcome to Argus.",
            "Double Ratchet session initialized.",
            "PreKey exchange verified."
        )
        val summary = repository.summarizeConversation(messages)
        assertTrue(summary.contains("Conversation Summary (3 messages)"))
        assertTrue(summary.contains("PreKey exchange verified"))
    }

    @Test
    fun testTranslation() {
        val helloInTamil = repository.translateMessage("Hello", "Tamil")
        assertTrue(helloInTamil.contains("வணக்கம்"))

        val helloInHindi = repository.translateMessage("Hello", "Hindi")
        assertTrue(helloInHindi.contains("नमस्ते"))
    }

    @Test
    fun testToneRewriting() {
        val professional = repository.rewriteMessage("need the report asap", "professional")
        assertTrue(professional.contains("Dear team"))
        assertTrue(professional.contains("Best regards"))

        val friendly = repository.rewriteMessage("see you soon", "friendly")
        assertTrue(friendly.contains("Hey there!"))
    }
}
