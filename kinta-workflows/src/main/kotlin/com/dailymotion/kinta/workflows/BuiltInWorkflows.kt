package com.dailymotion.kinta.workflows

import com.dailymotion.kinta.Workflows
import com.dailymotion.kinta.workflows.builtin.PullPlayStoreListings
import com.dailymotion.kinta.workflows.builtin.RetrievePlayStorePreviews
import com.dailymotion.kinta.workflows.builtin.cleanGithubBranches
import com.dailymotion.kinta.workflows.builtin.cleanLocal

class BuiltInWorkflows : Workflows {

    override fun all() = listOf(
            cleanLocal,
            cleanGithubBranches,
            RetrievePlayStorePreviews,
            PullPlayStoreListings
    )
}

