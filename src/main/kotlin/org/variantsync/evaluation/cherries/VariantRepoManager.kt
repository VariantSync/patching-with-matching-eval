package org.variantsync.evaluation.cherries

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.api.errors.JGitInternalException
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.baseline.shell.*
import org.variantsync.evaluation.syncstudy.panic
import java.nio.file.Path

class VariantRepoManager(
    sourceVariantV0: Path, sourceVariantV1: Path,
    private val targetVariantV0: Path, targetVariantV1: Path,
    private val githubRepoPath: Path
) {
    var lastCherry: CherryPick? = null

    private val shellSourceV0: ShellExecutor = ShellExecutor(Logger::debug, {}, sourceVariantV0)
    private val shellSourceV1: ShellExecutor = ShellExecutor(Logger::debug, {}, sourceVariantV1)
    private val shellTargetV0: ShellExecutor = ShellExecutor(Logger::debug, {}, targetVariantV0)
    private val shellTargetV1: ShellExecutor = ShellExecutor(Logger::debug, {}, targetVariantV1)

    fun prepareCherryPick(cherryPick: CherryPick): Boolean {
        lastCherry = cherryPick
        Logger.debug("Checking out commits of next cherry pick")
        try {
            val command = GitCheckoutCommand.Recommended(cherryPick.cherryParentCommit)
            if (this.shellSourceV0.execute(command).isFailure()) {
                Logger.info("Was not able to find source variant V0 (source before changes)")
                return false
            }
        } catch (e: Exception) {
            Logger.error(e.message)
            return false
        }
        try {
            val command = GitCheckoutCommand.Recommended(cherryPick.cherryCommit)
            if (this.shellSourceV1.execute(command).isFailure) {
                Logger.info("Was not able to find source variant V1 (source after changes)")
                return false
            }
        } catch (e: Exception) {
            Logger.error(e.message)
            return false
        }
        try {
            resetTargetVariant()
            val command = GitCheckoutCommand.Recommended(cherryPick.targetCommit)
           if (this.shellTargetV0.execute(command).isFailure) {
               Logger.info("Was not able to find target variant V0 (target before change propagation)")
               return false
           }
        } catch (e: Exception) {
            Logger.error(e.message)
            return false
        }

        try {
            val command = GitCheckoutCommand.Recommended(cherryPick.expectedResultCommit)
            if (this.shellTargetV1.execute(command).isFailure) {
               Logger.info("Was not able to find source variant V1 (expected result of change propagation)")
               return false
            }
        } catch (e: Exception) {
            Logger.error(e.message)
            return false
        }
        return true
    }

    fun resetTargetVariant() {
        try {
            Logger.debug("Cleaning state of target.")
            var failure = false
            shellTargetV0.execute(GitCherryPickCommand().abort())
            failure = failure || shellTargetV0.execute(GitRestoreCommand(".").staged()).isFailure
            failure = failure || shellTargetV0.execute(GitRestoreCommand(".")).isFailure
            failure = failure || shellTargetV0.execute(GitCleanCommand(".").forced()).isFailure
            failure = failure || shellTargetV0.execute(GitAddCommand(".")).isFailure

            if (failure) {
                Logger.warn("First cleanup phase failed.")
                val tempExecutor = ShellExecutor(Logger::warn, Logger::warn, targetVariantV0)
                tempExecutor.execute(GitCherryPickCommand().abort())
                tempExecutor.execute(RmCommand(targetVariantV0).recursive().force())
                tempExecutor.execute(CpCommand(githubRepoPath, targetVariantV0).recursive())
            }
        } catch (e: Exception) {
            Logger.error(e)
            Logger.error("Was not able to clean target.", e)
        }
    }
}