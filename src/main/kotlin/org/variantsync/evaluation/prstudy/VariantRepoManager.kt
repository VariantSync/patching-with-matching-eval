package org.variantsync.evaluation.prstudy

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.syncstudy.panic
import java.nio.file.Path

class VariantRepoManager(private val operations: CherryEvalOperations) {
    private val sourceV0: Git = Git.open(operations.sourceVariantV0.toFile())
    private val sourceV1: Git = Git.open(operations.sourceVariantV1.toFile())
    private val targetV0: Git = Git.open(operations.targetVariantV0.toFile())
    private val targetV1: Git = Git.open(operations.targetVariantV1.toFile())

    fun prepareCherryPick(cherryPick: CherryPick): Boolean {
        Logger.debug("Checking out commits of next pull request")
        try {
            this.sourceV0.checkout().setName(cherryPick.cherryParentCommit).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V0 (source before changes)")
            Logger.info(e.message)
            return false
        }
        try {
            this.sourceV1.checkout().setName(cherryPick.cherryCommit).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V1 (source after changes)")
            Logger.info(e.message)
            return false
        }
        try {
            this.targetV0.checkout().setName(cherryPick.targetCommit).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find target variant V0 (target before change propagation)")
            Logger.info(e.message)
            return false
        }

        try {
            this.targetV1.checkout().setName(cherryPick.expectedResultCommit).setForced(true)
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

    fun resetTargetVariant() {
        cleanRepo(this.targetV0, operations.targetVariantV0)
    }

}