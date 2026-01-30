package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Compiles {@link evaluator.Tester}-required source code files before the main file, if the additional files
 * are present.
 *
 * @author Afonso Caniço
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface PrecompileIfPresent {

    /**
     * The file names of all .java source code files to precompile, if the files exist.
     * @return An array of required Java source code files.
     */
    String[] value();
}
