#!/usr/bin/env kscript

import java.io.File
import kotlin.contracts.contract

fun runCommand(command: String) {
    val code = ProcessBuilder().command(command.split(" ")).inheritIO().start().waitFor()
    if (code != 0) {
        System.exit(1)
    }
}

fun usage() {
    println("./scripts/build-doc.kts --version=x.y.z")
    System.exit(1)
}

val tag = args.firstOrNull { it.startsWith("--version=") }

if (tag == null) {
    usage()
}

val tagName = tag!!.substringAfter("--version=")

// generate doc, it will go in build/kdoc/
runCommand("./gradlew dokka")

File("kdoc").mkdir()
File("build/kdoc").copyRecursively(File("kdoc/$tagName"))

File("kdoc").copyRecursively(File("docs"))

File("README.md").readLines()
        .filter { !it.contains("project website") } // Remove the link to the project website for users landing on github
        .map { it.replace("docs/", "") } // Fix links
        .joinToString(separator = "\n", postfix = "\n")
        .let {
            File("docs/index.md").writeText(it)
        }


