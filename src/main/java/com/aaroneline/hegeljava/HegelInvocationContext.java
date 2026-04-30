package com.aaroneline.hegeljava;
import org.junit.jupiter.api.extension.*;
import java.util.List;

public class HegelInvocationContext implements TestTemplateInvocationContext {
    private final TestCase testCase;

    public HegelInvocationContext(TestCase testCase) {
        this.testCase = testCase;
    }

    @Override
    public String getDisplayName(int invocationIndex) {
        return testCase.isFinalRun() ? "Counterexample" : "Case #" + invocationIndex;
    }

    @Override
    public List<Extension> getAdditionalExtensions() {
        return List.of(new HegelParameterResolver(testCase), 
                new HegelInvocationInterceptor(testCase));
    }

}
