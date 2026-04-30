package com.aaroneline.hegeljava;
import org.junit.jupiter.api.extension.*;
import java.util.List;
import java.lang.reflect.Method;
import com.aaroneline.hegeljava.backend.AssumeFailedException;
import com.aaroneline.hegeljava.backend.ServerDataSource;
import com.aaroneline.hegeljava.backend.StopTestException;

public final class HegelInvocationInterceptor implements InvocationInterceptor {
    private final TestCase testCase;

    public HegelInvocationInterceptor(TestCase testCase) {
        this.testCase = testCase;
    }

    public void interceptTestTemplateMethod(
            Invocation<Void> invocation,
            ReflectiveInvocationContext<Method> invocationContext,
            ExtensionContext extensionContext) throws Throwable {
        var ds = testCase.getDataSource();
        try {
            invocation.proceed();
            if (!ds.testAborted()) {
                ds.markComplete("VALID", null);
            }
        } catch (AssumeFailedException e) {
            if (!ds.testAborted()) {
                ds.markComplete("INVALID", null);
            }
            // Swallow
        } catch (StopTestException e) {
            // Swallow
        } catch (Throwable t) {
            if (!ds.testAborted()) {
                ds.markComplete("INTERESTING", failureOrigin(t));
            }
            if (testCase.isFinalRun()) {
                List<String> lines = testCase.getCounterexampleLines();
                if (!lines.isEmpty()) {
                    var msg = new StringBuilder("Falsyifying example:\n");
                    for  (var line : lines) {
                        msg.append("\t").append(line).append('\n');
                    }
                    var wrapper = new AssertionError(msg.toString().stripTrailing(), t);
                    wrapper.setStackTrace(t.getStackTrace());
                    throw wrapper;
                }
                throw t;
            }
            // Not a final run: swallow
        }
     }

    public static String failureOrigin(Throwable t) {
        StackTraceElement[] stack = t.getStackTrace();
        if (stack.length > 0) {
            var frame = firstUserFrame(stack);
            return t.getClass().getName() + " at " + 
                frame.getClassName() + "." + frame.getMethodName() + 
                ":" + frame.getLineNumber();
        }
        return t.getClass().getName();
    }

    private static StackTraceElement firstUserFrame(StackTraceElement[] stack) {
        for (var frame : stack) {
            String cls = frame.getClassName();
            if (!cls.startsWith("com.aaroneline.hegel.java.")
                    && !cls.startsWith("org.junit.")
                    && !cls.startsWith("org.opentest4j.")
                    && !cls.startsWith("java.")
                    && !cls.startsWith("jdk.")) {
                return frame;
            }
        }
        return stack[0];
    }

}
