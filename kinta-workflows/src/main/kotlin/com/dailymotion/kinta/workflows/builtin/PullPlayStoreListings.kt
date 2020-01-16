package com.dailymotion.kinta.workflows.builtin

import com.dailymotion.kinta.integration.googleplay.GooglePlayIntegration
import com.github.ajalt.clikt.core.CliktCommand
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.URL

object PullPlayStoreListings : CliktCommand(name = "retrievePlayStoreListings", help = "Pull listings from the Google Play") {

    const val ANDROID_METADATA_FOLDER = "kintaSrc/metadata/android/"

    override fun run() {
        //Get Google Play listings
        println("Getting listings from Google Play. Please wait")
        //val listings = GooglePlayIntegration.getListings()

    }
}