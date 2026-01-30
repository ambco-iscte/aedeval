package evaluator.messages;

import evaluator.annotations.Test;

public abstract class Result {

    public static String FAILURE_ERROR_CODE = "Assertion Failed";

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

    public static Result error(Test test, Throwable cause) {
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

    public static Result success(Test test, String message) {
        return new Result(test) {

            @Override
            public String errorCode() {
                return "Success";
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
