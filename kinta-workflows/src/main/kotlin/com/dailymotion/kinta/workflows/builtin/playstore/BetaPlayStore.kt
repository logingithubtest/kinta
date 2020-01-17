package com.dailymotion.kinta.workflows.builtin.playstore

import com.dailymotion.kinta.integration.googleplay.GooglePlayIntegration
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import java.io.File


object BetaPlayStore : CliktCommand(name = "betaPlayStore", help = "Upload a version to the beta track") {

    private val archiveFile by argument(name = "archiveFile")

    override fun run() {
        GooglePlayIntegration.uploadBeta(archiveFile = File(archiveFile))
    }

}