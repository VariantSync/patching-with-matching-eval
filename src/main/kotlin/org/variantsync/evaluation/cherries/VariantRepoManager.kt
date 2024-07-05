package org.variantsync.evaluation.cherries

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.baseline.shell.*
import org.variantsync.evaluation.syncstudy.panic
import java.nio.file.Path

class VariantRepoManager(
    private val sourceVariantV0: Path, private val sourceVariantV1: Path,
    private val targetVariantV0: Path, private val targetVariantV1: Path,
    private val githubRepoPath: Path
) {
    var lastCherry: CherryPick? = null

    var sourceV0Git: Git? = null
    var sourceV1Git: Git? = null
    var targetV0Git: Git? = null
    var targetV1Git: Git? = null
    val shellInTarget: ShellExecutor = ShellExecutor(Logger::debug, Logger::debug, targetVariantV0)

    fun open() {
        sourceV0Git = Git.open(sourceVariantV0.toFile())
        sourceV1Git = Git.open(sourceVariantV1.toFile())
        targetV0Git = Git.open(targetVariantV0.toFile())
        targetV1Git = Git.open(targetVariantV1.toFile())
    }

    fun prepareCherryPick(cherryPick: CherryPick): Boolean {
        lastCherry = cherryPick
        Logger.debug("Checking out commits of next cherry pick")
        try {
            this.sourceV0Git!!.checkout().setName(cherryPick.cherryParentCommit).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V0 (source before changes)")
            Logger.info(e.message)
            return false
        }
        try {
            this.sourceV1Git!!.checkout().setName(cherryPick.cherryCommit).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find source variant V1 (source after changes)")
            Logger.info(e.message)
            return false
        }
        try {
            resetTargetVariant()
            this.targetV0Git!!.checkout().setName(cherryPick.targetCommit).setForced(true).call()
        } catch (e: JGitInternalException) {
            Logger.info("Was not able to find target variant V0 (target before change propagation)")
            Logger.info(e.message)
            return false
        }

        try {
            this.targetV1Git!!.checkout().setName(cherryPick.expectedResultCommit).setForced(true)
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
            shellInTarget.execute(GitRestoreCommand(".").staged())
            shellInTarget.execute(GitRestoreCommand("."))
            shellInTarget.execute(GitCleanCommand(".").forced())
            shellInTarget.execute(GitAddCommand("."))
            
            val status = this.targetV0Git!!.status().call()
            if (!status.isClean || status.hasUncommittedChanges()) {
                Logger.debug("Normal clean failed. Running emergency cleanup")
                this.targetV0Git!!.add().addFilepattern(".").call()
                this.targetV0Git!!.checkout().setForced(true).setName(this.lastCherry!!.targetCommit).call()
            }
        } catch (e: Exception) {
            Logger.debug("First cleanup phase failed.", e)
            try {
                shellInTarget.execute(RmCommand(targetVariantV0).recursive())
                    .expect("Was not able to remove target variant V0.")
                shellInTarget.execute(CpCommand(githubRepoPath, targetVariantV0).recursive())
                    .expect("Was not able to copy target variant V0.")
            } catch (e2: Exception) {
                panic("Was not able to clean target.", e2)
            }
        }
    }

    fun close() {
        this.sourceV0Git!!.close()
        this.sourceV1Git!!.close()
        this.targetV0Git!!.close()
        this.targetV1Git!!.close()
    }
}