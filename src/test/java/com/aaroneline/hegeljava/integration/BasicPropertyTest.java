package com.aaroneline.hegeljava.integration;

import com.aaroneline.hegeljava.Generators;
import com.aaroneline.hegeljava.HegelTest;
import com.aaroneline.hegeljava.TestCase;
import com.aaroneline.hegeljava.stateful.Invariant;
import com.aaroneline.hegeljava.stateful.Rule;
import com.aaroneline.hegeljava.stateful.StateMachineRunner;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Integration tests that require a running Hegel server.
 *
 * <p>Enable with {@code -Dhegel.integration=true} or by having {@code hegel}
 * (or {@code uvx}) on PATH and setting the system property. These tests are
 * skipped in environments without the server binary.
 */
@EnabledIfSystemProperty(named = "hegel.integration", matches = "true")
class BasicPropertyTest {

    @HegelTest
    void integerSelfEquality(TestCase tc) {
        int n = tc.draw(Generators.integers());
        assert n == n : "integers should equal themselves";
    }

    @HegelTest
    void listAppendIncreasesLength(TestCase tc) {
        List<Integer> list = new ArrayList<>(tc.draw(Generators.lists(Generators.integers())));
        int initialLength = list.size();
        list.add(tc.draw(Generators.integers()));
        assert list.size() == initialLength + 1 : "append should increase size";
    }

    @HegelTest
    void positiveIntegersArePositive(TestCase tc) {
        int n = tc.draw(Generators.integers().minValue(1));
        assert n > 0 : "n should be positive but was " + n;
    }

    @HegelTest
    void optionalNeverThrows(TestCase tc) {
        Optional<String> s = tc.draw(Generators.optional(Generators.strings()));
        // Just verifies draw doesn't throw
        s.ifPresent(str -> { assert str != null; });
    }

    @HegelTest
    void emailLooksLikeEmail(TestCase tc) {
        String email = tc.draw(Generators.emails());
        assert email.contains("@") : "email should contain @: " + email;
    }

    @HegelTest
    void listFilteredByAssume(TestCase tc) {
        int n = tc.draw(Generators.integers());
        tc.assume(n >= 0);
        assert n >= 0 : "should only see non-negative: " + n;
    }

    @HegelTest
    void mappedGeneratorPreservesSchema(TestCase tc) {
        // map on BasicGenerator should still be a single round-trip
        int doubled = tc.draw(Generators.integers(0, 50).map(x -> x * 2));
        assert doubled >= 0 && doubled <= 100 : "doubled should be in [0,100]: " + doubled;
    }

    // ── Stateful: min-stack ────────────────────────────────────────────────────

    @HegelTest
    void minStackStateMachine(TestCase tc) {
        StateMachineRunner.run(new MinStack(), tc);
    }

    static class MinStack {
        private final List<Integer> stack = new ArrayList<>();
        private final List<Integer> minTracker = new ArrayList<>();

        @Rule
        public void push(TestCase tc) {
            int val = tc.draw(Generators.integers());
            stack.add(val);
            if (minTracker.isEmpty() || val <= minTracker.get(minTracker.size() - 1)) {
                minTracker.add(val);
            }
        }

        @Rule
        public void pop(TestCase tc) {
            tc.assume(!stack.isEmpty());
            int val = stack.remove(stack.size() - 1);
            if (!minTracker.isEmpty() && val == minTracker.get(minTracker.size() - 1)) {
                minTracker.remove(minTracker.size() - 1);
            }
        }

        @Invariant
        public void minimumIsConsistent() {
            if (stack.isEmpty()) return;
            int fastMin = minTracker.get(minTracker.size() - 1);
            int slowMin = stack.stream().mapToInt(Integer::intValue).min().orElseThrow();
            assert fastMin == slowMin
                    : "min mismatch: fast=" + fastMin + " slow=" + slowMin;
        }
    }
}
