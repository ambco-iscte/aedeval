package evaluator.messages;

import evaluator.annotations.Test;

import java.util.Objects;

public abstract class Result {

    private final static String FAILURE_ERROR_CODE = "Assertion Failed";
    private final static String SUCCESS_ERROR_CODE = "Success";

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
        return Objects.equals(errorCode(), SUCCESS_ERROR_CODE);
    }

    public boolean isFailure() {
        return Objects.equals(errorCode(), FAILURE_ERROR_CODE);
    }

    public static Result success(Test test, String message) {
        return new Result(test) {

            @Override
            public String errorCode() {
                return SUCCESS_ERROR_CODE;
            }

            @Override
            public boolean passed() {
                return true;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }

    public static Result failure(Test test, String message) {
        return new Result(test) {
            @Override
            public String errorCode() {
                return FAILURE_ERROR_CODE;
            }

            @Override
            public boolean passed() {
                return false;
            }

            @Override
            public String getMessage() {
                return message;
            }
        };
    }
}
