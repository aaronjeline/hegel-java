package com.aaroneline.hegeljava;

import org.junit.jupiter.api.extension.*;

public final class HegelParameterResolver implements ParameterResolver {
        private final TestCase testCase;

    HegelParameterResolver(TestCase testCase) {
        this.testCase = testCase;
    }

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
        return pc.getParameter().getType() == TestCase.class;
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
        return testCase;
    }
}
    

