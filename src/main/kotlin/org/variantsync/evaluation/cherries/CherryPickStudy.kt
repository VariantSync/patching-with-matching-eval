package org.variantsync.evaluation.cherries

import org.apache.commons.io.FileUtils
import org.eclipse.jgit.api.Git
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.IDProvider
import org.variantsync.evaluation.baseline.shell.CpCommand
import org.variantsync.evaluation.baseline.shell.RmCommand
import org.variantsync.evaluation.determineSampleSize
import org.variantsync.evaluation.waitForShutdown
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.ByteBuffer
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.security.SecureRandom
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.stream.Collectors
import kotlin.system.exitProcess

class CherryPickStudy(
    config: EvalConfig,
    dataset: CherryDataset,
) {
    // The study tasks that are to be executed in parallel
    private val evalTasks: MutableList<CherryPickEvalTask>
    private val numThreads: Int
    private val idProvider: IDProvider
    private val repoManagers: Map<CherryEvalOperations, VariantRepoManager>
    private val availableOperations: BlockingQueue<CherryEvalOperations>

    /**
     * Initialize the study from the given configuration
     */
    init {
        if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
            Files.createDirectories(config.EXPERIMENT_DIR_RESULTS())
        }
        this.numThreads = config.EXPERIMENT_THREAD_COUNT()

        val repoPath: Path = cloneGitHubRepo(config, dataset.repositoryId)

        if (config.EXPERIMENT_ENABLE_SAMPLING()) {
            val sampleSize = determineSampleSize(config, dataset.cherryPicks.size)
            Logger.info("Considering a representative sample of $sampleSize cherry picks.")
            val seed: ByteArray = ByteBuffer.allocate(java.lang.Long.BYTES).putLong(config.SEED()).array()
            dataset.cherryPicks =
                dataset.cherryPicks.shuffled(SecureRandom(seed)).subList(0, sampleSize)
        }

        this.evalTasks = ArrayList()
        this.availableOperations = LinkedBlockingQueue(numThreads)
        this.repoManagers = HashMap<CherryEvalOperations, VariantRepoManager>()

        for (i in 1..numThreads) {
            // Add one operations instance for each thread; each instance defines its own working directory
            val operations = CherryEvalOperations(config.EXPERIMENT_DIR_MAIN())
            // Clean old variant files
            cleanVariantDirectories(operations)
            // Copy the source and target variant to the respective variant directories
            prepareVariantDirectories(operations, repoPath)
            availableOperations.add(operations)
            val repoManager = VariantRepoManager(operations, repoPath)
            repoManagers[operations] = repoManager
        }

        idProvider = IDProvider(config.EXPERIMENT_START_ID())
        for (cherryPick in dataset.cherryPicks) {
            val runID = idProvider.next()
            if (runID < idProvider.start) {
                Logger.info("Skipped commit $runID")
                continue
            }
            evalTasks.add(
                CherryPickEvalTask(
                    config,
                    dataset.datasetName,
                    cherryPick,
                    availableOperations,
                    repoManagers,
                    runID
                )
            )
        }
    }

    private fun cloneGitHubRepo(config: EvalConfig, repoId: String): Path {
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

    /**
     * Execute the study.
     */
    fun run() {
        val threadPool = Executors.newFixedThreadPool(numThreads)
        Logger.info("Starting diffing and patching for cherry picks...")

        val futures = evalTasks.stream()
            .map { runnable: CherryPickEvalTask -> threadPool.submit(runnable) }
            .collect(Collectors.toList())

        waitForShutdown(threadPool, futures)

        // Finally, close all repo managers
        for (repoManager in this.repoManagers.values) {
            repoManager.close()
        }

        // And delete all workdirs
        for (operations in this.availableOperations) {
            FileUtils.deleteDirectory(operations.workDir.toFile())
        }
    }
}

fun main(args: Array<String>) {
    if (args.isEmpty()) {
        System.err.println(
            "The first argument should provide the path to the configuration file that is to be used"
        )
    }
    val config = EvalConfig(File(args[0]))
    Logger.info("Starting experiment initialization.")
    val datasets: List<CherryDataset> = try {
        loadPRDatasets(config.EXPERIMENT_DATASETS())
    } catch (e: IOException) {
        Logger.error(
            "Was not able to load the yaml datasets from '"
                    + config.EXPERIMENT_DATASETS() + "'"
        )
        throw UncheckedIOException(e)
    }

    for (dataset in datasets) {
        val datasetSize = dataset.cherryPicks.size
        Logger.info("using next dataset ${dataset.datasetName} with $datasetSize cherry picks")
        if (datasetSize > config.EXPERIMENT_DATASET_MAX_SIZE()) {
            Logger.info(
                "Skipping %s with %s cherry picks because it exceeds the maximum number of cherry picks (%d) set in the configuration.".format(
                    dataset.datasetName,
                    datasetSize,
                    config.EXPERIMENT_DATASET_MAX_SIZE()
                )
            )
            continue
        }
        val study = CherryPickStudy(config, dataset)
        try {
            study.run()
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.error(e)
            exitProcess(1)
        }
    }
    exitProcess(0)
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

fun loadPRDatasets(datasetsDir: Path): List<CherryDataset> {
    val datasets = ArrayList<CherryDataset>()
    for (yamlFile in getYamlFiles(datasetsDir)) {
        val dataset = loadDataset(yamlFile)
        if (dataset.isPresent) {
            datasets.add(dataset.get())
        }
    }
    return datasets
}

fun getYamlFiles(directoryPath: Path): List<Path> {
    val yamlFileVisitor = YamlFileVisitor()

    Files.walkFileTree(directoryPath, yamlFileVisitor)

    return yamlFileVisitor.yamlFiles
}

fun loadDataset(pathToYaml: Path): Optional<CherryDataset> {
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

        cherryPicks.add(CherryPick(id, cherryId, cherryParentId, targetId, expectedResultId))
        id++
    }

    return Optional.of(CherryDataset(pathToYaml.fileName.toString(), repoName, cherryPicks))
}

private fun prepareVariantDirectories(operations: CherryEvalOperations, gitHubRepoPath: Path) {
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

private fun cleanVariantDirectories(operations: CherryEvalOperations) {
    Logger.debug("Cleaning old variant files.")
    if (Files.exists(operations.sourceVariantV0)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV0).recursive())
            .expect("Was not able to remove source variant V0.")
    }
    if (Files.exists(operations.sourceVariantV1)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive())
            .expect("Was not able to remove source variant V1.")
    }
    if (Files.exists(operations.targetVariantV0)) {
        operations.shell.execute(RmCommand(operations.targetVariantV0).recursive())
            .expect("Was not able to remove target variant V0.")
    }
    if (Files.exists(operations.targetVariantV1)) {
        operations.shell.execute(RmCommand(operations.sourceVariantV1).recursive())
            .expect("Was not able to remove target variant V1.")
    }
}
