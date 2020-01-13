package com.dailymotion.kinta.workflows.builtin

import com.dailymotion.kinta.integration.github.GithubIntegration
import com.github.ajalt.clikt.core.CliktCommand

val cleanGithubBranches = object : CliktCommand(name = "cleanGithubBranches", help = """
    Clean up branches in your github repository. This is useful for workflows where 
        - Remove remote tracking branches that have been deleted in the remote with git fetch -p
        - Remove all the local branches that only have closed or merged pull requests.
    This will force delete the local branches, you can restore deleted branches from your git reflog
    but that process is a bit more involved so double check before you call this workflow.
    This only works for repositories hosted on github.
""".trimIndent()) {
    override fun run() {
        GithubIntegration.deleteClosedOrMergedBranches()
    }
}