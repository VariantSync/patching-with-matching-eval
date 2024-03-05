package org.variantsync.evaluation.pareco

import org.eclipse.jgit.api.Git
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.vevos.panic
import java.nio.file.Path

class VariantRepoManager(private val operations: PaReCoOperations) {
    private val sourceV0: Git = Git.open(operations.sourceVariantV0.toFile())
    private val sourceV1: Git = Git.open(operations.sourceVariantV1.toFile())
    private val targetV0: Git = Git.open(operations.targetVariantV0.toFile())
    private val targetV1: Git = Git.open(operations.targetVariantV1.toFile())

    fun preparePullRequest(pr: PullRequest) {
        Logger.debug("Checking out commits of next pull request")
        this.sourceV0.checkout().setStartPoint(pr.sourceV0).call()
        this.sourceV1.checkout().setStartPoint(pr.sourceV1).call()
        this.targetV0.checkout().setStartPoint(pr.targetV0).call()
        this.targetV1.checkout().setStartPoint(pr.targetV1).call()
    }

    fun cleanRepoStates() {
        cleanRepo(this.sourceV0, operations.sourceVariantV0)
        cleanRepo(this.sourceV1, operations.sourceVariantV1)
        cleanRepo(this.targetV0, operations.targetVariantV0)
        cleanRepo(this.targetV1, operations.targetVariantV1)
    }

    private fun cleanRepo(repo: Git, path: Path) {
        // Stash all changes and drop the stash. This is a workaround as the JGit API does not support restore.
        Logger.warn("Cleaning state of V0 repo.")
        try {
            repo.stashCreate().setIncludeUntracked(true).call()
            repo.stashDrop().setAll(true).call()
            Logger.warn("Cleaning state of repo.")
        } catch (e: Exception) {
            panic("Was not able to clean repository (${path}).", e)
        }
    }
}