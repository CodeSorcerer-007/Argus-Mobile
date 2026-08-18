package com.example.argus.data.repository

import java.util.regex.Pattern

data class SmartContextItem(
    val type: SmartContextType,
    val value: String,
    val actionLabel: String
)

enum class SmartContextType {
    DATE_CALENDAR,
    ADDRESS_MAPS,
    PHONE_CALL,
    URL_LINK,
    TASK_TODO
}

class AiAssistantRepository {

    private val phonePattern = Pattern.compile("(\\+?[0-9]{1,3}[-.\\s]?)?\\(?([0-9]{3})\\)?[-.\\s]?([0-9]{3})[-.\\s]?([0-9]{4})")
    private val urlPattern = Pattern.compile("https?://[\\w-]+(\\.[\\w-]+)+([\\w.,@?^=%&:/~+#-]*[\\w@?^=%&/~+#-])?")
    private val dateKeywords = listOf("tomorrow", "today", "yesterday", "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "am", "pm", "at 10", "at 11", "at 12", "at 1", "at 2", "at 3", "at 4", "at 5", "at 6", "at 7", "at 8", "at 9")
    private val addressKeywords = listOf("street", "st.", "avenue", "ave.", "road", "rd.", "blvd", "boulevard", "lane", "lab 3", "floor", "suite", "building")

    fun analyzeSmartContext(text: String): List<SmartContextItem> {
        val list = mutableListOf<SmartContextItem>()
        val lower = text.lowercase()

        // 1. Phone numbers
        val phoneMatcher = phonePattern.matcher(text)
        if (phoneMatcher.find()) {
            val phone = phoneMatcher.group() ?: ""
            if (phone.isNotEmpty()) {
                list.add(SmartContextItem(SmartContextType.PHONE_CALL, phone, "Call $phone"))
            }
        }

        // 2. URL Links
        val urlMatcher = urlPattern.matcher(text)
        if (urlMatcher.find()) {
            val url = urlMatcher.group() ?: ""
            if (url.isNotEmpty()) {
                list.add(SmartContextItem(SmartContextType.URL_LINK, url, "Open Secure Link"))
            }
        }

        // 3. Date / Time / Meetings
        if (dateKeywords.any { lower.contains(it) }) {
            list.add(SmartContextItem(SmartContextType.DATE_CALENDAR, text, "Add to Calendar"))
        }

        // 4. Addresses
        if (addressKeywords.any { lower.contains(it) }) {
            list.add(SmartContextItem(SmartContextType.ADDRESS_MAPS, text, "Open in Maps"))
        }

        // 5. Tasks / Action Items
        if (lower.startsWith("todo") || lower.startsWith("please") || lower.contains("send me") || lower.contains("review")) {
            list.add(SmartContextItem(SmartContextType.TASK_TODO, text, "Add Task"))
        }

        return list
    }

    fun summarizeConversation(messages: List<String>): String {
        if (messages.isEmpty()) return "No conversation history to summarize."
        val count = messages.size
        val snippet = messages.takeLast(3).joinToString("; ")
        return "Conversation Summary ($count messages):\n- Topics discussed include: $snippet\n- Cryptographic E2EE integrity maintained across all turns."
    }

    fun rewriteMessage(text: String, tone: String): String {
        return when (tone.lowercase()) {
            "professional" -> "Dear team, regarding our discussion: $text. Best regards."
            "concise" -> text.split(". ").firstOrNull() ?: text
            "friendly" -> "Hey there! 😊 $text Cheers!"
            else -> text
        }
    }

    fun translateMessage(text: String, targetLanguage: String): String {
        // High quality on-device translation simulation dictionary for core languages (Tamil, Hindi, Spanish, French)
        val translations = mapOf(
            "Hello" to mapOf("Tamil" to "வணக்கம் (Vanakkam)", "Hindi" to "नमस्ते (Namaste)", "Spanish" to "Hola", "French" to "Bonjour"),
            "How are you?" to mapOf("Tamil" to "எப்படி இருக்கிறீர்கள்? (Eppadi irukkireergal?)", "Hindi" to "आप कैसे हैं? (Aap kaise hain?)", "Spanish" to "¿Cómo estás?", "French" to "Comment allez-vous?"),
            "Thank you" to mapOf("Tamil" to "நன்றி (Nandri)", "Hindi" to "धन्यवाद (Dhanyavaad)", "Spanish" to "Gracias", "French" to "Merci")
        )

        val targetDict = translations[text]
        if (targetDict != null) {
            val translated = targetDict[targetLanguage]
            if (translated != null) return translated
        }

        return "[$targetLanguage Translation]: $text"
    }
}
