package com.karim.foodrun

import java.io.File
import java.util.UUID

/** Each granted URI keeps its original receipt even when the next export has the same name. */
internal fun createGroupExportFile(cacheDirectory: File, fileName: String, text: String): File {
    require(fileName.matches(Regex("[a-zA-Z0-9_.-]{1,80}")) && !fileName.startsWith('.'))
    val directory = File(File(cacheDirectory, "exports"), UUID.randomUUID().toString())
    check(directory.mkdirs()) { "Could not prepare the export. Check device storage and retry." }
    return File(directory, fileName).apply { writeText(text) }
}
