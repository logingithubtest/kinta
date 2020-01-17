package com.dailymotion.kinta.workflows.builtin.playstore

import com.dailymotion.kinta.integration.googleplay.GooglePlayIntegration
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.option
import java.io.File

/**
 * Push Play Store images for a specific imageType and a specific llanguage if provided
 */
object PushPlayStoreImages : CliktCommand(name = "pushPlayStoreImages", help = "Push listings to the Google Play") {

    private val imageTypeValue by argument(name = "imageType")

    private val languageCode by option("-language", help = "the language code you want to update. Else all supported.")

    private val ANDROID_METADATA_FOLDER = File("kintaSrc/metadata/android/")

    override fun run() {

        val imageType = GooglePlayIntegration.ImageType.values().find { it.value == imageTypeValue }
                ?: throw IllegalArgumentException("Wrong image type. Supported ones are : ${GooglePlayIntegration.ImageType.values().joinToString(separator = ", ") { it.value }}")

        /**
         * Make sure an PullPlayStoreListing has been done once
         */
        if (!ANDROID_METADATA_FOLDER.exists()) {
            println("$ANDROID_METADATA_FOLDER not found.")
            println("Make sure to call 'kinta run pullPlayStoreListings' first or create the files tree manually")
            return
        }

        val uploadMap = mutableMapOf<String, List<File>>()
        val languagesFolderList = ANDROID_METADATA_FOLDER.listFiles()?.filter { it.name == languageCode || languageCode == null }
        languagesFolderList?.forEach { languageFolder ->
            val filesList = mutableListOf<File>()
            File(languageFolder, "images/${imageType.value}").listFiles()?.forEach {
                filesList.add(it)
            }
            uploadMap[languageFolder.name] = filesList
        }

        println("This will DELETE all ${imageType.value} images from PLAY STORE for ${uploadMap.keys.joinToString(separator = ", ")}")
        println("Then, this will upload the ${imageType.value} local resources for languages : ${uploadMap.filter { it.value.isNotEmpty() }.keys.joinToString(separator = ", ")}")
        println("Are you sure you want to proceed? [yes/no]?")
        loop@ while (true) {
            when (readLine()) {
                "yes" -> break@loop
                "no" -> return
            }
        }

        uploadMap.forEach { language, _ ->
            println("Updating images for $language")
            /*GooglePlayIntegration.uploadImages(
                    languageCode = language,
                    imageType = imageType,
                    images = list,
                    overwrite = true
            ) */
        }
        println("Done!")
    }

}