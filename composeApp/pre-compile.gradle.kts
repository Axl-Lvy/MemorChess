import java.util.Properties

// Task to generate Secrets.kt file
val generateSecretsTask by
  tasks.registering {
    group = "codegen"
    description = "Generate secrets from local.properties"

    // Force task to always run
    outputs.upToDateWhen { false }
    doNotTrackState("Always regenerate secrets to ensure they're current")

    val projectDirValue = projectDir
    val rootProjectDirValue = rootProject.projectDir

    // Determine which local.properties file to use
    val moduleProps = File(projectDirValue, "local.properties")
    val globalProps = File(rootProjectDirValue, "local.properties")
    val propsFile =
      when {
        moduleProps.exists() -> moduleProps
        globalProps.exists() -> globalProps
        else -> null
      }

    // If no local.properties file is found, properties will be empty
    val properties =
      if (propsFile == null) {
        Properties()
      } else {
        if (!propsFile.exists()) {
          error("local.properties file not found at ${propsFile.absolutePath}")
        }
        Properties().apply { load(propsFile.inputStream()) }
      }

    // Create target directory and file
    val (secretsPackageDir, secretsFile) = getGeneratedFileName(projectDirValue)

    if (!secretsPackageDir.exists()) {
      secretsPackageDir.mkdirs()
    }

    // Generate Secrets.kt content
    val content = buildString {
      appendLine("package proj.memorchess.axl.core.config.generated")
      appendLine()
      appendLine("import proj.memorchess.axl.core.config.SecretsTemplate")
      appendLine()
      appendLine("/**")
      appendLine(" * Contains secrets stored in local.properties.")
      appendLine(" *")
      appendLine(" * Automatically generated file. DO NOT EDIT!")
      appendLine(" */")
      appendLine("object Secrets : SecretsTemplate() {")

      properties.forEach { (key, value) ->
        val originalKey = key.toString()
        val camelCaseKey = originalKey.toCamelCase()

        // Validate that the camelCase key is a valid Kotlin identifier
        if (Regex("^[a-zA-Z_][a-zA-Z0-9_]*$").matches(camelCaseKey)) {
          appendLine("    override val $camelCaseKey = \"$value\"")
        }
      }

      appendLine("}")
    }

    secretsFile.writeText(content)

    // Add Secrets.kt to .gitignore
    addToGitIgnore(projectDirValue, secretsFile)
  }

// Task to clean Secrets.kt file
val cleanSecretsTask by
  tasks.registering {
    group = "codegen"
    description = "Clean generated Secrets.kt file"
    val projectDirValue = projectDir
    val (secretsPackageDir, secretsFile) = getGeneratedFileName(projectDirValue)

    doLast {
      if (secretsFile.exists()) {
        secretsFile.delete()
        logger.info("🧹 Deleted generated Secrets.kt file")
      }

      // Also remove the directory if it's empty and only contains generated files
      if (secretsPackageDir.exists() && secretsPackageDir.listFiles()?.isEmpty() == true) {
        secretsPackageDir.delete()
        logger.info("🧹 Removed empty generated directory")
      }
    }
  }

// Helper functions
fun getGeneratedFileName(projectDir: File): Pair<File, File> {
  val secretsPackageDir =
    File("$projectDir/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
  val secretsFile = File(secretsPackageDir, "Secrets.kt")
  return Pair(secretsPackageDir, secretsFile)
}

fun addToGitIgnore(projectDir: File, secretsFile: File) {
  val gitIgnoreFile = File(projectDir, ".gitignore")
  val relativePath = secretsFile.relativeTo(projectDir).path.replace("\\", "/")

  if (!gitIgnoreFile.exists()) {
    gitIgnoreFile.writeText("# Auto-generated .gitignore\n$relativePath\n")
  } else {
    val existing = gitIgnoreFile.readText()
    if (!existing.contains(relativePath)) {
      gitIgnoreFile.appendText("\n$relativePath\n")
    }
  }
}

fun String.toCamelCase(): String {
  return split('_')
    .mapIndexed { index, part ->
      if (index == 0) {
        part.lowercase()
      } else {
        part.lowercase().replaceFirstChar { it.uppercase() }
      }
    }
    .joinToString("")
}

tasks
  .matching { it.name.contains("compile", ignoreCase = true) }
  .configureEach { dependsOn(generateSecretsTask) }

tasks.matching { it.name == "clean" }.configureEach { dependsOn(cleanSecretsTask) }

// Task to apply Supabase SQL functions
// Gradle cache is used to avoid reapplying functions if they haven't changed
val applySupabaseFunctions by
  tasks.registering {
    group = "codegen"
    val sqlDir = file("../supabase/functions")

    // Tell Gradle: these files are inputs
    inputs.files(sqlDir.listFiles() ?: emptyArray<File>())

    // Tell Gradle: this file is the "result" marker
    val outputMarker = layout.buildDirectory.file(".supabaseFunctionsApplied")
    outputs.file(outputMarker)
    val supabaseDbLink = createSupabaseDbLink()

    doLast {
      if (supabaseDbLink.isEmpty()) {
        logger.error(
          "Could not update the supabase functions as supabaseDbLink is missing in local.properties."
        )
        return@doLast
      }

      val sqlFiles = sqlDir.listFiles()?.sortedBy { it.name } ?: emptyList()

      if (sqlFiles.isEmpty()) {
        logger.lifecycle("No SQL files found in the directory: ${sqlDir.absolutePath}")
        return@doLast
      }

      var allSucceeded = true

      sqlFiles.forEach { sqlFile ->
        val processBuilder =
          ProcessBuilder("psql", "-f", sqlFile.absolutePath, supabaseDbLink).apply {
            directory(sqlDir)
            redirectErrorStream(false)
          }

        val process = processBuilder.start()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
          logger.warn("Failed to apply: ${sqlFile.name} (exit code: $exitCode)")
          allSucceeded = false
        }
      }

      if (allSucceeded) {
        outputMarker.get().asFile.writeText("Last applied at ${System.currentTimeMillis()}")
        logger.lifecycle("Finished applying SQL functions to database.")
      } else {
        logger.warn("Some SQL files failed to apply.")
      }
    }
  }

private fun createSupabaseDbLink(): String {
  val localProperties =
    Properties().apply {
      val file = rootProject.file("local.properties")
      if (file.exists()) {
        file.inputStream().use { load(it) }
      } else {
        logger.error("local.properties file not found.")
        return ""
      }
    }
  val host = localProperties.getProperty(".supabase_db_host")
  val port = localProperties.getProperty(".supabase_db_port")
  val dbName = localProperties.getProperty(".supabase_db_name")
  val user = localProperties.getProperty(".supabase_db_user")
  val password = localProperties.getProperty(".supabase_db_password")
  if (
    host.isNullOrEmpty() ||
      port.isNullOrEmpty() ||
      dbName.isNullOrEmpty() ||
      user.isNullOrEmpty() ||
      password.isNullOrEmpty()
  ) {
    logger.error("One or more Supabase database properties are missing in local.properties.")
    return ""
  }
  return "postgresql://$user:$password@$host:$port/$dbName"
}

tasks
  .matching { it.name.contains("compile", ignoreCase = true) }
  .configureEach { dependsOn(applySupabaseFunctions) }
