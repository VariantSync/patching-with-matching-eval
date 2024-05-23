package org.variantsync.evaluation.cherries

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.syncstudy.panic
import java.nio.file.Path

class VariantRepoManager(private val operations: CherryEvalOperations, private val githubRepoPath: Path) {
    private var lastCherry: CherryPick? = null
    private val sourceV0: Git = Git.open(operations.sourceVariantV0.toFile())
    private val sourceV1: Git = Git.open(operations.sourceVariantV1.toFile())
    private val targetV0: Git = Git.open(operations.targetVariantV0.toFile())
    private val targetV1: Git = Git.open(operations.targetVariantV1.toFile())

    fun prepareCherryPick(cherryPick: CherryPick): Boolean {
        lastCherry = cherryPick
        Logger.debug("Checking out commits of next cherry pick")
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
            resetTargetVariant()
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

    fun resetTargetVariant() {
        try {
            Logger.debug("Cleaning state of target.")
            this.targetV0.checkout().setForced(true).setName(this.lastCherry!!.targetCommit).call()
            this.targetV0.clean().setForce(true).call()
        } catch (e: Exception) {
            Logger.debug("Normal clean failed. Running emergency cleanup")
            try {
                operations.shell.execute(RmCommand(operations.targetVariantV0).recursive())
                    .expect("Was not able to remove target variant V0.")
                operations.shell.execute(CpCommand(githubRepoPath, operations.targetVariantV0).recursive())
                    .expect("Was not able to copy target variant V0.")
            } catch (e2: Exception) {
                panic("Was not able to clean target.", e2)
            }
        }
    }

    fun close() {
        this.sourceV0.close()
        this.sourceV1.close()
        this.targetV0.close()
        this.targetV1.close()
    }
}