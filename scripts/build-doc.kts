#!/usr/bin/env kscript

import java.io.File

fun runCommand(command: String) {
    ProcessBuilder().command(command.split(" ")).inheritIO().start().waitFor()
}

val tagName = System.getenv()

runCommand("./gradlew dokka -PTAG_NAME=${}")

File("README.md").readLines()
        .filter { !it.contains("project website") } // Remove the link to the project website for users landing on github
        .map { it.replace("docs/", "") } // Fix links
        .joinToString(separator = "\n", postfix = "\n")
        .let {
            File("docs/index.md").writeText(it)
        }


