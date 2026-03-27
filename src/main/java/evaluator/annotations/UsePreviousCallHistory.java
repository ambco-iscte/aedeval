package evaluator.annotations;

import java.lang.annotation.*;

/**
 * {@link evaluator.Tester} classes annotated with {@code UsePreviousCallHistory} will display the sequence of
 * previous method calls in each call's log, regardless of whether they were executed through {@code Tester.invoke}
 * or {@code Tester.invokeStateful}. Useful for tests targeting mutable data structures.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface UsePreviousCallHistory { }
