import de.ovgu.featureide.fm.core.analysis.cnf.formula.FeatureModelFormula;
import de.ovgu.featureide.fm.core.base.IFeatureModel;
import org.variantsync.evaluation.baseline.diff.DiffParser;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.filter.IFileDiffFilter;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;
import org.variantsync.evaluation.baseline.diff.filter.PCBasedFilter;
import org.variantsync.evaluation.baseline.diff.splitting.DefaultContextProvider;
import org.variantsync.evaluation.baseline.diff.splitting.DiffSplitter;
import org.variantsync.evaluation.baseline.diff.splitting.IContextProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.variantsync.vevos.simulation.feature.Variant;
import org.variantsync.vevos.simulation.feature.config.FeatureIDEConfiguration;
import org.variantsync.vevos.simulation.io.ResourceLoader;
import org.variantsync.vevos.simulation.io.kernelhaven.KernelHavenVariantPCIO;
import org.variantsync.vevos.simulation.util.fide.FeatureModelUtils;
import org.variantsync.vevos.simulation.variability.pc.Artefact;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;

import static org.variantsync.vevos.simulation.VEVOS.Initialize;

public class PCBasedFilterTest {

    Path resourceDir = Paths.get("src", "test", "resources", "pc-filter");
    Path splitsDir = resourceDir.resolve("splits");

    static {
        Initialize();
    }

    private void runComparison(Path pathToExpectedResult, IFileDiffFilter fileFilter, ILineFilter lineFilter) throws IOException {
        List<String> diffLines = Files.readAllLines(resourceDir.resolve("diff-A-B.txt"));
        IContextProvider contextProvider = new DefaultContextProvider(resourceDir);
        FineDiff fineDiff = DiffSplitter.split(DiffParser.toOriginalDiff(diffLines), fileFilter, lineFilter, contextProvider);

        List<String> expectedLines = Files.readAllLines(pathToExpectedResult);
        List<String> actualLines = fineDiff.toLines();
        Assertions.assertEquals(expectedLines, actualLines);
    }
    
    private PCBasedFilter getPCBasedFilter(Artefact oldTraces, Artefact newTraces, Variant variant, Path oldVersion, Path newVersion) {
        return new PCBasedFilter(oldTraces, newTraces, variant, oldVersion, newVersion);
    }

    private PCBasedFilter getPCBasedFilter(Variant variant) {
        ResourceLoader<Artefact> artefactLoader = new KernelHavenVariantPCIO();
        Artefact oldTraces = artefactLoader.load(resourceDir.resolve("filters/version-A.variant.csv")).expect("");
        Artefact newTraces = artefactLoader.load(resourceDir.resolve("filters/version-B.variant.csv")).expect("");
        Path oldVersion = Path.of("version-A");
        Path newVersion = Path.of("version-B");
        return getPCBasedFilter(oldTraces, newTraces, variant, oldVersion, newVersion);
    }

    @Test
    public void basicValidation() throws IOException {
        runComparison(resourceDir.resolve("fine-diff-A-B.txt"), null, null);
    }
    
    @Test
    public void removedLineNotInTargetVariant() throws IOException {
        IFeatureModel model = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED");
        final FeatureModelFormula fmf = new FeatureModelFormula(model);
        Variant variant = new Variant("Target", new FeatureIDEConfiguration(fmf, Arrays.asList("B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED")));
        PCBasedFilter pcBasedFilter = getPCBasedFilter(variant);
        runComparison(splitsDir.resolve("removedLineNotInTargetVariant.txt"), pcBasedFilter, pcBasedFilter);
    }

    @Test
    public void addedLineNotInTargetVariant() throws IOException {
        IFeatureModel model = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED");
        final FeatureModelFormula fmf = new FeatureModelFormula(model);
        Variant variant = new Variant("Target", new FeatureIDEConfiguration(fmf, Arrays.asList("A", "B", "C", "D", "LEAD", "TRAIL", "FILE", "UNRELATED")));
        PCBasedFilter pcBasedFilter = getPCBasedFilter(variant);
        runComparison(splitsDir.resolve("addedLineNotInTargetVariant.txt"), pcBasedFilter, pcBasedFilter);
    }

    @Test
    public void leadContextLineNotInTargetVariant() throws IOException {
        IFeatureModel model = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED");
        final FeatureModelFormula fmf = new FeatureModelFormula(model);
        Variant variant = new Variant("Target", new FeatureIDEConfiguration(fmf, Arrays.asList("A", "B", "C", "D", "E", "TRAIL", "FILE", "UNRELATED")));
        PCBasedFilter pcBasedFilter = getPCBasedFilter(variant);
        runComparison(splitsDir.resolve("leadContextLineNotInTargetVariant.txt"), pcBasedFilter, pcBasedFilter);
    }

    @Test
    public void trailContextLineNotInTargetVariant() throws IOException {
        IFeatureModel model = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED");
        final FeatureModelFormula fmf = new FeatureModelFormula(model);
        Variant variant = new Variant("Target", new FeatureIDEConfiguration(fmf, Arrays.asList("A", "B", "C", "D", "E", "LEAD", "FILE", "UNRELATED")));
        PCBasedFilter pcBasedFilter = getPCBasedFilter(variant);
        runComparison(splitsDir.resolve("trailContextLineNotInTargetVariant.txt"), pcBasedFilter, pcBasedFilter);
    }

    @Test
    public void entireFileNotInTargetVariant() throws IOException {
        IFeatureModel model = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED");
        final FeatureModelFormula fmf = new FeatureModelFormula(model);
        Variant variant = new Variant("Target", new FeatureIDEConfiguration(fmf, Arrays.asList("A", "B", "C", "D", "E", "LEAD", "TRAIL", "UNRELATED")));
        PCBasedFilter pcBasedFilter = getPCBasedFilter(variant);
        runComparison(splitsDir.resolve("entireFileNotInTargetVariant.txt"), pcBasedFilter, pcBasedFilter);
    }

    @Test
    public void unrelatedLinesNotInTargetVariant() throws IOException {
        IFeatureModel model = FeatureModelUtils.FromOptionalFeatures("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE", "UNRELATED");
        final FeatureModelFormula fmf = new FeatureModelFormula(model);
        Variant variant = new Variant("Target", new FeatureIDEConfiguration(fmf, Arrays.asList("A", "B", "C", "D", "E", "LEAD", "TRAIL", "FILE")));
        PCBasedFilter pcBasedFilter = getPCBasedFilter(variant);
        runComparison(splitsDir.resolve("unrelatedLinesNotInTargetVariant.txt"), pcBasedFilter, pcBasedFilter);
    }
}
