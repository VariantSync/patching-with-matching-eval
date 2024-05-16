import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.variantsync.evaluation.EvalConfig;
import org.variantsync.evaluation.SamplingKt;

import java.io.File;
import java.util.stream.Stream;

public class SampleSizeTest {

    // Values determined with external sample size calculator
    private static Stream<Arguments> providesSampleSizes() {
        return Stream.of(
                Arguments.of(5, 5),
                Arguments.of(7, 7),
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
        var configFile = new File("src/main/resources/config-debug.properties");
        var sampleSize = SamplingKt.determineSampleSize(new EvalConfig(configFile), population);
        Assertions.assertEquals(sampleSize, expectedSampleSize);
    }
}
