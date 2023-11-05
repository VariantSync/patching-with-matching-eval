import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.variantsync.evaluation.baseline.diff.DiffParser;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.splitting.DefaultContextProvider;
import org.variantsync.evaluation.baseline.diff.splitting.DiffSplitter;
import org.variantsync.evaluation.baseline.diff.splitting.IContextProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class SpecialCasesTest {
    Path eofTests = Paths.get("src", "test", "resources", "eof");
    Path entireFileTests = Paths.get("src", "test", "resources", "entire-file");

    public void runComparison(Path testDir) throws IOException {
        List<String> diffLines = Files.readAllLines(testDir.resolve("original.diff"));
        IContextProvider contextProvider = new DefaultContextProvider(testDir, false);
        FineDiff fineDiff = DiffSplitter.split(DiffParser.toOriginalDiff(diffLines), null, null, contextProvider);

        List<String> expectedLines = Files.readAllLines(testDir.resolve("splits.diff"));
        List<String> actualLines = fineDiff.toLines();
        for (int i = 0; i < expectedLines.size(); i++) {
            String expectedLine = expectedLines.get(i);
            String actualLine = actualLines.get(i);
            Assertions.assertEquals(expectedLine, actualLine, "Mismatch in line " + (i+1));
        }
    }

    @Test
    public void eofTest() throws IOException {
        runComparison(eofTests);
    }

    @Test
    public void entireFileTest() throws IOException {
        runComparison(entireFileTests);
    }
}