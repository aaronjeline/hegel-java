package com.aaroneline.stateful;

import com.aaroneline.Generators;
import com.aaroneline.TestCase;
import com.aaroneline.backend.AssumeFailedException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes a state machine test.
 *
 * <p>The state machine is any object with methods annotated with
 * {@link Rule} and (optionally) {@link Invariant}.
 *
 * <p>Usage:
 * <pre>{@code
 * @HegelTest
 * void testMyStateMachine(TestCase tc) {
 *     StateMachineRunner.run(new MyStateMachine(), tc);
 * }
 * }</pre>
 */
public final class StateMachineRunner {

    private StateMachineRunner() {}

    /**
     * Run the state machine for a random number of steps.
     *
     * <p>Rules are selected randomly. After each rule, all invariants are checked.
     * If a rule method calls {@code tc.assume(false)}, the step is skipped and not
     * counted. Other exceptions propagate as test failures.
     *
     * @param machine the state machine instance
     * @param tc      the current test case
     */
    public static void run(Object machine, TestCase tc) {
        List<Method> rules = collectAnnotated(machine.getClass(), Rule.class);
        List<Method> invariants = collectAnnotated(machine.getClass(), Invariant.class);

        if (rules.isEmpty()) {
            throw new IllegalArgumentException(
                    "State machine " + machine.getClass().getName() +
                    " has no @Rule methods.");
        }

        // Check invariants before the first rule.
        checkInvariants(machine, invariants);

        // Draw how many successful steps to execute (1–50).
        int stepCap = tc.drawSilent(Generators.integers(1, 50));

        int stepsOk = 0;
        while (stepsOk < stepCap) {
            int idx = tc.drawSilent(Generators.integers(0, rules.size() - 1));
            Method rule = rules.get(idx);
            tc.note("Step: " + rule.getName());

            try {
                rule.invoke(machine, tc);
                stepsOk++;
                checkInvariants(machine, invariants);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AssumeFailedException) {
                    // Rule precondition not met; skip this step.
                    tc.note("  (skipped: precondition not satisfied)");
                } else if (cause instanceof RuntimeException re) {
                    throw re;
                } else if (cause != null) {
                    throw new RuntimeException("State machine rule failed: " + rule.getName(), cause);
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not invoke rule: " + rule.getName(), e);
            }
        }
    }

    // ── Reflection helpers ─────────────────────────────────────────────────────

    private static List<Method> collectAnnotated(
            Class<?> cls, Class<? extends java.lang.annotation.Annotation> annotation) {
        List<Method> result = new ArrayList<>();
        for (Method m : cls.getMethods()) {
            if (m.isAnnotationPresent(annotation)) {
                m.setAccessible(true);
                result.add(m);
            }
        }
        return result;
    }

    private static void checkInvariants(Object machine, List<Method> invariants) {
        for (Method inv : invariants) {
            try {
                inv.invoke(machine);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause instanceof AssertionError ae) throw ae;
                if (cause instanceof RuntimeException re) throw re;
                throw new RuntimeException("Invariant failed: " + inv.getName(), cause);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not invoke invariant: " + inv.getName(), e);
            }
        }
    }
}
