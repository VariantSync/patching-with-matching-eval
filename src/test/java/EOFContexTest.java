import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.variantsync.evaluation.baseline.diff.DiffParser;
import org.variantsync.evaluation.baseline.diff.components.FineDiff;
import org.variantsync.evaluation.baseline.diff.filter.ILineFilter;
import org.variantsync.evaluation.baseline.diff.splitting.DefaultContextProvider;
import org.variantsync.evaluation.baseline.diff.splitting.DiffSplitter;
import org.variantsync.evaluation.baseline.diff.splitting.IContextProvider;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class EOFContexTest {
    Path resourceDir = Paths.get("src", "test", "resources", "eof");

    @Test
    public void runComparison() throws IOException {
        List<String> diffLines = Files.readAllLines(resourceDir.resolve("original.diff"));
        IContextProvider contextProvider = new DefaultContextProvider(resourceDir, false);
        FineDiff fineDiff = DiffSplitter.split(DiffParser.toOriginalDiff(diffLines), null, null, contextProvider);

        List<String> expectedLines = Files.readAllLines(resourceDir.resolve("splits.diff"));
        List<String> actualLines = fineDiff.toLines();
        for (int i = 0; i < expectedLines.size(); i++) {
            String expectedLine = expectedLines.get(i);
            String actualLine = actualLines.get(i);
            Assertions.assertEquals(expectedLine, actualLine, "Mismatch in line " + (i+1));
        }
    }
}