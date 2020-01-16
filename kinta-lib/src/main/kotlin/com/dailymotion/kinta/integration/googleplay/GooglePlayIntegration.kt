@file:Suppress("DEPRECATION")

// for GoogleCredential

package com.dailymotion.kinta.integration.googleplay

import com.dailymotion.kinta.KintaConfig
import com.dailymotion.kinta.KintaEnv
import com.dailymotion.kinta.Log
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.AbstractInputStreamContent
import com.google.api.client.http.FileContent
import com.google.api.client.http.HttpRequestInitializer
import com.google.api.client.json.jackson2.JacksonFactory
import com.google.api.client.util.PemReader
import com.google.api.client.util.SecurityUtils
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.LocalizedText
import com.google.api.services.androidpublisher.model.Track
import com.google.api.services.androidpublisher.model.TrackRelease
import kotlinx.serialization.json.Json
import org.gradle.internal.impldep.org.apache.commons.io.FilenameUtils
import java.io.File
import java.io.InputStream
import java.io.StringReader
import java.security.spec.PKCS8EncodedKeySpec
import java.util.*

object GooglePlayIntegration {

    private val TRACK_BETA = "beta"
    private val TRACK_PRODUCTION = "production"

    private val HTTP_TRANSPORT = GoogleNetHttpTransport.newTrustedTransport()
    private val JSON_FACTORY = JacksonFactory.getDefaultInstance()

    private fun publisher(googlePlayJson: String?, applicationName: String): AndroidPublisher {
        val googlePlayJson_ = googlePlayJson
                ?: KintaConfig.get(KintaEnv.GOOGLE_PLAY_JSON)
                ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_JSON)

        val json = Json.nonstrict.parseJson(googlePlayJson_).jsonObject
        Log.d(String.format("Authorizing using Service Account: %s", json.getPrimitive("client_email").content))

        val bytes = PemReader.readFirstSectionAndClose(StringReader(json.getPrimitive("private_key").content), "PRIVATE KEY").base64DecodedBytes
        val privKey = SecurityUtils.getRsaKeyFactory().generatePrivate(PKCS8EncodedKeySpec(bytes));

        val credential = GoogleCredential.Builder()
                .setTransport(HTTP_TRANSPORT)
                .setJsonFactory(JSON_FACTORY)
                .setServiceAccountId(json.getPrimitive("client_email").content)
                .setServiceAccountScopes(Collections.singleton(AndroidPublisherScopes.ANDROIDPUBLISHER))
                .setServiceAccountPrivateKey(privKey)
                .build()

        return AndroidPublisher.Builder(HTTP_TRANSPORT, JSON_FACTORY, HttpRequestInitializer {
            it.apply {
                connectTimeout = 100_000
                readTimeout = 100_000
            }
            credential.initialize(it)
        })
                .setApplicationName(applicationName)
                .build()

    }

    private fun makeEdit(publisher: AndroidPublisher, packageName: String, body: (edits: AndroidPublisher.Edits, editId: String) -> Unit) {
        val edits = publisher.edits()

        // Create a new edit to make changes to your listing.
        val editRequest = edits.insert(packageName, null)
        val edit = editRequest.execute()
        val editId = edit.getId()
        Log.d(String.format("Created edit with id: %s", editId))

        body.invoke(edits, editId)

        // Commit changes for edit.
        val commitRequest = edits.commit(packageName, editId)
        val appEdit = commitRequest.execute()
        Log.d(String.format("App edit with id %s has been comitted", appEdit.getId()))
    }

    /**
     * Upload the bundle, set up a beta track release with version @versionCode based on current prod track release configuration @prodVersionCode
     * @param aabFile the file to upload
     * @param versionCode the version code to upload
     * @param moduleProdVersionCode the current prod version for the module
     * @param lastReleaseName the last release name published
     */
    fun uploadBetaBundle(
            googlePlayJson: String? = null,
            packageName: String? = null,
            aabFile: File,
            versionCode: Long,
            moduleProdVersionCode: Long,
            lastReleaseName: String) {

        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)

        val publisher = publisher(googlePlayJson, packageName_)
        makeEdit(publisher, packageName_) { edits, editId ->
            // Upload new bundle to developer console
            val file = FileContent("application/octet-stream", aabFile)
            val uploadRequest = edits.bundles().upload(packageName_, editId, file)
            try {
                Log.d(String.format("Uploading bundle..."))
                uploadRequest.execute()
                Log.d(String.format("Version code %d has been uploaded", versionCode))
            } catch (e: GoogleJsonResponseException) {
                if (e.details.errors.get(0).reason == "apkUpgradeVersionConflict") {
                    // bundle was already uploaded
                } else {
                    throw e
                }
            }

            //Set MultiplesAPKs based on current prod conf
            val getProdVersionsRequest = edits.tracks().get(packageName_, editId, TRACK_PRODUCTION)
            Log.d(String.format("Getting prod conf..."))
            val listVersionCodes = getProdVersionsRequest.execute().releases
                    .find { it.name == lastReleaseName }
                    ?.versionCodes
                    ?: throw IllegalStateException("Can't find release named : $lastReleaseName on Play Console")

            Log.d(String.format("Current prod conf is : $listVersionCodes"))
            listVersionCodes[listVersionCodes.indexOf(moduleProdVersionCode)] = versionCode
            // Assign APK to beta track.
            val updateTrackRequest = edits.tracks().update(packageName_, editId, TRACK_BETA,
                    Track().setReleases(listOf(TrackRelease()
                            .setName(versionCode.toString())
                            .setStatus("completed")
                            .setVersionCodes(listVersionCodes))))
            Log.d("Updating track $TRACK_BETA with trackRelease, versions = $listVersionCodes,...")
            updateTrackRequest.execute()
            Log.d("Track $TRACK_BETA has been updated.")
        }
    }

    /**
     * @param versionCode version code to deploy
     * @param ratio the user fraction
     * @return true if this is the first time we release this version (ie first rollout)
     *
     */
    fun deployVersion(
            googlePlayJson: String? = null,
            packageName: String? = null,
            versionCode: Long,
            ratio: Int
    ): Boolean {
        var isFirstTimeInRollout = false
        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)

        val publisher = publisher(googlePlayJson, packageName_)

        makeEdit(publisher, packageName_) { edits, editId ->
            //Get beta track
            val getBetaVersionsRequest = edits.tracks().get(packageName_, editId, TRACK_BETA)
            val betaTrackResponse = getBetaVersionsRequest.execute()
            val betaTrackRelease = betaTrackResponse.releases.find { it.name == versionCode.toString() }

            //Get prod track
            val getProdVersionsRequest = edits.tracks().get(packageName_, editId, TRACK_PRODUCTION)
            val prodTrackResponse = getProdVersionsRequest.execute()
            val prodTrackRelease = prodTrackResponse.releases.find { it.name == versionCode.toString() }

            isFirstTimeInRollout = prodTrackRelease == null
            Log.d("First time in rollout = $isFirstTimeInRollout, versions = $versionCode")

            if (prodTrackRelease == null && betaTrackRelease == null) {
                throw IllegalStateException("Version already in prod or version not in beta (call 'kinta beta ....' ? )")
            }

            val status = if (ratio == 100) "completed" else "inProgress"
            val versions = (prodTrackRelease ?: betaTrackRelease)?.versionCodes

            val trackRelease = TrackRelease()
                    .setName(versionCode.toString())
                    .setStatus(status)
                    .setVersionCodes(versions)

            if (ratio < 100) {
                trackRelease.userFraction = ratio.toDouble() / 100.toDouble()
            }

            Log.d("Updating track $TRACK_PRODUCTION with trackRelease, versions = $versions, ratio = $ratio...")
            val updateTrackRequest = edits.tracks().update(packageName_, editId, TRACK_PRODUCTION, Track().setReleases(listOf(trackRelease)))
            updateTrackRequest.execute()

            Log.d("Track $TRACK_PRODUCTION has been updated.")
        }
        return isFirstTimeInRollout
    }

    fun uploadWhatsNew(
            googlePlayJson: String? = null,
            packageName: String? = null,
            versionCode: Long,
            whatsNewProvider: (lang: String) -> String?) {
        Log.d("uploading changelog for version $versionCode")

        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)

        val publisher = publisher(googlePlayJson, packageName_)

        makeEdit(publisher, packageName_) { edits, editId ->

            val list = edits.listings().list(packageName_, editId).execute()

            if (list.listings == null) {
                throw Exception("no listings found ?")
                // TODO: create a listing
            }

            val listLocalText = mutableListOf<LocalizedText>()
            for (listing in list.listings) {
                val content = whatsNewProvider(listing.language) ?: continue
                listLocalText.add(LocalizedText().setLanguage(listing.language).setText(content))
                Log.d("Set changelog for ${listing.language} to $content")
            }

            //Update Beta track release
            val betaTrack = edits.tracks().get(packageName_, editId, TRACK_BETA)
            betaTrack.execute().releases.find { it.name == versionCode.toString() }?.let {
                edits.tracks().update(packageName_, editId, TRACK_BETA, Track().setReleases(listOf(it.clone().setReleaseNotes(listLocalText)))).execute()
                Log.d("Releases notes updated for trackRelease $versionCode on track beta")
            }

            //Update Prod track release
            val prodTrack = edits.tracks().get(packageName_, editId, TRACK_PRODUCTION)
            prodTrack.execute().releases.find { it.name == versionCode.toString() }?.let {
                edits.tracks().update(packageName_, editId, TRACK_PRODUCTION, Track().setReleases(listOf(it.clone().setReleaseNotes(listLocalText)))).execute()
                Log.d("Releases notes updated for trackRelease $versionCode on track production")
            }
        }
    }

    data class ListingResource(
            val language: String,
            val title: String?,
            val shortDescription: String?,
            val description: String?,
            val video: String? = null
    )

    fun uploadListing(
            googlePlayJson: String? = null,
            packageName: String? = null,
            resources: List<ListingResource>
    ) {
        Log.d("uploading listing")

        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)

        val publisher = publisher(googlePlayJson, packageName_)

        makeEdit(publisher, packageName_) { edits, editId ->
            val existingListings = edits.listings().list(packageName_, editId).execute().listings

            for (listing in existingListings) {
                val resource = resources.find { it.language == listing.language } ?: continue

                Log.d("Set listing for ${listing.language}")
                Log.d("   title: ${resource.title}")
                Log.d("   shortDescription: ${resource.shortDescription}")
                Log.d("   description: ${resource.description}")

                resource.title?.let { listing.title = it }
                resource.shortDescription?.let { listing.shortDescription = it }
                resource.description?.let { listing.fullDescription = it }
                resource.video?.let { listing.video = it }

                edits.listings()
                        .update(packageName_, editId, listing.language, listing)
                        .execute()
                Log.d("Play store listing updated")
            }
        }
    }

    fun removeListings(
            googlePlayJson: String? = null,
            packageName: String? = null,
            languagesList: List<String>
    ) {
        Log.d("uploading listing")

        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)

        val publisher = publisher(googlePlayJson, packageName_)

        makeEdit(publisher, packageName_) { edits, editId ->
            val existingListingsToRemove = edits.listings().list(packageName_, editId).execute().listings
                    .filter {
                        languagesList.contains(it.language)
                    }

            for (listing in existingListingsToRemove) {
                Log.d("Removing language ${listing.language}")

                edits.listings()
                        .delete(packageName_, editId, listing.language)
                        .execute()
                Log.d("Play store listing updated")
            }
        }
    }

    fun getListings(
            googlePlayJson: String? = null,
            packageName: String? = null
    ): List<ListingResource> {
        println("Getting listings from Google Play. Please wait.")
        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)
        val publisher = publisher(googlePlayJson, packageName_)
        val resources = mutableListOf<ListingResource>()

        makeEdit(publisher, packageName_) { edits, editId ->
            resources.addAll(edits.listings().list(packageName_, editId).execute().listings.map {
                ListingResource(it.language, it.title, it.shortDescription, it.fullDescription, it.video)
            })
        }
        return resources
    }

    fun getImages(
            googlePlayJson: String? = null,
            packageName: String? = null
    ): List<ImageData> {

        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)
        val publisher = publisher(googlePlayJson, packageName_)
        val resources = mutableListOf<ImageData>()

        makeEdit(publisher, packageName_) { edits, editId ->
            //Retrieve supported languages
            edits.listings().list(packageName_, editId).execute().listings.map { it.language }.map { lang ->
                //Cover any imageType
                ImageType.values().map { imageType ->
                    resources.addAll(edits.images()?.list(packageName_, editId, lang, imageType.value)?.execute()?.images?.map {
                        ImageData(FilenameUtils.getName(it.url), it.url, lang, imageType)
                    } ?: listOf())
                }
            }
        }
        return resources
    }

    enum class ImageType(val value: String) {
        IMAGETYPE_FEATURE("featureGraphic"),
        IMAGETYPE_ICON("icon"),
        IMAGETYPE_PHONE("phoneScreenshots"),
        IMAGETYPE_PROMO("promoGraphic"),
        IMAGETYPE_SEVENINCH("sevenInchScreenshots"),
        IMAGETYPE_TENINCH("tenInchScreenshots"),
        IMAGETYPE_TVBANNER("tvBanner"),
        IMAGETYPE_TV("tvScreenshots"),
        IMAGETYPE_WEAR("wearScreenshots")
    }

    class ImageData(
            val id: String,
            val url: String,
            val languageCode: String,
            val imageType: ImageType
    )

    class ImageUploadData(
            val file: File,
            val languageCode: String,
            val imageType: ImageType
    )

    private data class GroupingKey(
            val imageType: ImageType,
            val languageCode: String
    )

    fun uploadImages(
            googlePlayJson: String? = null,
            packageName: String? = null,
            images: List<ImageUploadData>,
            overwrite: Boolean = false
    ) {
        Log.d("uploading image")

        val packageName_ = packageName ?: KintaEnv.getOrFail(KintaEnv.GOOGLE_PLAY_PACKAGE_NAME)

        val publisher = publisher(googlePlayJson, packageName_)


        makeEdit(publisher, packageName_) { edits, editId ->

            images.groupBy { GroupingKey(it.imageType, it.languageCode) }.forEach { entry ->
                if (overwrite) {
                    edits.images().deleteall(packageName, editId, entry.key.languageCode, entry.key.imageType.value).execute()
                }

                entry.value.forEach { uploadData ->

                    val mimetype = when (uploadData.file.extension.toLowerCase()) {
                        "png" -> "image/png"
                        "jpg" -> "image/jpeg"
                        "jpeg" -> "image/jpeg"
                        else -> {
                            throw IllegalArgumentException("Only jpg, jpeg and png extension are allowed (${uploadData.file.absolutePath}")
                        }
                    }
                    edits.images().upload(
                            packageName,
                            editId,
                            uploadData.languageCode,
                            uploadData.imageType.value,
                            object : AbstractInputStreamContent(mimetype) {
                                override fun getLength(): Long {
                                    return uploadData.file.length()
                                }

                                override fun retrySupported(): Boolean {
                                    return true
                                }

                                override fun getInputStream(): InputStream {
                                    return uploadData.file.inputStream()
                                }

                            }).execute()
                }
            }
        }
    }
}
