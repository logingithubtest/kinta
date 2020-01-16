package com.dailymotion.kinta.workflows

import com.dailymotion.kinta.Workflows
import com.dailymotion.kinta.workflows.builtin.*

class BuiltInWorkflows : Workflows {

    override fun all() = listOf(
            cleanLocal,
            cleanGithubBranches,
            PullPlayStorePreviews,
            PullPlayStoreListings,
            PushPlayStoreListings
    )
}

