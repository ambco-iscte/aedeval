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

    private final static String FAILURE_ERROR_CODE = "Failed Requirement";
    private final static String SUCCESS_ERROR_CODE = "Success";
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
        if (passed())
            return "[pass]" + getMessage();
        return "[fail]" + getMessage();
    }

    public static Result exception(Test test, Throwable cause) {
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

    public boolean isSuccess() {
        return this instanceof YouSucceedBecauseISaidSo;
    }

    public boolean isWarning() {
        return this instanceof WarningYouToDoBetterNextTime;
    }

    public boolean isFailedRequirement() {
        return this instanceof YouFailBecauseISaidSo;
    }

    public static Result success(Test test, String message) {
        return new YouSucceedBecauseISaidSo(test, message);
    }

    public static Result failedRequirement(Test test, String message) {
        return new YouFailBecauseISaidSo(test, message);
    }

    public static Result warning(Test test, String message) {
        return new WarningYouToDoBetterNextTime(test, message);
    }

    @Override
    public boolean equals(Object obj) {
        return obj != null && obj.getClass() == getClass() && test == ((Result) obj).test && toString().equals(obj.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getClass(), test, toString());
    }

    private static class YouSucceedBecauseISaidSo extends Result {
        private final String message;
        private YouSucceedBecauseISaidSo(Test test, String message) {
            super(test);
            this.message = message;
        }
        @Override public String errorCode() { return SUCCESS_ERROR_CODE; }
        @Override public boolean passed() { return true; }
        @Override public String getMessage() { return message; }
    }

    private static class WarningYouToDoBetterNextTime extends Result {
        private final String message;
        private WarningYouToDoBetterNextTime(Test test, String message) {
            super(test);
            this.message = message;
        }
        @Override public String errorCode() { return WARNING_ERROR_CODE; }
        @Override public boolean passed() { return false; }
        @Override public String getMessage() { return message; }
    }

    private static class YouFailBecauseISaidSo extends Result {
        private final String message;
        private YouFailBecauseISaidSo(Test test, String message) {
            super(test);
            this.message = message;
        }
        @Override public String errorCode() { return FAILURE_ERROR_CODE; }
        @Override public boolean passed() { return false; }
        @Override public String getMessage() { return message; }
    }
}
