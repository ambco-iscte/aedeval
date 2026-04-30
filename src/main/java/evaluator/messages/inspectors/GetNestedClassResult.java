package evaluator.messages.inspectors;

import evaluator.annotations.Test;
import evaluator.messages.Result;

public class GetNestedClassResult extends Result {

    private final Class<?> owner;
    private final String nestedClassName;
    private final Class<?> nestedClass;
    private final ClassNotFoundException exception;

    public GetNestedClassResult(Test test, Class<?> owner, String nestedClassName, Class<?> nestedClass, ClassNotFoundException exception) {
        super(test);
        this.owner = owner;
        this.nestedClassName = nestedClassName;
        this.nestedClass = nestedClass;
        this.exception = exception;
    }

    @Override
    public String errorCode() {
        return "Inner Class Not Found";
    }

    @Override
    public boolean passed() {
        return nestedClass != null && exception == null;
    }

    @Override
    public String getMessage() {
        String implementsNested = passed() ? "implements" : "does not implement";
        return "Class %s %s inner class <%s>.".formatted(owner.getSimpleName(), implementsNested, nestedClassName);
    }
}
