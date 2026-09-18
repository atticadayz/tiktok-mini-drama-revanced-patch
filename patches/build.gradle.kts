import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.JavaExec
import org.gradle.jvm.tasks.Jar
import java.time.Instant

plugins {
    kotlin("jvm") version "2.3.10"
}

group = "local.tiktokminis"
version = (findProperty("patchesVersion") as String?) ?: "0.1.0"

dependencies {
    compileOnly("app.revanced:patcher:22.0.0")
    compileOnly("com.android.tools.smali:smali:3.0.5")
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        freeCompilerArgs.add("-Xcontext-parameters")
        freeCompilerArgs.add("-Xskip-prerelease-check")
    }
}

val r8: Configuration by configurations.creating
dependencies {
    r8("com.android.tools:r8:9.2.23")
}

val dexDir = layout.buildDirectory.dir("dex")

val dexPatches by tasks.registering(JavaExec::class) {
    dependsOn(tasks.named("compileKotlin"))

    val kotlinClasses = layout.buildDirectory.dir("classes/kotlin/main")
    inputs.dir(kotlinClasses)
    outputs.dir(dexDir)

    classpath = r8
    mainClass.set("com.android.tools.r8.D8")

    doFirst {
        val out = dexDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()

        val classFiles = fileTree(kotlinClasses).matching {
            include("**/*.class")
        }.files

        args(
            "--release",
            "--min-api", "26",
            "--output", out.absolutePath,
        )
        args(classFiles.map { it.absolutePath })
    }
}

tasks.named<Jar>("jar") {
    dependsOn(dexPatches)

    archiveBaseName.set("patches")
    archiveVersion.set(project.version.toString())
    archiveExtension.set("rvp")

    from(dexDir)

    manifest {
        attributes(
            "Name" to "TikTok Mini Drama Interstitial Patch",
            "Description" to "Disables TikTok Minis episode-switch interstitial ads",
            "Version" to project.version.toString(),
            "Timestamp" to Instant.now().toString(),
            "Source" to "local.tiktokminis",
            "Author" to "Local patch",
            "License" to "GPL-3.0-or-later",
        )
    }
}
