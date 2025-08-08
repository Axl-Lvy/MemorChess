import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.DefaultTask
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
      tasks.register("generateSecrets", GenerateSecretsTask::class.java) {
        group = "codegen"
        description = "Generate secrets from local.properties"
        projectDir.set(project.projectDir)
        rootProjectDir.set(project.rootProject.projectDir)
        projectName.set(project.name)
      }

    val cleanSecretsTask =
      tasks.register("cleanSecrets", CleanSecretsTask::class.java) {
        group = "codegen"
        description = "Delete generated secrets file"
        projectDir.set(project.projectDir)
      }

    // Hook into compile tasks
    tasks
      .matching { it.name.contains("compile", ignoreCase = true) }
      .configureEach { dependsOn(generateSecretsTask) }

    // Hook into clean task
    tasks.matching { it.name == "clean" }.configureEach { dependsOn(cleanSecretsTask) }
  }
}

abstract class GenerateSecretsTask : DefaultTask() {
  @get:Input
  abstract val projectDir: Property<File>

  @get:Input
  abstract val rootProjectDir: Property<File>

  @get:Input
  abstract val projectName: Property<String>

  @TaskAction
  fun generateSecrets() {
    val projectDirValue = projectDir.get()
    val rootProjectDirValue = rootProjectDir.get()
    val projectNameValue = projectName.get()

    // Determine which local.properties file to use
    val moduleProps = File(projectDirValue, "local.properties")
    val globalProps = File(rootProjectDirValue, "local.properties")
    val propsFile =
      when {
        moduleProps.exists() -> moduleProps
        globalProps.exists() -> globalProps
        else -> error("❌ No local.properties found for module: $projectNameValue")
      }

    if (!propsFile.exists()) {
      error("local.properties file not found at ${propsFile.absolutePath}")
    }

    // Load properties from file
    val properties = Properties().apply { load(propsFile.inputStream()) }

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

  private fun getGeneratedFileName(projectDir: File): Pair<File, File> {
    val secretsPackageDir =
      File("$projectDir/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
    val secretsFile = File(secretsPackageDir, "Secrets.kt")
    return Pair(secretsPackageDir, secretsFile)
  }

  /** Adds the generated Secrets.kt file to .gitignore if not already present. */
  private fun addToGitIgnore(projectDir: File, secretsFile: File) {
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
}

abstract class CleanSecretsTask : DefaultTask() {
  @get:Input
  abstract val projectDir: Property<File>

  @TaskAction
  fun cleanSecrets() {
    val projectDirValue = projectDir.get()
    val (secretsPackageDir, secretsFile) = getGeneratedFileName(projectDirValue)

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

  private fun getGeneratedFileName(projectDir: File): Pair<File, File> {
    val secretsPackageDir =
      File("$projectDir/src/commonMain/kotlin/proj/memorchess/axl/core/config/generated")
    val secretsFile = File(secretsPackageDir, "Secrets.kt")
    return Pair(secretsPackageDir, secretsFile)
  }
}
