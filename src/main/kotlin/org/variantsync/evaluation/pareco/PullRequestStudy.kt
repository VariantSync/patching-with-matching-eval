package org.variantsync.evaluation.pareco

import org.eclipse.jgit.api.Git
import org.eclipse.jgit.lib.ObjectId
import org.eclipse.jgit.lib.Repository
import org.eclipse.jgit.revwalk.RevCommit
import org.eclipse.jgit.revwalk.RevSort
import org.eclipse.jgit.revwalk.RevWalk
import org.tinylog.kotlin.Logger
import org.variantsync.evaluation.EvalConfig
import org.variantsync.functjonal.iteration.ClusteredIterator
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
import java.util.concurrent.TimeUnit
import java.util.stream.Collectors
import kotlin.math.ceil
import kotlin.system.exitProcess

class PullRequestStudy(
    config: EvalConfig,
    dataset: PRDataset,
) {
    // The study tasks that are to be executed in parallel
    private val paReCoEvalTasks: MutableList<PaReCoEvalTask>
    private val numThreads: Int

    /**
     * Initialize the study from the given configuration
     */
    init {
        if (!Files.exists(config.EXPERIMENT_DIR_RESULTS())) {
            Files.createDirectories(config.EXPERIMENT_DIR_RESULTS())
        }
        this.numThreads = config.EXPERIMENT_THREAD_COUNT()

        val repoPath: Path = cloneGitHubRepo(config, dataset.targetRepoId)

        paReCoEvalTasks = ArrayList()
        val clusterSize = ceil(dataset.pullRequests.size.toDouble() / numThreads).toInt()
        val clusterIterator = ClusteredIterator(dataset.pullRequests.iterator(), clusterSize)
        while (clusterIterator.hasNext()) {
            paReCoEvalTasks.add(
                PaReCoEvalTask(
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
        val futures = paReCoEvalTasks.stream()
            .map { runnable: PaReCoEvalTask -> threadPool.submit(runnable) }
            .collect(Collectors.toList())
        threadPool.shutdown()
        for (future in futures) {
            try {
                future.get()
            } catch (e: Throwable) {
                Logger.error("Failed to finish task!")
                Logger.error(e)
                e.printStackTrace()
            }
        }
        if (!threadPool.awaitTermination(7, TimeUnit.DAYS)) {
            Logger.error("Thread pool timeout.")
        }

        Logger.info("All done.")
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
    val datasets: List<PRDataset> = try {
        loadPRDatasets(config.EXPERIMENT_DATASETS())
    } catch (e: IOException) {
        Logger.error(
            "Was not able to load the yaml datasets from '"
                    + config.EXPERIMENT_DATASETS() + "'"
        )
        throw UncheckedIOException(e)
    }

    for (dataset in datasets) {
        val datasetSize = dataset.pullRequests.size
        Logger.info("using next dataset ${dataset.datasetName} with $datasetSize pull requests")
        if (datasetSize > config.EXPERIMENT_DATASET_MAX_SIZE()) {
            Logger.info(
                "Skipping %s with %s pull requests because it exceeds the maximum number of pull requests (%d) set in the configuration.".format(
                    dataset.datasetName,
                    datasetSize,
                    config.EXPERIMENT_DATASET_MAX_SIZE()
                )
            )
            continue
        }
        val study = PullRequestStudy(config, dataset)
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

fun loadPRDatasets(datasetsDir: Path): List<PRDataset> {
    val datasets = ArrayList<PRDataset>()
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

fun loadDataset(pathToYaml: Path): Optional<PRDataset> {
    val parseException = IllegalArgumentException("the yaml file under $pathToYaml cannot be parsed into a pr dataset")

    val yaml = Yaml()
    val fileEntries = yaml.loadAll(Files.readString(pathToYaml)).iterator().next()

    if (fileEntries !is List<*>) {
        throw parseException
    }

    val destination = fileEntries[0]
    val source = fileEntries[1]
    if (source !is String || destination !is String) {
        throw parseException
    }

    val pullRequests = ArrayList<PullRequest>()
    val prEntries = fileEntries[2]
    if (prEntries !is HashMap<*, *>) {
        throw parseException
    }
    for (pr in prEntries.entries) {
        val prId = pr.key
        if (prId !is String) {
            throw parseException
        }
        if (prId == "nan") {
            // No valid dataset found
            return Optional.empty()
        }

        val prFields = pr.value
        if (prFields !is Map<*, *>) {
            throw parseException
        }

        // The names 'source' and 'target' are switched deliberately. In the PaReCo dataset 'source' refers to the
        // original repository for a fork, and 'target' refers to the fork repository. Pull requests are always considered
        // from the fork to the original (i.e., from 'target' to 'source'). The 'source' and 'target' terms which we use
        // refer to the source and target of a change propagation.
        val sourceVariantV0 = prFields["target_base_id"]
        val sourceVariantV1 = prFields["target_pull_id"]
        val targetVariantV0 = prFields["source_base_id"]

        if (sourceVariantV0 !is String || sourceVariantV1 !is String || targetVariantV0 !is String) {
            return Optional.empty()
        }

        pullRequests.add(PullRequest(prId.toInt(), sourceVariantV0, sourceVariantV1, targetVariantV0))
    }

    return Optional.of(PRDataset(pathToYaml.fileName.toString(), source, destination, pullRequests))
}
