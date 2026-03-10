package evaluator.annotations;

import java.lang.annotation.*;

/**
 * Tester classes annotated with this annotation allow evaluated code to use
 * the specified Java packages. Example: {@code @AllowedPackages({"java.util", "java.io"})}.
 *
 * <p>If this annotation is not included, the package {@code java.lang} is permitted by default. You can disallow it
 * using {@code @AllowedPackages()} (or by specifying other packages, but not {@code java.lang}).</p>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
public @interface AllowedPackages {
    String[] DEFAULT = new String[] { "java.lang" };
    String[] value();
}
