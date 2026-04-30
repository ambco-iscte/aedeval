package evaluator.messages.inspectors;

import evaluator.AssertEqualsType;
import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import static extensions.Extensions.tryOrElse;

public class ClassGetStaticResult<T> extends Result {

    private static final class FieldNotStaticException extends Exception {

        private final Field field;

        public FieldNotStaticException(Field field) {
            if (field == null)
                throw new IllegalArgumentException("");
            if (Modifier.isStatic(field.getModifiers()))
                throw new IllegalArgumentException("");
            this.field = field;
        }

        @Override
        public String getMessage() {
            return "Field %s should be static, but visibility modifiers are: %s".formatted(
                field,
                Modifier.toString(field.getModifiers())
            );
        }
    }

    private final Class<?> owner;
    private final Field field;
    private final T expected;
    private final Object actual;
    private final AssertEqualsType equalsType;
    private final FieldNotStaticException notStaticException;

    public ClassGetStaticResult(Test test, Class<?> owner, Field field, T expected, AssertEqualsType equalsType) {
        super(test);

        if (equalsType == AssertEqualsType.CONTENT || equalsType == AssertEqualsType.PERMUTATION || equalsType == AssertEqualsType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Iterable.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be an array or Iterable collection of elements, but is " + expected.getClass().getCanonicalName() + "!");
        }

        if (equalsType == AssertEqualsType.TYPEOF && !Class.class.isAssignableFrom(expected.getClass()))
            throw new IllegalArgumentException("Expected value should be a java.lang.Class, but is " + expected.getClass().getCanonicalName() + "!");

        this.owner = owner;
        this.field = field;
        this.expected = expected;
        this.equalsType = equalsType;

        field.setAccessible(true);

        FieldNotStaticException exception = null;
        if (!Modifier.isStatic(field.getModifiers()))
            exception = new FieldNotStaticException(field);
        notStaticException = exception;

        this.actual = tryOrElse(() -> field.get(null), null);
    }

    @Override
    public String errorCode() {
        return "Wrong Class Property";
    }

    @Override
    public boolean passed() {
        return notStaticException == null && equalsType.equals(expected, actual);
    }

    @Override
    public String getMessage() {
        String expectedMessage = switch (equalsType) {
            case EXACT -> "expected <" + Extensions.toStringOrDefault(expected) + ">";
            case ANY -> "expected one of " + Extensions.toStringOrDefault(expected);
            case PERMUTATION -> "expected a permutation of <" + Extensions.toStringOrDefault(expected) + ">";
            case CONTENT -> "expected content to be <" + Extensions.toStringOrDefault(expected) + ">";
            case NOTNULL -> "expected a non-null value";
            case TYPEOF -> "expected a value of type " + ((Class<?>) expected).getCanonicalName();
        };

        if (passed())
            return Extensions.capitaliseFirstCharacter(expectedMessage);

        if (notStaticException != null)
            return "Expected field <%s> to be static, but its visibility modifiers are %s".formatted(
                    field,
                    Modifier.toString(field.getModifiers())
            );

        return "Field <%s> holds an incorrect value: %s but was <%s>".formatted(
                field,
                expectedMessage,
                Extensions.toStringOrDefault(actual)
        );
    }
}
