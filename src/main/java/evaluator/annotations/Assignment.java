package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Descriptive annotation that describes a Tester class's corresponding assignment.
 *
 * @author Afonso Caniço
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface Assignment {
    String value() default "";
}
