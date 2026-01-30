package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Annotation that marks methods that should be executed once before each test method's execution in a submission
 * test class. Useful for resetting parameters, etc.
 *
 * Functionality identical to JUnit's
 * <a href="https://junit.org/junit5/docs/5.0.0/api/org/junit/jupiter/api/BeforeEach.html">BeforeEach</a> annotation.
 *
 * @author Afonso Caniço
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface BeforeEach {
}
