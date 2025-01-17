/*
 * This file is part of logisim-evolution.
 *
 * Logisim-evolution is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by the
 * Free Software Foundation, either version 3 of the License, or (at your
 * option) any later version.
 *
 * Logisim-evolution is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with logisim-evolution. If not, see <http://www.gnu.org/licenses/>.
 *
 * Original code by Carl Burch (http://www.cburch.com), 2011.
 * Subsequent modifications by:
 *   + College of the Holy Cross
 *     http://www.holycross.edu
 *   + Haute École Spécialisée Bernoise/Berner Fachhochschule
 *     http://www.bfh.ch
 *   + Haute École du paysage, d'ingénierie et d'architecture de Genève
 *     http://hepia.hesge.ch/
 *   + Haute École d'Ingénierie et de Gestion du Canton de Vaud
 *     http://www.heig-vd.ch/
 */

import org.gradle.internal.os.OperatingSystem
import java.text.SimpleDateFormat
import java.util.Date
import java.io.*

plugins {
  checkstyle
  id("com.github.ben-manes.versions") version "0.38.0"
  java
  application
  id("com.github.johnrengelman.shadow") version "7.0.0"
}

repositories {
  mavenCentral()
}

application {
  mainClass.set("com.cburch.logisim.Main")
}

dependencies {
  implementation("org.hamcrest:hamcrest:2.2")
  implementation("javax.help:javahelp:2.0.05")
  implementation("com.fifesoft:rsyntaxtextarea:3.1.2")
  implementation("net.sf.nimrod:nimrod-laf:1.2")
  implementation("org.drjekyll:colorpicker:1.3")
  implementation("org.drjekyll:fontchooser:2.4")
  implementation("at.swimmesberger:swingx-core:1.6.8")
  implementation("org.scijava:swing-checkbox-tree:1.0.2")
  implementation("org.slf4j:slf4j-api:1.7.30")
  implementation("org.slf4j:slf4j-simple:1.7.30")
  implementation("com.formdev:flatlaf:1.2")

  compileOnly("org.jetbrains:annotations:22.0.0")

  // NOTE: Be aware of reported issues with Eclipse and Batik
  // See: https://github.com/logisim-evolution/logisim-evolution/issues/709
  // implementation("org.apache.xmlgraphics:batik-swing:1.14")

  testImplementation("org.junit.vintage:junit-vintage-engine:5.7.1")
}

/**
 * Strings used as keys to reference shared variables (via `ext.*`)
 */
val APP_DIR_NAME = "appDirName"
val APP_VERSION = "appVersion"
val APP_VERSION_SHORT = "appVersionShort"
val JPACKAGE = "jpackage"
val LIBS_DIR = "libsDir"
val LINUX_PARAMS = "linuxParameters"
val SHADOW_JAR_FILE_NAME = "shadowJarFilename"
val SHARED_PARAMS = "sharedParameters"
val SUPPORT_DIR = "supportDir"
val TARGET_DIR = "targetDir"
val TARGET_FILE_PATH_BASE = "targetFilePathBase"
val UPPERCASE_PROJECT_NAME = "uppercaseProjectName"

java {
  sourceCompatibility = JavaVersion.VERSION_14
  targetCompatibility = JavaVersion.VERSION_14
}

java {
  sourceSets["main"].java {
    srcDir("${buildDir}/generated/logisim/java")
    srcDir("${buildDir}/generated/sources/srcgen")
  }
}

task<Jar>("sourcesJar") {
  group = "build"
  description = "Creates a JAR archive with project sources."
  dependsOn.add("classes")
  classifier = "src"

  from(sourceSets.main.get().allSource)
}

/**
 * Setting up all shared vars and parameters.
 */
extra.apply {
  // NOTE: optional suffix is prefixed with `-` (because of how LogisimVersion class parses it), which
  // I remove here because `jpackage` tool do not like it when used to build RPM package.
  // Do NOT use `project.version` instead.
  val appVersion = (project.version as String).replace("-", "")
  set(APP_VERSION, appVersion)
  logger.info("appVersion: ${appVersion}")

  // Short (with suffix removed) version string, i.e. for "3.6.0beta1", short form is "3.6.0".
  // This is mostly used by createApp as version numbering rule is pretty strict on macOS.
  // Do NOT use `project.version` instead.
  val appVersionShort = (project.version as String).split('-')[0]
  set(APP_VERSION_SHORT, appVersionShort)
  logger.info("appVersionShort: ${appVersionShort}")

  // Destination folder where packages are stored.
  val targetDir="${buildDir}/dist"
  set(TARGET_DIR, targetDir)

  // JAR folder.
  val libsDir="${buildDir}/libs"
  set(LIBS_DIR, libsDir)

  // The root dir for jpackage extra files.
  val supportDir="${projectDir}/support/jpackage"
  set(SUPPORT_DIR, supportDir)

  // Base name of produced artifacts. Suffixes will be added later by relevant tasks.
  val baseFilename = "${project.name}-${appVersion}"
  set(TARGET_FILE_PATH_BASE, "${targetDir}/${baseFilename}")
  logger.debug("targetFilePathBase: \"${targetDir}/${baseFilename}\"")

  // Name of application shadowJar file.
  val shadowJarFilename = "${baseFilename}-all.jar"
  set(SHADOW_JAR_FILE_NAME, shadowJarFilename)
  logger.debug("shadowJarFilename: \"${shadowJarFilename}\"")

  // JDK/jpackage vars
  val javaHome = System.getProperty("java.home") ?: throw GradleException("java.home is not set")
  val jpackage = "${javaHome}/bin/jpackage"
  set(JPACKAGE, jpackage)

  // Copyrights note.
  val copyrights = "Copyright ©2001–${SimpleDateFormat("yyyy").format(Date())} ${project.name} developers"

  // Platform-agnostic jpackage parameters shared across all the builds.
  var params = listOf(
      jpackage,
      "--input", libsDir,
      "--main-class", "com.cburch.logisim.Main",
      "--main-jar", shadowJarFilename,
      "--app-version", appVersion,
      "--copyright", copyrights,
      "--dest", targetDir,
      "--description", "Digital logic design tool and simulator",
      "--vendor", "${project.name} developers",
  )
  if (logger.isDebugEnabled()) {
    params += listOf("--verbose")
  }
  set(SHARED_PARAMS, params)

  // Linux (DEB/RPM) specific settings for jpackage.
  val linuxParams = params + listOf(
      "--name", project.name,
      "--file-associations", "${supportDir}/linux/file.jpackage",
      "--icon", "${supportDir}/linux/logisim-icon-128.png",
      "--install-dir", "/opt",
      "--linux-shortcut"
  )
  set(LINUX_PARAMS, linuxParams)

  // All the macOS specific stuff.
  val uppercaseProjectName = project.name.capitalize().trim()
  set(UPPERCASE_PROJECT_NAME, uppercaseProjectName)
  set(APP_DIR_NAME, "${targetDir}/${uppercaseProjectName}.app")
}

/**
 * Creates distribution directory and checks if source.
 */
tasks.register("createDistDir") {
  val libsDir = ext.get(LIBS_DIR) as String

  group = "build"
  description = "Creates the directory for distribution."
  dependsOn("shadowJar")

  val shadowJarFilename = ext.get(SHADOW_JAR_FILE_NAME) as String

  inputs.dir(libsDir)
  outputs.dir(ext.get(TARGET_DIR) as String)

  doFirst {
    var jarFiles = File(libsDir).list()
    var jarCount = jarFiles.count()

    if ( jarCount > 1) {
      logger.warn("Expected 1 shadowJar file, found ${jarCount} in: ${libsDir}")

      val expectedShadowJar = File("${libsDir}/${shadowJarFilename}")
      if (expectedShadowJar.exists()) {
        logger.warn("Found needed: ${shadowJarFilename}")
      }

      for (file in jarFiles.filter { file -> file != shadowJarFilename }) {
        logger.warn("Will remove: ${file}")
      }
    }
  }

  doLast {
    val folder = File(libsDir)
    if (!folder.exists() && !folder.mkdirs()) {
      throw GradleException("Unable to create directory: ${folder.absolutePath}")
    }

    var jarFiles = File(libsDir).list()
    var jarCount = jarFiles.count()
    if ( jarCount > 1) {
      // Value of shadowJarFileName is our needed JAR file. Remove the rest.
      for (file in jarFiles.filter { file -> file != shadowJarFilename }) {
        if (!delete("${libsDir}/${file}")) {
          throw GradleException("Failed to remove orphaned file: ${file}")
        }
      }
    }
  }
}

/**
 * Helper method that simplifies runining external commands using ProcessBuilder().
 * Will throw GradleException on command failure (non-zero return code).
 *
 * params: List of strings which signifies the external program file to be invoked and its arguments (if any).
 * exMsg: Optional error message to be used with thrown exception on failure.
 *
 * Returns content of invoked app's stdout
 */
fun runCommand(params: List<String>, exceptionMsg: String): String {
  val procBuilder = ProcessBuilder()
  procBuilder
    .redirectOutput(ProcessBuilder.Redirect.PIPE)
    .redirectError(ProcessBuilder.Redirect.PIPE)
    .command(params)
  val proc = procBuilder.start()

  logger.debug("EXECUTING CMD: " + params.joinToString(" "))

  var rc = -1
  try {
    rc = proc.waitFor()
    logger.debug("CMD COMPLETED. RC: ${rc}")
  } catch (ex: Exception) {
    logger.error(ex.message)
    logger.error(ex.stackTraceToString())
  }

  if (rc != 0) {
    logger.error(proc.errorStream.bufferedReader().readText().trim())
    logger.error("Command \"${params[0]}\" failed with RC ${rc}.")
    var exMsg = exceptionMsg ?: ""
    throw GradleException(exMsg)
  }

  return proc.inputStream.bufferedReader().readText().trim()
}

/**
 * Task: createDeb
 *
 * Creates the Linux DEB package file (Debian, Ubuntu and derrivatives).
 */
tasks.register("createDeb") {
  group = "build"
  description = "Makes DEB Linux installation package."
  dependsOn("shadowJar", "createDistDir")
  inputs.dir(ext.get(LIBS_DIR) as String)
  inputs.dir("${ext.get(SUPPORT_DIR) as String}/linux")

  // Debian uses `_` to separate name from version string.
  // https://www.debian.org/doc/manuals/debian-faq/pkg-basics.en.html
  val appVersion = ext.get(APP_VERSION) as String
  val targetDir = ext.get(TARGET_DIR) as String
  val debPackagePath = "${targetDir}/${project.name}_${appVersion}-1_amd64.deb"
  outputs.file(debPackagePath)

  doFirst {
    if (!OperatingSystem.current().isLinux) {
      throw GradleException("This task runs on Linux only.")
    }
  }

  doLast {
    val params = ext.get(LINUX_PARAMS) as List<String> + listOf("--type", "deb")
    runCommand(params, "Error while creating the DEB package.")
  }
}

/**
 * Task: createRpm
 *
 * Creates the Linux RPM package file (RedHat and derrivatives).
 */
tasks.register("createRpm") {
  group = "build"
  description = "Makes RPM Linux installation package."
  dependsOn("shadowJar", "createDistDir")
  inputs.dir(ext.get(LIBS_DIR) as String)
  inputs.dir("${ext.get(SUPPORT_DIR) as String}/linux")
  outputs.file("${ext.get(TARGET_FILE_PATH_BASE) as String}-1.x86_64.rpm")

  doFirst {
    if (!OperatingSystem.current().isLinux) {
      throw GradleException("This task runs on Linux only.")
    }
  }

  doLast {
    val params = ext.get(LINUX_PARAMS) as List<String> + listOf("--type", "rpm")
    runCommand(params, "Error while creating the RPM package.")
  }
}

/**
 * Task: createMsi
 *
 * Creates MSI installater file for Microsoft Windows.
 */
tasks.register("createMsi") {
  group = "build"
  description = "Makes the Windows installation package."
  dependsOn("shadowJar", "createDistDir")

  val supportDir = ext.get(SUPPORT_DIR) as String;

  inputs.dir(ext.get(LIBS_DIR) as String)
  inputs.dir("${supportDir}/windows")
  outputs.file("${ext.get(TARGET_FILE_PATH_BASE) as String}.msi")

  doFirst {
    if (!OperatingSystem.current().isWindows) {
      throw GradleException("This task runs on Windows only.")
    }
  }

  doLast {
    val params = ext.get(SHARED_PARAMS) as List<String> + listOf(
        "--name", project.name,
        "--file-associations", "${supportDir}/windows/file.jpackage",
        "--icon", "${supportDir}/windows/Logisim-evolution.ico",
        "--win-menu-group", project.name as String,
        "--win-shortcut",
        "--win-dir-chooser",
        "--win-menu",
        "--type", "msi",
    )
    runCommand(params, "Error while creating the MSI package.")
  }
}

/**
 * Task: createApp
 *
 * Creates macOS application.
 */
tasks.register("createApp") {
  val supportDir = ext.get(SUPPORT_DIR) as String
  val appDirName = ext.get(APP_DIR_NAME) as String

  group = "build"
  description = "Makes the macOS application."
  dependsOn("shadowJar", "createDistDir")
  inputs.dir(ext.get(LIBS_DIR) as String)
  inputs.dir("${supportDir}/macos")
  outputs.dir(appDirName)

  doFirst {
    if (!OperatingSystem.current().isMacOsX) {
      throw GradleException("This task runs on macOS only.")
    }
  }

  doLast {
    delete(appDirName)

    var params = ext.get(SHARED_PARAMS) as List<String>
    params += listOf(
      "--name", ext.get(UPPERCASE_PROJECT_NAME) as String,
      "--file-associations", "${supportDir}/macos/file.jpackage",
      "--icon", "${supportDir}/macos/Logisim-evolution.icns",
      // app versioning is strictly checked for macOS. No suffix allowed for `app-image` type.
      "--app-version", ext.get(APP_VERSION_SHORT) as String,
      "--type", "app-image",
    )
    runCommand(params, "Error while creating the .app directory.")

    val targetDir = ext.get(TARGET_DIR) as String
    val pListFilename = "${appDirName}/Contents/Info.plist"
    runCommand(listOf(
      "awk",
      "/Unknown/{sub(/Unknown/,\"public.app-category.education\")};"
              + "{print >\"${targetDir}/Info.plist\"};"
              + "/NSHighResolutionCapable/{"
              + "print \"  <string>true</string>\" >\"${targetDir}/Info.plist\";"
              + "print \"  <key>NSSupportsAutomaticGraphicsSwitching</key>\" >\"${targetDir}/Info.plist\""
              + "}",
      pListFilename,
    ), "Error while patching Info.plist file.")

    runCommand(listOf(
      "mv", "${targetDir}/Info.plist", pListFilename
    ), "Error while moving Info.plist into the .app directory.")

    runCommand(listOf(
        "codesign", "--remove-signature", appDirName
    ), "Error while executing: codesign --remove-signature")
  }
}

/**
 * Task: createDmg
 *
 * Creates macOS DMG package file.
 */
tasks.register("createDmg") {
  group = "build"
  description = "Makes the macOS DMG package."
  dependsOn("createApp")

  val appDirName = ext.get(APP_DIR_NAME) as String

  inputs.dir(appDirName)
  outputs.file(ext.get(TARGET_FILE_PATH_BASE) as String + ".dmg")

  doFirst {
    if (!OperatingSystem.current().isMacOsX) {
      throw GradleException("This task runs on macOS only.")
    }
  }

  doLast {
    val params = listOf(
        ext.get(JPACKAGE) as String,
        "--app-image", appDirName,
        "--name", project.name,
        // We can pass full version here, even if contains suffix part too.
        "--app-version", ext.get(APP_VERSION) as String,
        "--dest", ext.get(TARGET_DIR) as String,
        "--type", "dmg",
      )
    runCommand(params, "Error while creating the DMG package")
  }
}

/**
 * Generates Java class file with project information like current version, branch name, last commit hash etc.
 */
fun genBuildInfo(buildInfoFilePath: String) {
  val now = Date()
  val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ").format(now)
  val branchName = runCommand(listOf("git", "-C", "$projectDir", "rev-parse", "--abbrev-ref", "HEAD"),
      "Failed getting branch name.")
  val branchLastCommitHash = runCommand(listOf("git", "-C", "$projectDir", "rev-parse", "--short=8", "HEAD"),
      "Failed getting last commit has.")
  val currentMillis = Date().time
  val buildYear = SimpleDateFormat("yyyy").format(now)

  val buildInfoClass = arrayOf(
    "// ************************************************************************",
    "// THIS IS COMPILE TIME GENERATED FILE! DO NOT EDIT BY HAND!",
    "// Generated at ${nowIso}",
    "// ************************************************************************",
    "",
    "package com.cburch.logisim.generated;",
    "",
    "import com.cburch.logisim.LogisimVersion;",
    "import java.util.Date;",
    "",
    "public final class BuildInfo {",
    "    // Build time VCS details",
    "    public static final String branchName = \"${branchName}\";",
    "    public static final String branchLastCommitHash = \"${branchLastCommitHash}\";",
    "    public static final String buildId = \"${branchName}/${branchLastCommitHash}\";",
    "",
    "    // Project build timestamp",
    "    public static final long millis = ${currentMillis}L;", // keep traling `L`
    "    public static final String year = \"${buildYear}\";",
    "    public static final String dateIso8601 = \"${nowIso}\";",
    "    public static final Date date = new Date();",
    "    static { date.setTime(millis); }",
    "",
    "    // Project version",
    "    public static final LogisimVersion version = LogisimVersion.fromString(\"${ext.get(APP_VERSION) as String}\");",
    "    public static final String name = \"${project.name.capitalize().trim()}\";",
    "} // End of generated BuildInfo",
    "",
  )

  logger.info("Generating: ${buildInfoFilePath}")
  val buildInfoFile = File(buildInfoFilePath)
  buildInfoFile.parentFile.mkdirs()
  file(buildInfoFilePath).writeText(buildInfoClass.joinToString("\n"))
}

/**
 * Task: genBuildInfo
 *
 * Wrapper task for genBuildInfo() method generating BuildInfo class.
 * No need to trigger it manually.
 */
tasks.register("genBuildInfo") {
  // Target location for generated files.
  val buildInfoDir = "${buildDir}/generated/logisim/java/com/cburch/logisim/generated"

  group = "build"
  description = "Creates Java class file with vital project information."

  // TODO: we should not have hardcoded path here but use default sourcesSet maybe?
  inputs.dir("${projectDir}/src")
  inputs.dir(ext.get(SUPPORT_DIR) as String)
  inputs.files("${projectDir}/gradle.properties", "${projectDir}/README.md", "${projectDir}/LICENSE.md")
  outputs.dir(buildInfoDir)

  doLast {
    // Full path to the Java class file to be generated.
    genBuildInfo("${buildInfoDir}/BuildInfo.java")
  }
}

/**
 * Task: jpackage
 *
 * Umbrella task to create packages for all supported platforms.
 */
tasks.register("createAll") {
  group = "build"
  description = "Makes the platform specific packages for the current platform."

  if (OperatingSystem.current().isLinux) {
    dependsOn("createDeb", "createRpm")
  }
  if (OperatingSystem.current().isWindows) {
    dependsOn("createMsi")
  }
  if (OperatingSystem.current().isMacOsX) {
    dependsOn("createDmg")
  }
}

/**
 * @deprecated. Use `createAll()`
 */
tasks.register("jpackage") {
  group = "build"
  var desc = "DEPRECATED: Use `createAll` task instead."
  description = desc
  dependsOn("createAll")

  doFirst {
    logger.warn(desc)
  }
}

val compilerOptions = listOf("-Xlint:deprecation", "-Xlint:unchecked")

tasks {
  compileJava {
    options.compilerArgs = compilerOptions
    dependsOn("genBuildInfo")
  }
  compileTestJava {
    options.compilerArgs = compilerOptions
    dependsOn("genBuildInfo")
  }

  jar {
    manifest {
      attributes.putAll(mapOf(
          "Implementation-Title" to name,
          "Implementation-Version" to archiveVersion
      ))
    }

    from(".") {
      include("LICENSE.md")
      include("README.md")
      include("CHANGES.md")
    }
  }

  shadowJar {
    archiveBaseName.set(project.name)
    archiveVersion.set(ext.get(APP_VERSION) as String)
    from(".") {
      include("LICENSE.md")
      include("README.md")
      include("CHANGES.md")
    }
  }

  // Checkstyles related tasks: "checkstylMain" and "checkstyleTest"
  checkstyle {
    // Checkstyle version to use
    toolVersion = "8.45"

    // let's use google_checks.xml config provided with Checkstyle.
    // https://stackoverflow.com/a/67513272/1235698
    val archive = configurations.checkstyle.get().resolve().filter {
      name.startsWith("checkstyle")
    }
    config = resources.text.fromArchiveEntry(archive, "google_checks.xml")

    // FIXME: There should be cleaner way of using custom suppression config with built-in style.
    // https://stackoverflow.com/a/64703619/1235698
    System.setProperty( "org.checkstyle.google.suppressionfilter.config", "${projectDir}/config/checkstyle/suppressions.xml")
  }
  checkstyleMain {
    source = fileTree("src/main/java")
  }
  checkstyleTest {
    source = fileTree("src/test/java")
  }
}
