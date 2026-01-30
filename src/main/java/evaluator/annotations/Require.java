package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Used to annotate submission testing methods with the class names required for test execution.
 * <p>
 * Example: @Require(classes={"MySourceCodeFile.java"})
 *
 * @author Afonso Caniço
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@Documented
public @interface Require {

    /**
     * The file names of all required .java source code files.
     * @return An array of required class source code files.
     */
    String[] value();

    /**
     * True if the test is a hard requirement, i.e. none of the other tests should even run
     * if this test fails.
     */
    boolean hard() default false;
}
