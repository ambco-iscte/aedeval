package evaluator.messages.inspectors;

import evaluator.AssertEqualsType;
import evaluator.annotations.Test;
import evaluator.messages.Result;
import extensions.Extensions;

public class ObjectGetPropertyResult<T> extends Result {

    private final Object owner;
    private final Class<?> fieldType;
    private final String fieldName;
    private final T expected;
    private final T actual;
    private final AssertEqualsType equalsType;

    public ObjectGetPropertyResult(Test test, Object owner, Class<?> fieldType, String fieldName, T expected, T actual, AssertEqualsType equalsType) {
        super(test);

        if (equalsType == AssertEqualsType.CONTENT || equalsType == AssertEqualsType.PERMUTATION || equalsType == AssertEqualsType.ANY) {
            if (expected != null && !expected.getClass().isArray() && !Iterable.class.isAssignableFrom(expected.getClass()))
                throw new IllegalArgumentException("Expected value should be an array or Iterable collection of elements, but is " + expected.getClass().getCanonicalName() + "!");
        }

        if (equalsType == AssertEqualsType.TYPEOF && !Class.class.isAssignableFrom(expected.getClass()))
            throw new IllegalArgumentException("Expected value should be a java.lang.Class, but is " + expected.getClass().getCanonicalName() + "!");

        this.owner = owner;
        this.fieldType = fieldType;
        this.fieldName = fieldName;
        this.expected = expected;
        this.actual = actual;
        this.equalsType = equalsType;
    }

    @Override
    public String errorCode() {
        return "Wrong Object Property";
    }

    @Override
    public boolean passed() {
        return equalsType.equals(expected, actual);
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

        return "Property <%s %s.%s> holds an incorrect value: %s but was <%s>".formatted(
                fieldType.getSimpleName(),
                Extensions.toStringOrDefault(owner),
                fieldName,
                expectedMessage,
                Extensions.toStringOrDefault(actual)
        );
    }
}
