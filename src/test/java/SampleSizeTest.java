import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.variantsync.evaluation.EvalConfig;
import org.variantsync.evaluation.Operations;
import org.variantsync.evaluation.baseline.shell.AppliedPatchTracker;
import org.variantsync.evaluation.baseline.shell.ShellExecutor;

import java.io.File;
import java.nio.file.Path;
import java.util.stream.Stream;

public class SampleSizeTest {

    // Values determined with external sample size calculator
    private static Stream<Arguments> providesSampleSizes() {
        return Stream.of(
                Arguments.of(5, 5),
                Arguments.of(50, 45),
                Arguments.of(100, 80),
                Arguments.of(1000, 278),
                Arguments.of(5000, 357),
                Arguments.of(200_000, 384)
        );
    }

    @ParameterizedTest
    @MethodSource("providesSampleSizes")
    public void simpleSize(int population, int expectedSampleSize) {
        TestOperations operations = new TestOperations();
        var configFile = new File("src/main/resources/config-debug.properties");
        var sampleSize = operations.determineSampleSize(new EvalConfig(configFile), population);
        Assertions.assertEquals(sampleSize, expectedSampleSize);
    }

    private class TestOperations extends Operations {

        @NotNull
        @Override
        public Path rejectsFile() {
            return null;
        }

        @NotNull
        @Override
        public Path rejectsFileFiltered() {
            return null;
        }

        @NotNull
        @Override
        public Path splitAndFilteredPatchFile() {
            return null;
        }

        @NotNull
        @Override
        public Path splitPatchFile() {
            return null;
        }

        @NotNull
        @Override
        public Path workDir() {
            return null;
        }

        @NotNull
        @Override
        public ShellExecutor shell() {
            return null;
        }

        @NotNull
        @Override
        public Path patchDir() {
            return null;
        }

        @NotNull
        @Override
        public AppliedPatchTracker appliedPatchTracker() {
            return null;
        }

        @NotNull
        @Override
        public Path filteredPatchFile() {
            return null;
        }

        @NotNull
        @Override
        public Path patchFile() {
            return null;
        }

        @NotNull
        @Override
        public Path sourceV0Path(@NotNull String name) {
            return null;
        }
    }
}
