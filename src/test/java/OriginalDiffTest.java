import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.anon.evaluation.util.diff.DiffParser;
import org.anon.evaluation.util.diff.components.OriginalDiff;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class OriginalDiffTest {
    final static Path BASE_DIR = Path.of("src/test/resources/original-diff");
    final OriginalDiff baseDiff = loadDiff("base-diff.diff");

    private OriginalDiff loadDiff(String file) {
        Path pathToDiff = BASE_DIR.resolve(file);
        try {
            List<String> lines = Files.readAllLines(pathToDiff);
            return DiffParser.toOriginalDiff(lines);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    public void sameIsPartiallyEqual() {
        OriginalDiff diff = loadDiff("base-diff.diff");
        Assertions.assertTrue(baseDiff.partiallyEquals(diff, 1));
    }

    @Test
    public void slightlyDifferentIsPartiallyEqual() {
        OriginalDiff diff = loadDiff("additional-file.diff");
        Assertions.assertTrue(baseDiff.partiallyEquals(diff, 1));
    }

    @Test
    public void differentChange() {
        OriginalDiff diff = loadDiff("different-change.diff");
        Assertions.assertFalse(baseDiff.partiallyEquals(diff, 1));
    }

    @Test
    public void differentContext() {
        OriginalDiff diff = loadDiff("different-context.diff");
        Assertions.assertFalse(baseDiff.partiallyEquals(diff, 1));
    }

    @Test
    public void differentHunkStart() {
        OriginalDiff diff = loadDiff("different-hunk-start.diff");
        Assertions.assertFalse(baseDiff.partiallyEquals(diff, 1));
    }

    @Test
    public void differentFile() {
        OriginalDiff diff = loadDiff("different-file.diff");
        Assertions.assertFalse(baseDiff.partiallyEquals(diff, 1));
    }
}
