package mc.snakenest.launcher.benchmark;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BenchmarkSamplesTest {
    @Test void readsActualTimestampsAndUnitsWithoutRescaling() {
        var samples=BenchmarkRunner.parseSamples(JsonParser.parseString("[{\"seconds\":0.25,\"value\":552},{\"seconds\":0.5,\"value\":510}]"));
        assertEquals(List.of(new BenchmarkResult.Sample(.25,552),new BenchmarkResult.Sample(.5,510)),samples);
    }
    @Test void rejectsMalformedUnorderedAndInvalidSeries() {
        for(String input:new String[]{"null","{}","[{}]","[{\"seconds\":-1,\"value\":3}]","[{\"seconds\":0.2,\"value\":2},{\"seconds\":0.1,\"value\":3}]","[{\"seconds\":0.1,\"value\":0}]"}){
            assertTrue(BenchmarkRunner.parseSamples(JsonParser.parseString(input)).isEmpty());
        }
        assertTrue(BenchmarkResult.validSamples(List.of(new BenchmarkResult.Sample(.1,Double.NaN))).isEmpty());
        assertTrue(BenchmarkResult.validSamples(List.of(new BenchmarkResult.Sample(Double.POSITIVE_INFINITY,4))).isEmpty());
    }
    @Test void boundedImmutableSamplesAndBackwardsCompatibility() {
        assertTrue(new BenchmarkResult(552,121,"GPU",null).cpuSamples().isEmpty());
        assertTrue(BenchmarkResult.validSamples(java.util.Collections.nCopies(129,new BenchmarkResult.Sample(.1,4))).isEmpty());
        var result=new BenchmarkResult(552,121,"GPU",null,List.of(new BenchmarkResult.Sample(.25,552)),List.of(new BenchmarkResult.Sample(.1,2.166)));
        assertThrows(UnsupportedOperationException.class,()->result.cpuSamples().clear());
        assertEquals(2.166479338842975,result.gpuFrameMillis(),.00001);
    }
}
