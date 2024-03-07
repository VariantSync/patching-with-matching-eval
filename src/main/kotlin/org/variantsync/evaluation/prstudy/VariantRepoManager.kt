package org.variantsync.evaluation.prstudy

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.eclipse.jgit.errors.IncorrectObjectTypeException
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevWalk
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.syncstudy.panic
import java.nio.file.Path

class VariantRepoManager(private val operations: PREvalOperations) {
    private val sourceV0: Git = Git.open(operations.sourceVariantV0.toFile())
    private val sourceV1: Git = Git.open(operations.sourceVariantV1.toFile())
    private val targetV0: Git = Git.open(operations.targetVariantV0.toFile())
    private val targetV1: Git = Git.open(operations.targetVariantV1.toFile())

    fun preparePullRequest(pr: PullRequest): Boolean {
        Logger.debug("Checking out commits of next pull request")
        try {
            this.sourceV0.checkout().setName(pr.sourceV0).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V0 (source before changes)")
            Logger.info(e.message)
            return false
        }
        try {
            this.sourceV1.checkout().setName(pr.sourceV1).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V1 (source after changes)")
            Logger.info(e.message)
            return false
        }
        try {
            this.targetV0.checkout().setName(pr.targetV0).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find target variant V0 (target before change propagation)")
            Logger.info(e.message)
            return false
        }
        val expectedResultCommitId = findExpectedResultCommit(targetV1, pr.sourceV0, pr.sourceV1) ?: return false

        try {
            this.targetV1.checkout().setName(expectedResultCommitId).setForced(true)
                .call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V1 (expected result of change propagation)")
            Logger.info(e.message)
            return false
        }
        return true
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

        // Iterate over all refs in the repository (e.g., branches, tags, etc.)
        for (ref in repository.refDatabase.refs) {
            try {
                revWalk.markStart(revWalk.parseCommit(ref.objectId))
            } catch (e: IncorrectObjectTypeException) {
                Logger.debug("found ref ${ref.objectId} is not a commit")
            }
        }

        val parent1 = revWalk.parseCommit(ObjectId.fromString(parent1Id))
        val parent2 = revWalk.parseCommit(ObjectId.fromString(parent2Id))

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