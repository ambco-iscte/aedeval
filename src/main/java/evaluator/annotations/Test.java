package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Annotation that identifies methods which test students' implemented API methods.
 * Identifies the target method's name, the list of expected results for each test case (as defined in the test method
 * proper), and the weight of the method towards the final assignment grade.
 *
 * @author Afonso Caniço
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Test {

    /**
     * What is this test method testing?
     * @return A description of the test case.
     */
    String description() default "";

    /**
     * Value that the question is worth.
     * @return The weight of the question.
     */
    double weight();

    /**
     * Amount (in absolute value) to take away from the evaluation if the test fails.
     * @return A double value. Should be between 0 and the maximum evaluation achievable.
     */
    double penalty() default 0.0;
}
