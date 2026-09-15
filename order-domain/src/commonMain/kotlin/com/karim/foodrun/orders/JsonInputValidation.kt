package com.karim.foodrun.orders

/** Bounds untrusted JSON before decoding and rejects ambiguous duplicate object keys. */
object JsonInputValidation {
    fun validate(text: String) {
        require(text.length <= MenuValidation.MAX_BYTES && text.encodeToByteArray().size <= MenuValidation.MAX_BYTES) { "JSON exceeds 2 MB." }
        val objects = mutableListOf<MutableSet<String>?>()
        var index = 0
        while (index < text.length) {
            when (text[index]) {
                '{' -> objects.add(mutableSetOf())
                '[' -> objects.add(null)
                '}', ']' -> {
                    require(objects.isNotEmpty()) { "Invalid JSON structure." }
                    objects.removeAt(objects.lastIndex)
                }
                '"' -> {
                    val start = index++
                    var escaped = false
                    while (index < text.length) {
                        val character = text[index]
                        if (!escaped && character == '"') break
                        escaped = !escaped && character == '\\'
                        index++
                    }
                    require(index < text.length) { "Unterminated JSON string." }
                    var next = index + 1
                    while (next < text.length && text[next].isWhitespace()) next++
                    if (next < text.length && text[next] == ':') {
                        val keys = requireNotNull(objects.lastOrNull()) { "Invalid JSON property." }
                        val key = orderJson.decodeFromString<String>(text.substring(start, index + 1))
                        require(keys.add(key)) { "Duplicate JSON property: ${key.take(80)}." }
                    }
                }
            }
            require(objects.size <= 24) { "JSON nesting is too deep." }
            index++
        }
        require(objects.isEmpty()) { "Incomplete JSON structure." }
    }
}
