import java.util.Properties

// Task to generate Secrets.kt file
val generateSecretsTask by
  tasks.registering {
    group = "codegen"
    description = "Generate secrets from local.properties"

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
