package com.karim.foodrun.server

/** One-shot migration, never an automatic startup fallback. Run only with the old API stopped. */
object MigrateFirestoreToTurso {
    @JvmStatic fun main(args: Array<String>) {
        require(args.contentEquals(arrayOf("--old-server-stopped"))) { "Stop the old FoodRun API, then pass --old-server-stopped." }
        val environment = System.getenv()
        val sourceEnvironment = environment + mapOf("FOODRUN_STORAGE" to "firestore", "FOODRUN_FIRESTORE_BOOTSTRAP" to "false")
        val targetEnvironment = environment + ("FOODRUN_TURSO_BOOTSTRAP" to "true")
        try {
            TursoStore.configured(targetEnvironment).use { target ->
                requireNotNull(FirestoreStore.configured(sourceEnvironment)).use { source ->
                    val rows = target.importFrom(source)
                    println("Verified migration complete: $rows rows copied to Turso. Source records were preserved.")
                }
            }
        } catch (_: Exception) {
            System.err.println("Migration did not complete. Keep the target unpublished and preserve the source; check credentials, source quota and target namespace.")
            kotlin.system.exitProcess(1)
        }
    }
}
