package org.variantsync.evaluation.prstudy

class PRDataset(
    val datasetName: String,
    val sourceRepoId: String,
    val targetRepoId: String,
    val pullRequests: List<PullRequest>
) {
}