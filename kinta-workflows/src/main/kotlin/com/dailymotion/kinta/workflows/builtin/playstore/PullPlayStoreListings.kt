package com.dailymotion.kinta.workflows.builtin.playstore

import com.dailymotion.kinta.integration.googleplay.GooglePlayIntegration
import com.github.ajalt.clikt.core.CliktCommand
import java.io.File
import java.io.PrintWriter


object PullPlayStoreListings : CliktCommand(name = "pullPlayStoreListings", help = "Pull listings from the Google Play") {

    private val ANDROID_METADATA_FOLDER = File("kintaSrc/metadata/android/")

    override fun run() {
        //Get Google Play listings
        val listings = GooglePlayIntegration.getListings()

        //Delete txt files from local resources
        ANDROID_METADATA_FOLDER.listFiles()?.filter { it.isDirectory }?.map {
            //Remove txt files
            it.listFiles()?.filter { !it.isDirectory }?.forEach { txtFile ->
                txtFile.delete()
            }
            //Remove language directory if empty or contains empty folder
            if (isDirectoryEmpty(it)) {
                it.delete()
            }
        } ?: listOf()

        //Update local resources
        listings.groupBy { it.language }.map {
            println("Processing language : ${it.key}")
            val folder = File(ANDROID_METADATA_FOLDER, it.key).apply {
                mkdirs()
            }
            it.value.forEach {
                it.title?.let { writeTextFile(File(folder, "title.txt"), it) }
                it.shortDescription?.let { writeTextFile(File(folder, "short_description.txt"), it) }
                it.description?.let { writeTextFile(File(folder, "full_description.txt"), it) }
                it.video?.let { writeTextFile(File(folder, "video.txt"), it) }
            }
        }


    }

    private fun writeTextFile(file: File, text: String) {
        val writer = PrintWriter(file, "UTF-8")
        writer.println(text.trim())
        writer.close()
    }

    private fun isDirectoryEmpty(file: File): Boolean {
        if (!file.isDirectory) {
            return false
        }
        return file.listFiles()?.find { !isDirectoryEmpty(it) } == null
    }
}