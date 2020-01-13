package com.dailymotion.kinta.command

import com.dailymotion.kinta.Workflows
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.subcommands
import java.io.File
import java.net.URLClassLoader
import java.util.*

fun loadCommands(): List<CliktCommand> {
    val fileBuiltInWorflowsJar = File("workflows-builtin/build/libs/kinta-workflows-builtin.jar")
    val fileCustomWorkflowsJar = File("./kintaSrc/build/libs/kinta-workflows-custom.jar")

    val urlClassLoader = URLClassLoader(
            arrayOf(
                    fileBuiltInWorflowsJar.toURI().toURL(),
                    fileCustomWorkflowsJar.toURI().toURL()),
            ClassLoader.getSystemClassLoader())
    val loader = ServiceLoader.load(Workflows::class.java, urlClassLoader)

    val listCommand: MutableList<CliktCommand> = mutableListOf()
    try {
        loader.forEach { listCommand.addAll(it.all()) }
    } catch (serviceError: ServiceConfigurationError) {
        serviceError.printStackTrace()
    }
    return listCommand.sortedBy { it.commandName }
}

val Run = object: CliktCommand(name = "run", help = "") {
    override fun run() {
    }
}.subcommands(loadCommands())