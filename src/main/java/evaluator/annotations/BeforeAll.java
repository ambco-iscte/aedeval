package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Annotation marking methods that should be executed before running the tests in a submission test class.
 * Useful for creating instances that are used more than once, setting up test configurations just once, etc.
 *
 * Functionality identical to JUnit's
 * <a href="https://junit.org/junit5/docs/5.0.0/api/org/junit/jupiter/api/BeforeAll.html">BeforeAll</a> annotation.
 *
 * @author Afonso Caniço
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface BeforeAll {
}
