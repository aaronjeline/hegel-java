# Hegel-Java

An implementation of [Hegel](https://hegel.dev) in Java.

Currently extremely rough, don't depend on it.

## Getting Started

Hegel is a property-based testing library for Java. Hegel is based on
[Hypothesis](https://github.com/HypothesisWorks/hypothesis), using the
[Hegel](https://hegel.dev/) protocol.

This guide walks you through the basics of installing Hegel and writing your
first tests.

### Install Hegel

Add `hegel-java` to your project as a test dependency.

If you are working from this repository, first install the local snapshot:

```bash
mvn install
```

Then add the dependency to your project's `pom.xml`:

```xml
<dependency>
  <groupId>io.hegel</groupId>
  <artifactId>hegel-java</artifactId>
  <version>0.1.0-SNAPSHOT</version>
  <scope>test</scope>
</dependency>
```

Hegel runs tests through JUnit 5, so your project should also be configured to
use the JUnit Platform. If you are using Maven, the Surefire plugin works well:

```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-surefire-plugin</artifactId>
  <version>3.3.1</version>
  <configuration>
    <useModulePath>false</useModulePath>
  </configuration>
</plugin>
```

The Java client starts a Hegel server automatically. It first looks for a
`hegel` executable on your `PATH`; if one is not found, it falls back to running
the server with `uvx`. You can override the command with the
`HEGEL_SERVER_COMMAND` environment variable.

### Write Your First Test

You're now ready to write your first test. We'll use JUnit as the test runner
for the purposes of this guide. Create a new test under your project's
`src/test/java` directory:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegerPropertiesTest {

    @HegelTest
    void integerSelfEquality(TestCase tc) {
        int n = tc.draw(Generators.integers());
        assertEquals(n, n); // integers should always be equal to themselves
    }
}
```

Now run the test using Maven:

```bash
mvn test
```

You should see that this test passes.

Let's look at what's happening in more detail. The `@HegelTest` annotation runs
your test many times, 100 by default. The test method takes a `TestCase`
parameter, which provides a `draw` method for drawing different values. This
test draws an integer and checks that it is equal to itself.

Next, try a test that fails:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegerPropertiesTest {

    @HegelTest
    void integersAlwaysBelow50(TestCase tc) {
        int n = tc.draw(Generators.integers());
        assertTrue(n < 50); // this will fail
    }
}
```

This test asserts that every integer is less than 50, which is incorrect. Hegel
will find a test case that makes this assertion fail, and then shrink it to find
the smallest counterexample. In this case, the minimal failing value is `n = 50`.

To fix this test, constrain the integers you generate with `minValue` and
`maxValue`:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertTrue;

class IntegerPropertiesTest {

    @HegelTest
    void boundedIntegersAlwaysBelow50(TestCase tc) {
        int n = tc.draw(Generators.integers()
                .minValue(0)
                .maxValue(49));
        assertTrue(n < 50);
    }
}
```

Run the test again. It should now pass.

### Use Generators

Hegel provides a library of generators that you can use out of the box. There
are primitive generators, such as `integers`, `longs`, `doubles`, `booleans`,
`strings`, and `bytes`, and combinators that make generators out of other
generators, such as `lists`, `sets`, `maps`, `tuples`, `oneOf`, and `optional`.

For example, you can use `lists` to generate a list of integers:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ListPropertiesTest {

    @HegelTest
    void appendIncreasesLength(TestCase tc) {
        List<Integer> list = new ArrayList<>(
                tc.draw(Generators.lists(Generators.integers())));
        int initialLength = list.size();

        list.add(tc.draw(Generators.integers()));

        assertEquals(initialLength + 1, list.size());
    }
}
```

This test checks that appending an element to a random list of integers always
increases its length.

You can also define custom generators. A custom generator is any implementation
of `Generator<T>`. For example, say you have a `Person` class that you want to
generate:

```java
import io.hegel.Generator;
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class PersonPropertiesTest {

    record Person(int age, String name) {}

    static final Generator<Person> PEOPLE = tc -> {
        int age = tc.draw(Generators.integers());
        String name = tc.draw(Generators.strings());
        return new Person(age, name);
    };

    @HegelTest
    void generatedPeopleHaveNames(TestCase tc) {
        Person person = tc.draw(PEOPLE);
        assertNotNull(person.name());
    }
}
```

You can feed the results of one `draw` call into later generation decisions. For
example, say you extend `Person` to include a `drivingLicense` boolean field:

```java
import io.hegel.Generator;
import io.hegel.Generators;

class PersonGenerators {

    record Person(int age, String name, boolean drivingLicense) {}

    static final Generator<Person> PEOPLE = tc -> {
        int age = tc.draw(Generators.integers());
        String name = tc.draw(Generators.strings());
        boolean drivingLicense = age >= 18
                ? tc.draw(Generators.booleans())
                : false;

        return new Person(age, name, drivingLicense);
    };
}
```

### Debug Failing Test Cases

Use the `note` method to attach debug information:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DebugPropertiesTest {

    @HegelTest
    void additionIsCommutative(TestCase tc) {
        int x = tc.draw(Generators.integers());
        int y = tc.draw(Generators.integers());

        tc.note("x + y = " + (x + y) + ", y + x = " + (y + x));

        assertEquals(x + y, y + x);
    }
}
```

Notes only appear when Hegel replays the minimal failing example.

During the final replay, Hegel also prints drawn values, which can help explain
the exact counterexample that caused the property to fail.

### Change the Number of Test Cases

By default, Hegel runs 100 test cases. To override this, pass `testCases` to the
`@HegelTest` annotation:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IntegerPropertiesTest {

    @HegelTest(testCases = 500)
    void integersMany(TestCase tc) {
        int n = tc.draw(Generators.integers());
        assertEquals(n, n);
    }
}
```

You can also set a fixed seed for reproducibility:

```java
@HegelTest(seed = 1234L)
void reproducibleProperty(TestCase tc) {
    int n = tc.draw(Generators.integers());
    // Your assertions here
}
```

### Filter Test Cases

Use `assume` when only some generated inputs are valid for the property you want
to test:

```java
import io.hegel.Generators;
import io.hegel.HegelTest;
import io.hegel.TestCase;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AssumptionPropertiesTest {

    @HegelTest
    void onlyChecksNonNegativeIntegers(TestCase tc) {
        int n = tc.draw(Generators.integers());
        tc.assume(n >= 0);

        assertTrue(n >= 0);
    }
}
```

If the assumption is false, Hegel discards that test case and generates another
one.
