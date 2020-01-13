package com.dailymotion.kinta

import java.io.File
import java.util.*

object KintaConfig {
    private val properties = Properties()
    private val file = File(Project.findBaseDir(), "kinta.config")

    init {
        try {
            properties.load(file.inputStream())
        } catch (e: Exception) {
            // fail silently if we don't have any file yet
            //e.printStackTrace(System.err)
        }
    }

    fun put(key: String, value: String) {
        properties.put(key, value)
        properties.store(file.outputStream(), "Kinta Configuration file")
    }
    fun get(key: String) = properties.getProperty(key)
}