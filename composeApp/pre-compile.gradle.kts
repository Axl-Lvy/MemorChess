import java.util.Properties

// Task to generate Secrets.kt file
val generateSecretsTask by
  tasks.registering {
    group = "codegen"
    description = "Generate secrets from local.properties"

    // Capture directory values at configuration time
    val projectDirPath = projectDir.absolutePath
    val rootProjectDirPath = rootProject.projectDir.absolutePath

    // Declare inputs
    val moduleProps = File(projectDirPath, "local.properties")
    val globalProps = File(rootProjectDirPath, "local.properties")
    val propsFile =
      when {
        moduleProps.exists() -> moduleProps
        globalProps.exists() -> globalProps
        else -> null
      }

    if (propsFile != null) {
      inputs.file(propsFile)
    }

    // Declare outputs - inline getGeneratedFileName logic
    val secretsPackageDir =
      File("$projectDirPath/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
    val secretsFile = File(secretsPackageDir, "Secrets.kt")
    outputs.file(secretsFile)

    // Force task to always run
    outputs.upToDateWhen { false }

    doLast {
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

      if (!secretsPackageDir.exists()) {
        secretsPackageDir.mkdirs()
      }

      // Inline toCamelCase logic
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

      // Inline addToGitIgnore logic
      val gitIgnoreFile = File(projectDirPath, ".gitignore")
      val relativePath = secretsFile.relativeTo(File(projectDirPath)).path.replace("\\", "/")

      if (!gitIgnoreFile.exists()) {
        gitIgnoreFile.writeText("# Auto-generated .gitignore\n$relativePath\n")
      } else {
        val existing = gitIgnoreFile.readText()
        if (!existing.contains(relativePath)) {
          gitIgnoreFile.appendText("\n$relativePath\n")
        }
      }

      logger.lifecycle("🔐 Generated Secrets.kt from local.properties")
    }
  }

// Task to clean Secrets.kt file
val cleanSecretsTask by
  tasks.registering {
    group = "codegen"
    description = "Clean generated Secrets.kt file"
    val projectDirValue = projectDir.absolutePath
    // Inline getGeneratedFileName logic
    val secretsPackageDir =
      File("$projectDirValue/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
    val secretsFile = File(secretsPackageDir, "Secrets.kt")

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
