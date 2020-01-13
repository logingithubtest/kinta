package com.dailymotion.kinta.integration.github

import com.apollographql.apollo.ApolloClient
import com.apollographql.apollo.coroutines.toDeferred
import com.dailymotion.kinta.*
import com.dailymotion.kinta.integration.github.internal.GithubOauthClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.eclipse.jgit.api.Git
import org.eclipse.jgit.transport.URIish

object GithubIntegration {
    fun openPullRequest(token: String? = null,
                        owner: String? = null,
                        repo: String? = null,
                        head: String? = null,
                        base: String? = null,
                        title: String? = null
    ) {
        val token_ = token ?: retrieveToken()
        val owner_ = owner ?: repository().owner
        val repo_ = repo ?: repository().name

        val head_ = head ?: Project.repository.branch!!
        val base_ = base ?: "master"
        val title_ = title ?: base_

        check(head_ != base_) {
            "You cannot make a pull request with the same head and base ($head_)"
        }

        val jsonObject = JsonObject(
                mapOf(
                        "title" to JsonPrimitive(title_),
                        "head" to JsonPrimitive(head_),
                        "base" to JsonPrimitive(base_)
                )
        )

        val body = RequestBody.create(MediaType.parse("application/json"), Json.Companion.nonstrict.toJson(jsonObject).toString())

        val request = Request.Builder()
                .url("https://api.github.com/repos/$owner_/$repo_/pulls")
                .post(body)
                .build()

        Log.d("creating pull request to merge $head_ into $base_")
        val response = httpClient(token_).newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception(response.body()?.string() ?: "")
        }

        response.body()?.charStream()?.let {
            try {
                val htmlUrl = Json.nonstrict.parseJson(it.readText()).jsonObject.getPrimitive("html_url").content
                Log.d("-> $htmlUrl")
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    /**
     * @param name: the name of the branch
     * @param pullRequests: the pull requests associated with a given branch
     */
    data class BranchInfo(
            val name: String,
            val pullRequests: List<PullRequestInfo>
    )

    data class PullRequestInfo(
            val number: Int,
            val merged: Boolean,
            val closed: Boolean
    )

    /**
     * For each branch in branches, get some info about the pull requests associated to it
     */
    fun getBranchesInfo(
            token: String? = null,
            owner: String? = null,
            repo: String? = null,
            branches: List<String>): List<BranchInfo> {
        val token_ = token ?: retrieveToken()
        val owner_ = owner ?: repository().owner
        val repo_ = repo ?: repository().name

        return runBlocking {
            branches.map {
                val pullRequests = apolloClient(token_)
                        .query(GetPullRequestByName(owner_, repo_, it))
                        .toDeferred()
                        .await()
                        .data()
                        ?.repository
                        ?.pullRequests
                        ?.nodes
                        ?.filterNotNull() ?: emptyList()

                BranchInfo(
                        name = it,
                        pullRequests = pullRequests.map {
                            PullRequestInfo(it.number, it.merged, it.closed)
                        }
                )
            }
        }
    }

    fun deleteRef(
            token: String? = null,
            owner: String? = null,
            repo: String? = null,
            ref: String
    ) {
        val token_ = token ?: retrieveToken()
        val owner_ = owner ?: repository().owner
        val repo_ = repo ?: repository().name

        println("deleting $ref...")

        val request = Request.Builder()
                .url("https://api.github.com/repos/$owner_/$repo_/git/refs/heads/$ref")
                .delete()
                .build()

        val response = httpClient(token_).newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception(response.body()?.string() ?: "")
        }

    }

    fun deleteClosedOrMergedBranches(
            token: String? = null,
            owner: String? = null,
            repo: String? = null,
            toExcludeFilter: ((String) -> Boolean) = { _ -> false }
    ): List<String> {
        val token_ = token ?: retrieveToken()
        val owner_ = owner ?: repository().owner
        val repo_ = repo ?: repository().name

        val allRefs = runBlocking {
            val query = GetRefs(owner_, repo_)

            apolloClient(token_).query(query)
                    .toDeferred()
                    .await()
                    .data()
                    ?.repository
                    ?.refs
                    ?.edges
                    ?.map { it?.node }
                    ?.filterNotNull() ?: emptyList()

        }

        /**
         * Get the list of all open pullRequests and their base branch name
         * We don't want to delete these
         */
        val allBaseBrancheNames = allRefs
                .flatMap { it.associatedPullRequests.nodes!!.filterNotNull() }
                .filter { !it.merged && !it.closed }
                .mapNotNull { it.baseRef?.name }


        val deletedBranchNames = allRefs.filter {
            val name = it.name

            if (name == "master") {
                // never delete master
                return@filter false
            }

            val associatedPullRequests = it.associatedPullRequests.nodes?.filterNotNull()

            if(toExcludeFilter(name)){
                // The user has exclude this ref from the delete list
                return@filter false
            }

            if (associatedPullRequests.isNullOrEmpty()) {
                // This ref has no associated pull request yet, don't delete it
                return@filter false
            }

            if (associatedPullRequests.count { !it.merged && !it.closed } > 0) {
                // There is one open pull request on this ref, don't delete it
                return@filter false
            }

            if (allBaseBrancheNames.contains(name)) {
                // This ref is used as a base for another one, don't delete it
                return@filter false
            }

            // fallthrough, delete this branch
            true
        }.map {
            it.name
        }

        deletedBranchNames.forEach {
            deleteRef(token, owner, repo, it)
        }

        return deletedBranchNames
    }

    fun apolloClient(token: String): ApolloClient {
        return ApolloClient.builder()
                .serverUrl("https://api.github.com/graphql")
                .okHttpClient(httpClient(token))
                .build()
    }


    private fun httpClient(token: String): OkHttpClient {
        return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    chain.proceed(chain.request()
                            .newBuilder()
                            .addHeader("Authorization", "Bearer ${token}")
                            .build()
                    )
                }
                .build()
    }

    fun retrieveToken(): String {
        return KintaEnv.get(KintaEnv.GITHUB_TOKEN)
                ?: GithubOauthClient.getToken()
    }

    data class Repository(val owner: String, val name: String)

    fun repository(): Repository {
        val git = Git(Project.repository)

        /*
         * I did not find another way to retrieve the github repository details than to parse
         * the remote url
         */
        val remoteConfigList = git.remoteList().call()

        val uri = remoteConfigList.filter { it.name == "origin" }.first().urIs[0]

        return repoDetails(uri)
    }

    fun repoDetails(uriIsh: URIish): Repository {
        if (uriIsh.host != "github.com") {
            throw Exception("this script only works with github.com")
        }

        // https://github.com/owner/repo.git has a leading '/'
        // git@github.com:owner/repo.git has not
        val s = uriIsh.path.trim('/').split("/")

        val repoName = s[1].removeSuffix(".git")

        return Repository(s[0], repoName)
    }
}