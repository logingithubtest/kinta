package com.dailymotion.kinta.workflows.builtin

import com.dailymotion.kinta.integration.googleplay.GooglePlayIntegration
import com.github.ajalt.clikt.core.CliktCommand
import java.io.File
import java.io.PrintWriter


object PullPlayStoreListings : CliktCommand(name = "pullPlayStoreListings", help = "Pull listings from the Google Play") {

    private const val ANDROID_METADATA_FOLDER = "kintaSrc/metadata/android/"

    override fun run() {
        //Get Google Play listings
        val listings = GooglePlayIntegration.getListings()

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

    private fun writeTextFile(file: File, text: String){
        val writer = PrintWriter(file, "UTF-8")
        writer.println(text.trim())
        writer.close()
    }
}