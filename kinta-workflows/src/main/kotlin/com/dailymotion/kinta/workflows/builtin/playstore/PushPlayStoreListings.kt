package com.dailymotion.kinta.workflows.builtin.playstore

import com.dailymotion.kinta.integration.googleplay.GooglePlayIntegration
import com.github.ajalt.clikt.core.CliktCommand
import java.io.*


object PushPlayStoreListings : CliktCommand(name = "pushPlayStoreListings", help = "Push listings to the Google Play") {

    private val ANDROID_METADATA_FOLDER = File("kintaSrc/metadata/android/")

    override fun run() {
        /**
         * Make sure an PullPlayStoreListing has been done once
         */
        if (!ANDROID_METADATA_FOLDER.exists()) {
            println("$ANDROID_METADATA_FOLDER not found.")
            println("Make sure to call 'kinta run pullPlayStoreListings' first")
            return
        }
        /**
         * Each subfolder of Android meta data folder is a language folder
         * Find them and pick the txt files which represent a meta data field
         */
        val localResources = ANDROID_METADATA_FOLDER.listFiles()?.filter { it.isDirectory }?.map {
            GooglePlayIntegration.ListingResource(
                    it.name,
                    getStringFromFile(File(it, "title.txt")),
                    getStringFromFile(File(it, "short_description.txt")),
                    getStringFromFile(File(it, "full_description.txt")),
                    getStringFromFile(File(it, "video.txt"))
            )
        } ?: listOf()
        /**
         * Get Play Store listings
         */
        val playStoreResources = GooglePlayIntegration.getListings()
        /**
         * Elements added or updated locally
         */
        val updateList = localResources.filter { resource ->
            playStoreResources.find { it == resource } == null
        }
        /**
         * Elements removed locally
         */
        val removeList = playStoreResources.filter { resource ->
            localResources.find { it.language == resource.language } == null
        }

        if (updateList.isEmpty() && removeList.isEmpty()) {
            println("Nothing to update.")
            return
        }
        if (updateList.isNotEmpty()) {
            println("You're about to UPDATE languages : ${updateList.joinToString(separator = ", ") { it.language }}")
        }
        if (removeList.isNotEmpty()) {
            println("You're about to REMOVE languages : ${removeList.joinToString(separator = ", ") { it.language }}")
        }
        println("Are you sure you want to proceed? [yes/no]?")
        loop@ while (true) {
            when (readLine()) {
                "yes" -> break@loop
                "no" -> return
            }
        }

        if (updateList.isNotEmpty()) {
            GooglePlayIntegration.uploadListing(resources = updateList)
        }
        if(removeList.isNotEmpty()) {
            GooglePlayIntegration.removeListings(languagesList= removeList.map { it.language })
        }
        println("Done!")
    }

    private fun convertStreamToString(inputStream: InputStream?): String {
        val reader = BufferedReader(InputStreamReader(inputStream))
        val sb = StringBuilder()
        var line: String?
        while (reader.readLine().also { line = it } != null) {
            sb.append(line).append("\n")
        }
        reader.close()
        return sb.toString().trim()
    }

    private fun getStringFromFile(file: File): String? {
        val fin = FileInputStream(file)
        return try {
            convertStreamToString(fin)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            fin.close()
        }
    }
}