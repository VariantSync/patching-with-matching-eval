package org.variantsync.evaluation.patching

import org.eclipse.jgit.api.CherryPickResult
import org.variantsync.evaluation.Operations
import org.variantsync.evaluation.baseline.diff.DiffParser
import org.variantsync.evaluation.cherries.CherryEvalOperations
import org.variantsync.vevos.simulation.feature.Variant
import java.nio.file.Files

// TODO: Find a valid evaluation method; Currently, we cannot really detect errors made by cherry pick
class GitCP(private val name: String, private val strip: Int) : Patcher {
    override fun applyPatch(
        operations: Operations,
        sourceVariant: Variant,
        targetVariant: Variant,
        withFiler: Boolean
    ): Rejects {
        val pathToPatchFile = if (withFiler) {
            operations.filteredPatchFile()
        } else {
            operations.patchFile()
        }
        val patch = DiffParser.toOriginalDiff(Files.readAllLines(pathToPatchFile))

        if (operations !is CherryEvalOperations) {
            // If this is not an evaluation of cherry picks, we cannot apply git cherry pick as patcher
            return Rejects(patch.intoChanges(strip))
        }
        val cherry =
            operations.repoManager.targetV0Git!!.repository.resolve(operations.repoManager.lastCherry!!.cherryCommit)

        val cherryPickOperation = operations.repoManager.targetV0Git!!.cherryPick().setNoCommit(true).include(cherry)
        val cherryPickResult = cherryPickOperation.call()
        when (cherryPickResult.status) {
            CherryPickResult.CherryPickStatus.OK -> {
                return Rejects(ArrayList())
            }

            CherryPickResult.CONFLICT, CherryPickResult.CherryPickStatus.FAILED -> {
                val cherryPickFails = cherryPickResult.failingPaths
                val rejects = ArrayList<Change>()
                for (file in cherryPickFails.keys) {
                    for (fileDiff in patch.fileDiffs) {
                        if (fileDiff.oldFile.endsWith(file) || fileDiff.newFile.endsWith(file)) {
                            rejects.addAll(fileDiff.intoChanges(strip))
                        }
                    }
                }
                return Rejects(rejects)
            }

            else -> {
                throw IllegalStateException("new case found")
            }
        }
    }

    override fun name(): String {
        return name
    }

    override fun clean(operations: Operations) {
        super.clean(operations)
        // TODO("Proper cleaning")
    }
}