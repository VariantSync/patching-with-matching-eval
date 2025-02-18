package org.variantsync.evaluation.execution

import org.eclipse.jgit.api.Git
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.util.diff.components.FileDiff
import org.variantsync.evaluation.util.diff.components.OriginalDiff
import org.variantsync.evaluation.patching.*
import org.variantsync.evaluation.util.diff.DiffParser
import org.variantsync.evaluation.util.shell.CpCommand
import org.variantsync.evaluation.util.shell.DiffCommand
import org.variantsync.evaluation.util.shell.RmCommand
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.IOException
import java.math.RoundingMode
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom
import java.text.DecimalFormat
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

fun filterUnpatchedFiles(originalPatch: OriginalDiff, diffToFilter: OriginalDiff, strip: Int): OriginalDiff {
    val oldFiles = HashSet<Path>()
    val newFiles = HashSet<Path>()
    for (fd in originalPatch.fileDiffs) {
        oldFiles.add(fd.oldFile.subpath(strip, fd.oldFile.nameCount))
        newFiles.add(fd.newFile.subpath(strip, fd.newFile.nameCount))
    }

    val filteredDiffs = ArrayList<FileDiff>()
    for (fileDiff in diffToFilter.fileDiffs) {
        val oldPath = fileDiff.oldFile.subpath(strip, fileDiff.oldFile.nameCount)
        val newPath = fileDiff.newFile.subpath(strip, fileDiff.newFile.nameCount)
        if (oldFiles.contains(oldPath) && newFiles.contains(newPath)) {
            filteredDiffs.add(fileDiff)
        }
    }
    return OriginalDiff(filteredDiffs)
}

fun defaultPatchers(strip: Int): List<Patcher> {
    val patchers = ArrayList<Patcher>()
    patchers.add(GNUPatch("unix_patch", strip))
    // patchers.add(MPatch("pwm_f1", strip, 1))
    patchers.add(MPatch("pwm_f2", strip, 2))
    // patchers.add(GitApply("git_apply", strip))
    patchers.add(GitCP("git_cherry", strip, MergeStrategy.Ours))
    return patchers
}

@Throws(IOException::class)
fun readContentSafely(filePath: Path): List<String> {
    val content = Files.readString(filePath)
    if (content.isEmpty()) {
        return emptyList()
    }
    val lines = content.split("\n")
    return if (lines.last().isEmpty()) {
        lines.subList(0, lines.lastIndex)
    } else {
        lines
    }
}


fun cloneGitHubRepo(config: EvalConfig, repoId: String): Path {
    val repoUri = "https://github.com/$repoId.git"
    val cloneDir = config.EXPERIMENT_DIR_REPOS().resolve(repoId.replace("/", "_"))

    if (Files.exists(cloneDir)) {
        return cloneDir
    }

    Logger.info("cloning $repoUri into $cloneDir")
    Git.cloneRepository().setURI(repoUri).setDirectory(cloneDir.toFile()).call().close()
    Logger.info("done")
    return cloneDir
}


fun printProgress(completed: Int, numCherryPicks: Int, repetition: Int, i: ULong) {
    val completionPercentage = 100 * (completed.toDouble() / numCherryPicks.toDouble())
    val df = DecimalFormat("#.##")
    df.roundingMode = RoundingMode.DOWN
    Logger.info(
        "(Rep.: $repetition, ID: $i) Finished $completed of $numCherryPicks cherry picks (${
            df.format(
                completionPercentage
            )
        }%)\n"
    )
}

fun cloneDatasets(
    allSamples: ArrayList<ArrayList<CherryDataset>>,
    config: EvalConfig
) {
    Logger.info("Looking for datasets that still should be cloned.")
    val datasetsToClone = HashSet<CherryDataset>()
    for (s in allSamples) {
        for (dataset in allSamples[0]) {
            datasetsToClone.add(dataset)
        }
    }

    Logger.info("There are ${datasetsToClone.size} to check.")
    val threadPool = Executors.newFixedThreadPool(config.EXPERIMENT_THREAD_COUNT())
    for (dataset in datasetsToClone) {
        threadPool.submit {
            try {
                cloneGitHubRepo(config, dataset.repositoryId)
            } catch (e: Exception) {
                Thread.sleep(60_000)
                cloneGitHubRepo(config, dataset.repositoryId)
            }
        }
    }
    threadPool.shutdown()
    if (!threadPool.awaitTermination(1, TimeUnit.DAYS)) {
        Logger.error("Thread pool timeout.")
    }
    Logger.info("Cloned all datasets\n")
}

fun createOrLoadSamples(
    config: EvalConfig,
    datasetsPerLanguage: Map<String, MutableList<CherryDataset>>,
    rand: SecureRandom
): ArrayList<ArrayList<CherryDataset>> {
    if (Files.exists(config.EXPERIMENT_SAMPLE_FILE())) {
        Logger.info("Found existing sample file...loading it\n")
        return loadSample(config.EXPERIMENT_SAMPLE_FILE())
    }

    val allSamples = ArrayList<ArrayList<CherryDataset>>()
    for (i in config.EXPERIMENT_REPEATS_START()..config.EXPERIMENT_REPEATS_END()) {
        allSamples.add(ArrayList())
    }
    val langs = ArrayList<String>(datasetsPerLanguage.keys)
    langs.sort()
    for (language in langs) {
        val datasets = datasetsPerLanguage[language]!!
        val sample: List<List<CherryDataset>> = if (config.EXPERIMENT_ENABLE_SAMPLING()) {
            Logger.info("Sampling for next language $language with ${datasets.size} usable repositories")
            sampleCherries(config, datasets, rand)
        } else {
            Logger.info("Loading dataset for $language with ${datasets.size} usable repositories")
            val temp = ArrayList<List<CherryDataset>>()
            for (i in config.EXPERIMENT_REPEATS_START()..config.EXPERIMENT_REPEATS_END()) {
                temp.add(datasets)
            }
            temp
        }
        for (sampleList in sample.withIndex()) {
            allSamples[sampleList.index].addAll(sampleList.value)
        }
    }
    // Shuffle the datasets to consider repos in random order
    for (repetition in config.EXPERIMENT_REPEATS_START()..config.EXPERIMENT_REPEATS_END()) {
        val repetitionIndex = repetition - config.EXPERIMENT_REPEATS_START()
        allSamples[repetitionIndex].shuffle(rand)
    }
    Logger.info("Done.\n")
    saveSample(config.EXPERIMENT_SAMPLE_FILE(), allSamples)
    return allSamples
}

fun sampleCherries(config: EvalConfig, datasets: List<CherryDataset>, rand: SecureRandom): List<List<CherryDataset>> {
    val allCherryPicks = HashMap<CherryPick, CherryDataset>()
    // Collect all cherry picks and associate them with the dataset from which they came
    for (dataset in datasets) {
        for (cherryPick in dataset.cherryPicks) {
            val datasetCopy = CherryDataset(dataset.datasetName, dataset.repositoryId, dataset.language, ArrayList())
            allCherryPicks[cherryPick] = datasetCopy
        }
    }

    val sampleSize = determineSampleSize(config, allCherryPicks.keys.size)
    Logger.info("Considering ${config.EXPERIMENT_REPEATS_COUNT()} representative samples of $sampleSize cherry picks " +
            "for ${allCherryPicks.keys.size} cherry picks in total.")

    val sample: MutableList<List<CherryDataset>> = ArrayList()
    val cherries: List<CherryPick> = ArrayList(allCherryPicks.keys)
    for (repetition in config.EXPERIMENT_REPEATS_START()..config.EXPERIMENT_REPEATS_END()) {
        val cherrySubset = cherries.shuffled(rand).subList(0, sampleSize)
        val remainingDatasets = HashMap<CherryDataset, MutableList<CherryPick>>()
        for (cherry in cherrySubset) {
            val cherryPickList = remainingDatasets.getOrPut(allCherryPicks[cherry]!!){ ArrayList()}
            cherryPickList.add(cherry)
        }

        val datasetSubset: MutableList<CherryDataset> = ArrayList()
        for (dataset in remainingDatasets.keys) {
            dataset.cherryPicks = remainingDatasets[dataset]!!
            datasetSubset.add(dataset)
        }

        val sampledCherries = countCherryPicks(datasetSubset)
        Logger.info("Created sample of $sampledCherries cherry picks for repetition $repetition.")
        if (sampledCherries != sampleSize) {
            Logger.error("Mismatch of expected to actual sample size")
        }

        sample.add(datasetSubset)
    }
    return sample
}

fun countCherryPicks(datasets: List<CherryDataset>): Int {
    var totalNumberOfCherryPicks = 0
    for (dataset in datasets) {
        totalNumberOfCherryPicks += dataset.cherryPicks.size
    }
    return totalNumberOfCherryPicks
}

class YamlFileVisitor : SimpleFileVisitor<Path>() {
    val yamlFiles = mutableListOf<Path>()

    override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
        if (file.toString().endsWith(".yaml")) {
            yamlFiles.add(file)
        }
        return FileVisitResult.CONTINUE
    }

    override fun visitFileFailed(file: Path?, exc: IOException?): FileVisitResult {
        return FileVisitResult.CONTINUE
    }
}

fun loadPRDatasets(config: EvalConfig): Map<String, MutableList<CherryDataset>> {
    val datasetsPerLanguage = HashMap<String, MutableList<CherryDataset>>()
    for (yamlFile in getYamlFiles(config.EXPERIMENT_DATASETS())) {
        val dataset = loadDataset(yamlFile, config.EXPERIMENT_CHERRY_TYPE())
        if (dataset.isPresent) {
            val datasetSize = dataset.get().cherryPicks.size
            if (datasetSize < config.EXPERIMENT_DATASET_MIN_SIZE() || datasetSize > config.EXPERIMENT_DATASET_MAX_SIZE()) {
                Logger.info(
                    ("Skipping %s with %s cherry picks because its size is outside the range (%d, %d) set in " +
                            "the configuration.").format(
                        dataset.get().datasetName,
                        datasetSize,
                        config.EXPERIMENT_DATASET_MIN_SIZE(),
                        config.EXPERIMENT_DATASET_MAX_SIZE(),
                    )
                )
                continue
            }
            if (datasetSize == 0) {
                continue
            }
            val list = datasetsPerLanguage.getOrPut(dataset.get().language) { ArrayList() }
            list.add(dataset.get())
        }
    }
    return datasetsPerLanguage
}

fun getYamlFiles(directoryPath: Path): List<Path> {
    val yamlFileVisitor = YamlFileVisitor()

    Files.walkFileTree(directoryPath, yamlFileVisitor)

    return yamlFileVisitor.yamlFiles
}

enum class CherryType {
    Trivial,
    Complex,
    Both,
}

fun loadDataset(pathToYaml: Path, cherryType: CherryType): Optional<CherryDataset> {
    val parseException = IllegalArgumentException("the yaml file under $pathToYaml cannot be parsed into a pr dataset")

    val loaderOptions = LoaderOptions()
    loaderOptions.codePointLimit = Integer.MAX_VALUE
    val yaml = Yaml(loaderOptions)
    val entries = yaml.loadAll(Files.readString(pathToYaml)).iterator().next()

    if (entries !is List<*>) {
        throw parseException
    }

    val repoId = entries[0]
    if (repoId !is HashMap<*, *>) {
        throw parseException
    }
    val repoName = repoId["repo_name"]
    if (repoName !is String) {
        throw parseException
    }

    val language = repoId["language"]
    if (language !is String) {
        throw parseException
    }

    val cherryPicks = ArrayList<CherryPick>()
    val prEntries = entries[1]
    if (prEntries !is List<*>) {
        throw parseException
    }

    var id = 0
    for (cp in prEntries) {
        if (cp !is HashMap<*, *>) {
            throw parseException
        }
        val isTrivial = cp["is_trivial"] as? Boolean ?: true

        if (cherryType == CherryType.Trivial && !isTrivial) {
            continue
        } else if (cherryType == CherryType.Complex && isTrivial) {
            continue
        }

        val cherryAndTarget = cp["cherry_and_target"]
        if (cherryAndTarget !is HashMap<*, *>) {
            throw parseException
        }

        val cherry = cherryAndTarget["cherry"]
        val target = cherryAndTarget["target"]

        if (cherry !is HashMap<*, *> || target !is HashMap<*, *>) {
            throw parseException
        }

        val cherryParents = cherry["parent_ids"]
        val targetParents = target["parent_ids"]
        if (cherryParents !is List<*> || targetParents !is List<*>) {
            throw parseException
        }
        if (cherryParents.size != 1 || targetParents.size != 1) {
            // We filter all cherry-pick scenarios with merges
            continue
        }


        val cherryId = cherry["id"]
        val cherryParentId = cherryParents[0]
        // The target of a cherry-pick is what we consider the expected result
        // The parent of this target is our actual target to which we want to propagate the changes
        val targetId = targetParents[0]
        val expectedResultId = target["id"]

        if (cherryParentId !is String || expectedResultId !is String || cherryId !is String || targetId !is String) {
            return Optional.empty()
        }

        cherryPicks.add(CherryPick(id, cherryId, cherryParentId, targetId, expectedResultId, isTrivial))
        id++
    }

    return Optional.of(CherryDataset(pathToYaml.fileName.toString(), repoName, language, cherryPicks))
}

fun prepareVariantDirectories(operations: EvalOperations, gitHubRepoPath: Path) {
    Logger.debug("Creating new source and target variant copies.")
    operations.shell.execute(CpCommand(gitHubRepoPath, operations.sourceVariantV0).recursive())
        .expect("Was not able to copy source variant V0.")
    operations.shell.execute(CpCommand(gitHubRepoPath, operations.sourceVariantV1).recursive())
        .expect("Was not able to copy source variant V1.")
    operations.shell.execute(CpCommand(gitHubRepoPath, operations.targetVariantV0).recursive())
        .expect("Was not able to copy target variant V0.")
    operations.shell.execute(CpCommand(gitHubRepoPath, operations.targetVariantV1).recursive())
        .expect("Was not able to copy target variant V1.")
}

fun prepareVariantDirectories(operations: CompositionAnalysisOperations, gitHubRepoPath: Path) {
    Logger.debug("Creating new source and target variant copies.")
    operations.shell.execute(CpCommand(gitHubRepoPath, operations.sourceVariantV0).recursive())
        .expect("Was not able to copy source variant V0.")
    operations.shell.execute(CpCommand(gitHubRepoPath, operations.sourceVariantV1).recursive())
        .expect("Was not able to copy source variant V1.")
}

fun cleanVariantDirectories(operations: EvalOperations) {
    Logger.debug("Cleaning old variant files.")
    if (Files.exists(operations.sourceVariantV0)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV0).recursive().force())
            .expect("Was not able to remove source variant V0.")
    }
    if (Files.exists(operations.sourceVariantV1)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive().force())
            .expect("Was not able to remove source variant V1.")
    }
    if (Files.exists(operations.targetVariantV0)) {
        operations.shell.execute(RmCommand(operations.targetVariantV0).recursive().force())
            .expect("Was not able to remove target variant V0.")
    }
    if (Files.exists(operations.targetVariantV1)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive().force())
            .expect("Was not able to remove target variant V1.")
    }
}

fun cleanVariantDirectories(operations: CompositionAnalysisOperations) {
    Logger.debug("Cleaning old variant files.")
    if (Files.exists(operations.sourceVariantV0)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV0).recursive().force())
            .expect("Was not able to remove source variant V0.")
    }
    if (Files.exists(operations.sourceVariantV1)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive().force())
            .expect("Was not able to remove source variant V1.")
    }
}

fun loadCompletedRuns(config: EvalConfig): HashMap<Int, MutableMap<String, MutableSet<EvaluationRun>>> {
    if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
        return HashMap()
    }
    val completedRuns = loadProcessedRuns(config)

    val map = HashMap<Int, MutableMap<String, MutableSet<EvaluationRun>>>()
    for (run in completedRuns) {
        val datasetName = run.datasetName
        val innerMap = map.getOrPut(run.repetition) { HashMap() }
        val set = innerMap.getOrPut(datasetName) { HashSet() }
        set.add(run)
    }
    return map
}

// Get the difference between two directories using UNIX diff
fun getOriginalDiff(
    operations: EvalOperations,
    v0Path: Path, v1Path: Path
): OriginalDiff {
    return getOriginalDiff(operations, v0Path, v1Path, false)
}

// Get the difference between two directories using UNIX diff
fun getOriginalDiff(
    operations: EvalOperations,
    v0Path: Path, v1Path: Path, ignoreBlanks: Boolean
): OriginalDiff {
    val diffCommand: DiffCommand = DiffCommand.Recommended(
        operations.workDir.relativize(v0Path),
        operations.workDir.relativize(v1Path)
    ).exclude(".*")
    if (ignoreBlanks) {
        diffCommand.ignoreBlankLines()
    }
    val output = operations.shell.execute(diffCommand, operations.workDir)
    //.expect("Was not able to diff variants.")
    return if (output.isSuccess) {
        DiffParser.toOriginalDiff(output.success)
    } else {
        // Assume that the error lines still contain valid diffs, which is usually the case
        DiffParser.toOriginalDiff(output.failure.output)
    }
}