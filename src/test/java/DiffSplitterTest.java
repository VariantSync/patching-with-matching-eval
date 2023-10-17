import org.variantsync.evaluation.baseline.diff.DiffParser;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.filter.IFileDiffFilter;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.variantsync.evaluation.baseline.diff.splitting.DefaultContextProvider;
import org.variantsync.evaluation.baseline.diff.splitting.DiffSplitter;
import org.variantsync.evaluation.baseline.diff.splitting.IContextProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class DiffSplitterTest {
    Path resourceDir = Paths.get("src", "test", "resources", "patch-breakdown", "splits");

    private void runComparison(Path pathToExpectedResult, IFileDiffFilter fileDiffFilter, ILineFilter lineFilter) throws IOException {
        List<String> diffLines = Files.readAllLines(resourceDir.getParent().resolve("diff-A-B.txt"));
        IContextProvider contextProvider = new DefaultContextProvider(resourceDir.getParent(), false);
        FineDiff fineDiff;
        if (fileDiffFilter == null && lineFilter == null) {
            fineDiff = DiffSplitter.split(DiffParser.toOriginalDiff(diffLines), contextProvider);
        } else {
            fineDiff = DiffSplitter.split(DiffParser.toOriginalDiff(diffLines), fileDiffFilter, lineFilter, contextProvider);
        }

        List<String> expectedLines = Files.readAllLines(pathToExpectedResult);
        List<String> actualLines = fineDiff.toLines();
        for (int i = 0; i < expectedLines.size(); i++) {
            String expectedLine = expectedLines.get(i);
            String actualLine = actualLines.get(i);
            Assertions.assertEquals(expectedLine, actualLine);
        }
    }

    private void runComparison(Path pathToExpectedResult) throws IOException {
        runComparison(pathToExpectedResult, null, null);
    }

    @Test
    public void splitToBasicFineDiff() throws IOException {
        Path pathToExpectedResult = resourceDir.getParent().resolve("fine-diff-A-B.txt");
        runComparison(pathToExpectedResult);
    }

    @Test
    public void filterAllFiles() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterAllFiles.txt");
        runComparison(pathToExpectedResult, f -> false, null);
    }

    @Test
    public void filterFirstFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterFirstFile.txt");
        runComparison(pathToExpectedResult, f -> !f.oldFile().toString().contains("first-file.txt"), null);
    }

    @Test
    public void filterEmptyLineOfThirdFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterEmptyLineOfThirdFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/third-file.txt") && i == 4);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterAllHunksOfSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterAllHunksOfSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("second-file.txt"));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterMyObjEditsInThirdFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterMyObjEditsInThirdFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/third-file.txt") && (i == 7 || i == 8));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterCommentsInSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterCommentsInSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && (i == 15 || i == 18) || f.toString().contains("version-B/second-file.txt") && (i == 13 || i == 16 || i == 18));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterZumZumInsertionInFirstFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterZumZumInsertionInFirstFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-B/first-file.txt") && i == 12);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterBlablaInsertionInFirstFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterBlablaInsertionInFirstFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-B/first-file.txt") && i == 13);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterFordDeletionInSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterFordDeletionInSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && i == 12);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterMazdaDeletionInSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterMazdaDeletionInSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && i == 13);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterDeletionInSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterDeletionInSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && (i == 12 || i == 13));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterOneLineOfLeadingContextOfFirstFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterOneLineOfLeadingContextOfFirstFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-B/first-file.txt") && i == 9);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterTwoLinesOfLeadingContextOfFirstFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterTwoLinesOfLeadingContextOfFirstFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-B/first-file.txt") && (i == 9 || i == 10));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterAllLinesOfLeadingContextOfFirstFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterAllLinesOfLeadingContextOfFirstFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-B/first-file.txt") && i < 12);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterOneLineOfContextBetweenEditsOfSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterOneLineOfContextBetweenEditsOfSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && i == 15 || f.toString().contains("version-B/second-file.txt") && i == 13);
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterThreeLinesOfContextBetweenEditsOfSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterThreeLinesOfContextBetweenEditsOfSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && (i >= 15 && i < 18) || f.toString().contains("version-B/second-file.txt") && (i >= 13 && i < 16));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterAllContextBetweenEditsOfSecondFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterAllContextBetweenEditsOfSecondFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("version-A/second-file.txt") && (i >= 14 && i < 20) || f.toString().contains("version-B/second-file.txt") && (i >= 12 && i < 18));
        runComparison(pathToExpectedResult, null, lineFilter);
    }

    @Test
    public void filterTrailingContextOfThirdFile() throws IOException {
        Path pathToExpectedResult = resourceDir.resolve("filterTrailingContextOfThirdFile.txt");
        ILineFilter lineFilter = (f, i) -> !(f.toString().contains("third-file.txt") && i == 11);
        runComparison(pathToExpectedResult, null, lineFilter);
    }
}