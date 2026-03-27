package evaluator.messages;

import evaluator.annotations.Test;

import java.lang.annotation.*;
import java.util.Objects;

public abstract class Result {

    /**
     * Annotating a Result class with this annotation ensures it is always presented in the
     * resulting Report, even when the test case passed.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface AlwaysShowInReport { }

    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    @Documented
    public @interface DoNotShowInReport { }

    private final static String FAILURE_ERROR_CODE = "Failed Requirement";
    private final static String WARNING_ERROR_CODE = "Warning";

    private final Test test;

    public Result(Test test) {
        this.test = test;
    }

    public Test getTest() {
        return test;
    }

    public abstract String errorCode();
    public abstract boolean passed();
    public abstract String getMessage();

    @Override
    public String toString() {
        return (passed() ? "[pass] " : "[fail] ") + getMessage();
    }

    public static Result unexpectedException(Test test, Throwable cause) {
        return new Result(test) {
            @Override
            public String errorCode() {
                return cause.getClass().getSimpleName();
            }

            @Override
            public boolean passed() {
                return false;
            }

            @Override
            public String getMessage() {
                return String.format("Unexpected %s: %s", errorCode(), cause.getMessage());
            }
        };
    }

    public static Result success(Test test) {
        return new ScriptedSuccess(test);
    }

    public static Result failure(Test test) {
        return new ScriptedFailure(test);
    }

    public static Result failedRequirement(Test test, String message) {
        return new FailedRequirement(test, message);
    }

    public static Result warning(Test test, String message) {
        return new Warning(test, message);
    }

    public boolean isSuccess() {
        return this instanceof ScriptedSuccess;
    }

    public boolean isWarning() {
        return this instanceof Warning;
    }

    public boolean isFailure() {
        return this instanceof FailedRequirement || this instanceof ScriptedFailure;
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && test == ((Result) obj).test && toString().equals(obj.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), test, passed(), getMessage());
    }

    @DoNotShowInReport
    private static class ScriptedSuccess extends Result {
        private ScriptedSuccess(Test test) { super(test); }
        @Override public String errorCode() { return null; }
        @Override public boolean passed() { return true; }
        @Override public String getMessage() { return null; }
    }

    private static class Warning extends Result {
        private final String message;
        private Warning(Test test, String message) {
            super(test);
            this.message = message;
        }
        @Override public String errorCode() { return WARNING_ERROR_CODE; }
        @Override public boolean passed() { return false; }
        @Override public String getMessage() { return message; }
    }

    private static class FailedRequirement extends Result {
        private final String message;
        private FailedRequirement(Test test, String message) {
            super(test);
            this.message = message;
        }
        @Override public String errorCode() { return FAILURE_ERROR_CODE; }
        @Override public boolean passed() { return false; }
        @Override public String getMessage() { return message; }
    }

    @DoNotShowInReport
    private static class ScriptedFailure extends Result {
        private ScriptedFailure(Test test) {super(test); }
        @Override public String errorCode() { return null; }
        @Override public boolean passed() { return false; }
        @Override public String getMessage() { return null; }
    }
}
