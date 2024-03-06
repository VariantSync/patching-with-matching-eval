package org.variantsync.evaluation.pareco

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.syncstudy.panic
import java.nio.file.Path

class VariantRepoManager(private val operations: PaReCoOperations) {
    private val sourceV0: Git = Git.open(operations.sourceVariantV0.toFile())
    private val sourceV1: Git = Git.open(operations.sourceVariantV1.toFile())
    private val targetV0: Git = Git.open(operations.targetVariantV0.toFile())
    private val targetV1: Git = Git.open(operations.targetVariantV1.toFile())

    fun preparePullRequest(pr: PullRequest) {
        Logger.debug("Checking out commits of next pull request")
        this.sourceV0.checkout().setName(pr.sourceV0).setForced(true).call()
        this.sourceV1.checkout().setName(pr.sourceV1).setForced(true).call()
        this.targetV0.checkout().setName(pr.targetV0).setForced(true).call()
        this.targetV1.checkout().setName(findExpectedResultCommit(targetV1, pr.sourceV0, pr.sourceV1)).setForced(true)
            .call()
    }

    fun cleanRepoStates() {
        cleanRepo(this.sourceV0, operations.sourceVariantV0)
        cleanRepo(this.sourceV1, operations.sourceVariantV1)
        cleanRepo(this.targetV0, operations.targetVariantV0)
        cleanRepo(this.targetV1, operations.targetVariantV1)
    }

    private fun cleanRepo(repo: Git, path: Path) {
        // Stash all changes and drop the stash. This is a workaround as the JGit API does not support restore.
        Logger.debug("Cleaning state of V0 repo.")
        try {
            repo.stashCreate().setIncludeUntracked(true).call()
            repo.stashDrop().setAll(true).call()
            Logger.debug("Cleaning state of repo.")
        } catch (e: Exception) {
            panic("Was not able to clean repository (${path}).", e)
        }
    }

    private fun findExpectedResultCommit(git: Git, parent1Id: String, parent2Id: String): String? {
        val repository: Repository = git.repository

        val revWalk = RevWalk(repository)
        revWalk.sort(RevSort.TOPO)
        val parent1 = revWalk.parseCommit(ObjectId.fromString(parent1Id))
        val parent2 = revWalk.parseCommit(ObjectId.fromString(parent2Id))
        revWalk.markStart(revWalk.parseCommit(repository.resolve("HEAD")))

        var mergeCommit: RevCommit? = null
        for (commit in revWalk) {
            if (commit.parentCount == 2) {
                if (commit.parents.contains(parent1) && commit.parents.contains(parent2)) {
                    mergeCommit = commit
                    break
                }
            }
        }

        revWalk.close()

        return mergeCommit?.name
    }
}