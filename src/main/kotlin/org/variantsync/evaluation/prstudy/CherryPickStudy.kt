package org.variantsync.evaluation.prstudy

import org.eclipse.jgit.api.Git
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.EvalConfig
import org.variantsync.evaluation.determineSampleSize
import org.variantsync.evaluation.waitForShutdown
import org.variantsync.functjonal.iteration.ClusteredIterator
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.IOException
import java.io.UncheckedIOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes
import java.util.*
import java.util.concurrent.Executors
import java.util.stream.Collectors
import kotlin.math.ceil
import kotlin.system.exitProcess

// TODO: Implement different handling of parallel tasks. Each thread should use its own workspace, but each task only processes a single cherry pick
// TODO: Implement timeout?
class CherryPickStudy(
    config: EvalConfig,
    dataset: CherryDataset,
) {
    // The study tasks that are to be executed in parallel
    private val evalTasks: MutableList<CherryPickEvalTask>
    private val numThreads: Int

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
            dataset.cherryPicks = dataset.cherryPicks.shuffled().subList(0, sampleSize)
        }

        evalTasks = ArrayList()
        val clusterSize = ceil(dataset.cherryPicks.size.toDouble() / numThreads).toInt()
        val clusterIterator = ClusteredIterator(dataset.cherryPicks.iterator(), clusterSize)
        while (clusterIterator.hasNext()) {
            evalTasks.add(
                CherryPickEvalTask(
                    config,
                    dataset.datasetName,
                    repoPath,
                    clusterIterator.next()
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
        val futures = evalTasks.stream()
            .map { runnable: CherryPickEvalTask -> threadPool.submit(runnable) }
            .collect(Collectors.toList())
        waitForShutdown(threadPool, futures)
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
    if (repoId !is String) {
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

    return Optional.of(CherryDataset(pathToYaml.fileName.toString(), repoId, cherryPicks))
}
