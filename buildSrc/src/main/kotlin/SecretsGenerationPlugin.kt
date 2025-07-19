
import org.gradle.api.Plugin
import org.gradle.api.Project
import java.io.File
import java.util.Properties

/**
 * Plugin that configures secrets generation for Kotlin Multiplatform projects. Generates a
 * Secrets.kt file from local.properties for all KMP targets.
 */
class SecretsPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.afterEvaluate { configureSecretsGeneration() }
  }

  private fun Project.configureSecretsGeneration() {
    val generateSecretsTask =
      tasks.register("generateSecrets") {
        group = "codegen"
        description = "Generate secrets from local.properties"
        doLast { generateSecretFile(this@configureSecretsGeneration) }
      }

    val cleanSecretsTask =
      tasks.register("cleanSecrets") {
        group = "codegen"
        description = "Delete generated secrets file"
        doLast { cleanGeneratedSecrets(this@configureSecretsGeneration) }
      }

    // Hook into compile tasks
    tasks
      .matching { it.name.contains("compile", ignoreCase = true) }
      .configureEach { dependsOn(generateSecretsTask) }

    // Hook into clean task
    tasks.matching { it.name == "clean" }.configureEach { dependsOn(cleanSecretsTask) }
  }

  /** Converts a snake_case string to camelCase. */
  private fun String.toCamelCase(): String {
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

  /**
   * Generates a Secrets.kt file containing values from local.properties.
   *
   * @param project The project context
   */
  private fun generateSecretFile(project: Project) {
    // Determine which local.properties file to use
    val moduleProps = project.file("local.properties")
    val globalProps = project.rootProject.file("local.properties")
    val propsFile =
      when {
        moduleProps.exists() -> moduleProps
        globalProps.exists() -> globalProps
        else -> error("❌ No local.properties found for module: ${project.name}")
      }

    if (!propsFile.exists()) {
      error("local.properties file not found at ${propsFile.absolutePath}")
    }

    // Load properties from file
    val properties = Properties().apply { load(propsFile.inputStream()) }

    // Create target directory and file
    val (secretsPackageDir, secretsFile) = getGeneratedFileName(project)

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
    addToGitIgnore(project, secretsFile)
  }

  /**
   * Cleans up the generated Secrets.kt file.
   *
   * @param project The project context
   */
  private fun cleanGeneratedSecrets(project: Project) {
    val (secretsPackageDir, secretsFile) = getGeneratedFileName(project)

    if (secretsFile.exists()) {
      secretsFile.delete()
      project.logger.info("🧹 Deleted generated Secrets.kt file")
    }

    // Also remove the directory if it's empty and only contains generated files
    if (secretsPackageDir.exists() && secretsPackageDir.listFiles()?.isEmpty() == true) {
      secretsPackageDir.delete()
      project.logger.info("🧹 Removed empty generated directory")
    }
  }

  private fun getGeneratedFileName(project: Project): Pair<File, File> {
    val secretsPackageDir =
      File("${project.projectDir}/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
    val secretsFile = File(secretsPackageDir, "Secrets.kt")
    return Pair(secretsPackageDir, secretsFile)
  }

  /** Adds the generated Secrets.kt file to .gitignore if not already present. */
  private fun addToGitIgnore(project: Project, secretsFile: File) {
    val gitIgnoreFile = project.file(".gitignore")
    val relativePath = secretsFile.relativeTo(project.projectDir).path.replace("\\", "/")

    if (!gitIgnoreFile.exists()) {
      gitIgnoreFile.writeText("# Auto-generated .gitignore\n$relativePath\n")
    } else {
      val existing = gitIgnoreFile.readText()
      if (!existing.contains(relativePath)) {
        gitIgnoreFile.appendText("\n$relativePath\n")
      }
    }
  }
}
