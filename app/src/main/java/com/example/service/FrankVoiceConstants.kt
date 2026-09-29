package com.example.service

object FrankVoiceConstants {
    const val WAKE_WORD = "Hey Frank"
    const val DEFAULT_HOTWORD_KEYWORD = "hey frank"
    
    // Normalized wake phrases that count as triggering Frank
    val WAKE_VARIANTS = listOf(
        "hey frank",
        "hay frank",
        "a frank",
        "hi frank",
        "hello frank",
        "okay frank",
        "ok frank",
        "frank"
    )

    @Volatile
    var customWakeVariants: List<String> = emptyList()

    private fun getAllVariants(): List<String> {
        return (customWakeVariants + WAKE_VARIANTS).distinct().sortedByDescending { it.length }
    }

    /**
     * Checks if the spoken text contains a wake command ("Hey Frank ...")
     * and strips the wake word to extract the intended command payload.
     * Also strips common polite fillers ("please", "can you", "could you").
     * Returns a pair of (isWakeTriggered: Boolean, extractedCommand: String).
     */
    fun extractCommandFromWakeWord(spokenText: String): Pair<Boolean, String> {
        val raw = spokenText.trim()
        val lower = raw.lowercase()
        val variants = getAllVariants()

        // 1. Check exact wake word match
        for (variant in variants) {
            if (lower == variant || lower == "$variant." || lower == "$variant!" || lower == "$variant?") {
                return Pair(true, "")
            }
        }

        // 2. Check wake word at the beginning: "Hey Frank, open camera" or "Jarvis, what time is it"
        for (variant in variants) {
            if (lower.startsWith("$variant ") || lower.startsWith("$variant,") || lower.startsWith("$variant.")) {
                val idx = lower.indexOf(variant)
                if (idx != -1) {
                    val after = raw.substring(idx + variant.length).trimStart(' ', ',', '.', '!', '?').trim()
                    return Pair(true, cleanPolitePhrasing(after))
                }
            }
        }

        // 3. Check wake word at the end: "Open camera, Hey Frank" or "Open camera Jarvis"
        for (variant in variants) {
            if (lower.endsWith(" $variant") || lower.endsWith(", $variant") || lower.endsWith(". $variant")) {
                val cut = raw.substring(0, raw.length - variant.length).trimEnd(' ', ',', '.', '!', '?').trim()
                if (cut.isNotBlank()) {
                    return Pair(true, cleanPolitePhrasing(cut))
                }
            }
        }

        // 4. Check wake word inside query: "Can you please, Hey Frank, turn on torch"
        for (variant in variants) {
            val token = " $variant "
            if (lower.contains(token)) {
                val idx = lower.indexOf(token)
                val before = raw.substring(0, idx).trim()
                val after = raw.substring(idx + token.length).trim()
                val combined = "$before $after".trim()
                return Pair(true, cleanPolitePhrasing(combined))
            }
        }

        return Pair(false, cleanPolitePhrasing(raw))
    }

    private fun cleanPolitePhrasing(input: String): String {
        var res = input.trim()
        val prefixesToStrip = listOf(
            "please ",
            "can you please ",
            "could you please ",
            "would you please ",
            "can you ",
            "could you ",
            "would you ",
            "will you ",
            "kindly ",
            "go ahead and ",
            "i want you to ",
            "i need you to "
        )
        for (prefix in prefixesToStrip) {
            if (res.lowercase().startsWith(prefix)) {
                res = res.substring(prefix.length).trim()
                break
            }
        }
        return res
    }
}
