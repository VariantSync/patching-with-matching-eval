package org.variantsync.evaluation.execution

import java.nio.file.Path
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.util.shell.GitCheckoutCommand
import org.variantsync.evaluation.util.shell.ShellExecutor

class SourceRepoManager(
        sourceVariantV0: Path,
        sourceVariantV1: Path,
        private val githubRepoPath: Path
) {
    var lastCherry: CherryPick? = null

    private val shellSourceV0: ShellExecutor = ShellExecutor(Logger::debug, {}, sourceVariantV0)
    private val shellSourceV1: ShellExecutor = ShellExecutor(Logger::debug, {}, sourceVariantV1)

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
        return true
    }
}

