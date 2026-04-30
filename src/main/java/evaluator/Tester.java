package evaluator;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import evaluator.algorithmic.OrderOfGrowth;
import evaluator.algorithmic.OrderOfGrowthEstimator;
import evaluator.algorithmic.functions.SymbolicFunction;
import evaluator.annotations.*;
import evaluator.messages.Result;
import evaluator.messages.RuntimeComplexityEstimation;
import evaluator.messages.constructors.ConstructorMissingExceptionError;
import evaluator.messages.constructors.ConstructorNotImplementedError;
import evaluator.messages.constructors.ObjectInstantiationError;
import evaluator.messages.files.FileAndClassNameMismatchError;
import evaluator.messages.files.IncorrectFileNameError;
import evaluator.messages.files.MissingFileError;
import evaluator.messages.inspectors.*;
import evaluator.messages.inspectors.NoSuchFieldError;
import evaluator.messages.loading.ClassLoadingError;
import evaluator.messages.loading.CompilationError;
import evaluator.messages.methods.*;
import extensions.Extensions;
import extensions.Files;
import extensions.Levenshtein;
import extensions.lang.ThrowingBiConsumer;
import extensions.lang.ThrowingFunction;
import extensions.lang.ThrowingRunnable;
import extensions.out.Console;
import loading.ClassLoader;
import loading.Source;
import loading.SourceLookup;
import loading.exceptions.ClassLoadingException;
import loading.exceptions.CompilationException;
import org.apache.commons.io.FilenameUtils;
import org.jspecify.annotations.NonNull;
import reflection.Reflector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import static extensions.Extensions.tryOrElse;

/**
 * Abstract class used to test a student's submission. Automatically loads .java files, compiles them, and runs the
 * appropriate tests.
 * <p>
 * Most methods are either private auxiliary methods or protected methods to be used in assignment-specific
 * test classes.
 * <p>
 * {@link Tester#runAllTests()} is the main method, which runs all submission tests and collects the results -
 * used by {@link FullEvaluator} to run all tests for a loaded class.
 *
 * @author Caroline Conti
 * @author Afonso Caniço
 *
 */
@SuppressWarnings("UnusedReturnValue")
public class Tester extends Reflector {

	private final Map<Test, List<MethodCall>> invocations = new HashMap<>();
	private final Map<Test, Set<Result>> results = new HashMap<>();
	private final Map<String, Class<?>> compiledTypes = new HashMap<>(); // Only compile class once, reuse if possible
	private final List<String> invalidClassNames = new ArrayList<>(); // If an error is raised, don't try loading again
    private final Map<String, CompilationUnit> compilationUnits = new HashMap<>();
    private volatile Test currentTest;
	private final Submission submission;
    private double fileNameSimilarityThreshold = 0.8;
    private final boolean usePreviousCallHistoryGlobal = getClass().isAnnotationPresent(UsePreviousCallHistory.class);
    private final AtomicBoolean silent = new AtomicBoolean(false);

	/**
	 * Creates an instance of a tester for a directory containing Java source code files.
	 * @param submission Submission to be tested.
	 */
	public Tester(Submission submission) {
		this.submission = submission;
	}

    public void setFileNameSimilarityThreshold(double threshold) {
        if (threshold < 0 || threshold > 1)
            throw new IllegalArgumentException("File name similarity threshold must be >= 0 and <= 1.");
        fileNameSimilarityThreshold = threshold;
    }

	public static Set<String> getAllRequiredFiles(Class<? extends Tester> type) {
		Set<String> files = new HashSet<>();
		for (Method test : getAnnotatedMethods(type, Test.class)) {
			Require require = test.getAnnotation(Require.class);
			if (require != null) {
				files.addAll(Arrays.asList(require.value()));
			}
		}
		return files;
	}

	public Map<Test, Set<Result>> getResults() {
		return results;
	}

	public Submission getSubmission() {
		return submission;
	}

	protected void log(Result message) {
        if (silent.get())
            return;
		if (!results.containsKey(currentTest))
			results.put(currentTest, new HashSet<>());
		results.get(currentTest).add(message);
	}

	private void log(MethodCall call) {
        if (silent.get())
            return;
		if (!invocations.containsKey(currentTest))
			invocations.put(currentTest, new ArrayList<>());
		invocations.get(currentTest).add(call);
	}

    /**
     * Executes a task which returns a value without logging any result messages or throwing any exceptions.
     * @param block Task to execute.
     * @return Task result.
     * @param <T> The result type of the task.
     */
    protected <T> Optional<T> quietly(Callable<T> block) {
        silent.set(true);
        Optional<T> result = Optional.empty();
        try {
            result = Optional.of(block.call());
        } catch (Throwable ignored) { }
        silent.set(false);
        return result;
    }

    /**
     * Executes a task without logging any result messages or throwing any exceptions.
     * @param block Task to execute.
     */
    protected void quietly(ThrowingRunnable block) {
        silent.set(true);
        try { block.run(); } catch (Exception ignored) { }
        silent.set(false);
    }

	protected ObjectInstantiation instantiate(Class<?> type, Class<?>[] parameterTypes, Object... initArgs) throws ManualFailureException {
		try {
			if (type == null)
				fail();
            Constructor<?> constructor = Objects.requireNonNull(type).getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true); // Access private constructors through Reflection magic
			return new ObjectInstantiation(constructor, initArgs);
		} catch (NoSuchMethodException e) {
			log(new ConstructorNotImplementedError(currentTest, type, parameterTypes));
			fail();
		} catch (InaccessibleObjectException | SecurityException e) {
			log(Result.unexpectedException(currentTest, e));
			fail();
		}
		return null;
	}

    /**
     * Returns the JavaParser representation of a Java source code file.
     * @param javaFile The name of the .java file (including the extension).
     * @return The JavaParser AST for the source code file.
     */
    protected CompilationUnit getCompilationUnit(String javaFile) {
        if (!compilationUnits.containsKey(javaFile)) {
            SourceLookup.Result match = SourceLookup.lookup(submission.getDirectory(), javaFile, fileNameSimilarityThreshold);
            if (match.getCompilationUnit().isPresent())
                compilationUnits.put(javaFile, match.getCompilationUnit().get());
            else if (match.get().isPresent()) {
                try {
                    compilationUnits.put(javaFile, StaticJavaParser.parse(match.get().get()));
                } catch (FileNotFoundException ignored) { }
            }
        }
        return compilationUnits.get(javaFile);
    }

	/**
	 * Gets the compiled class from a .java file. Stores already-compiled files for re-utilisation to avoid compiling
	 * and loading the same class more than once.
	 * @param javaFile The name of the .java file (including the extension).
	 * @return The compiled class stored in the specified .java file.
	 */
	protected Class<?> getClass(String javaFile) {
        if (!compiledTypes.containsKey(javaFile) && !invalidClassNames.contains(javaFile)) {
            SourceLookup.Result match = SourceLookup.lookup(submission.getDirectory(), javaFile, fileNameSimilarityThreshold);

            if (match.getCompilationUnit().isPresent())
                compilationUnits.putIfAbsent(javaFile, match.getCompilationUnit().get());

            if (match.get().isEmpty()) {
                invalidClassNames.add(javaFile);
                log(new MissingFileError(null, submission.getDirectory(), javaFile));
                return null;
            }
            File source = match.get().get();

            if (match instanceof SourceLookup.FoundWithSimilarName similar)
                log(new IncorrectFileNameError(null, javaFile, similar.actual()));
            else if (match instanceof SourceLookup.FoundWithFileAndClassNameMismatch mismatch)
                log(new FileAndClassNameMismatchError(
                    null,
                    javaFile,
                    mismatch.actual(),
                    mismatch.clazz(),
                    mismatch.isCorrectFileName() ? FileAndClassNameMismatchError.Mismatch.RightFileNameWrongClassName : FileAndClassNameMismatchError.Mismatch.WrongFileNameRightClassName
                ));

            // Rename File
            File fixed = Path.of(source.getParentFile().getPath(), javaFile).toFile();
            if (!source.getName().equals(javaFile)) {
                if (source.renameTo(fixed))
                    source = fixed;
                else
                    Console.warning("Failed to rename file " + source.getPath() + ", which does not match " + javaFile);
            }

            // Rename Class
            String originalClassName = Source.findFirstPublicTypeName(source);
            if (!Objects.equals(originalClassName, FilenameUtils.getBaseName(javaFile))) {
                try {
                    java.nio.file.Path renamed = Source.renameType(source, originalClassName, FilenameUtils.getBaseName(javaFile));
                    if (renamed != null) {
                        source = renamed.toFile();
                        CompilationUnit unit = tryOrElse(() -> StaticJavaParser.parse(renamed), null);
                        if (unit != null)
                            compilationUnits.putIfAbsent(javaFile, unit);
                    }
                } catch (ParseProblemException | IOException ignored) { }
            }

            try {
                String[] allowedPackages = AllowedPackages.DEFAULT;
                if (this.getClass().isAnnotationPresent(AllowedPackages.class)) {
                    String[] allowed = this.getClass().getAnnotation(AllowedPackages.class).value();
                    allowedPackages = Extensions.concat(allowedPackages, allowed);
                }

                Class<?> loaded = ClassLoader.load(source, allowedPackages);
                if (loaded == null) {
                    invalidClassNames.add(javaFile);
                    return null;
                }
                compiledTypes.put(javaFile, loaded);
                return loaded;
            } catch (ClassLoadingException ex) {
                log(new ClassLoadingError(null, source, ex));
            } catch (CompilationException ex) {
                log(new CompilationError(null, source, ex));
            } catch (IOException ignored) { }

            invalidClassNames.add(javaFile);
            return null;
        }
        return compiledTypes.get(javaFile);
	}

    protected boolean containsValidClassFile(String name) {
        return tryOrElse(() -> quietly(() -> getClass(name) != null).isPresent(), false);
    }

	/**
	 * Finds a method in a given class. Case-insensitive.
	 * @param type The method's declaring class.
     * @param returnType The method's return type.
	 * @param name The method name to find.
	 * @param parameterTypes The method's parameter types.
	 * @return The first method found which matches the provided signature.
	 * @throws NoSuchMethodException If no matching method is found.
	 */
	protected Method findMethod(Class<?> type, Class<?> returnType, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
		Levenshtein lev = new Levenshtein();
		for (Method method : type.getDeclaredMethods()) {
			boolean nameIsSimilar = method.getName().equals(name) || lev.similarity(method.getName(), name) >= fileNameSimilarityThreshold;
            boolean compatibleRetType = method.getReturnType() == returnType || returnType.isAssignableFrom(method.getReturnType());
			if (Arrays.equals(method.getParameterTypes(), parameterTypes) && nameIsSimilar && compatibleRetType) {
				if (!method.getName().equals(name))
					log(new IncorrectMethodNameError(currentTest, type, name, method));
                return method;
			}
		}

        String[] paramTypeNames = new String[parameterTypes.length];
        for (int i = 0; i < paramTypeNames.length; i++)
            paramTypeNames[i] = parameterTypes[i].getSimpleName();

		throw new NoSuchMethodException(returnType.getSimpleName() + " " + type.getSimpleName() + "." + name + Arrays.toString(paramTypeNames).replace('[', '(').replace(']', ')'));
	}

    /**
     * Finds a method in a given object's class. Case-insensitive.
     * @param caller An object of the same type as the method's declaring type.
     * @param returnType The method's return type.
     * @param name The method name to find.
     * @param parameterTypes The method's parameter types.
     * @return The first method found which matches the provided signature.
     * @throws NoSuchMethodException If no matching method is found.
     */
    protected Method findMethod(Object caller, Class<?> returnType, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
       return findMethod(caller.getClass(), returnType, name, parameterTypes);
    }

	/**
	 * Invokes a method on a given object and returns the result. Logs the call and any thrown exceptions.
	 * @param method The method to invoke.
	 * @param object The object to invoke the method on.
	 * @param args The arguments to pass to the method call.
	 */
	protected MethodCall invoke(Method method, Object object, Object... args) {
		return new MethodCall(method, object, args, usePreviousCallHistoryGlobal);
	}

    /**
     * Invokes a method on a given object and returns the result. Logs the call and any thrown exceptions.
     * <p>Unlike {@link #invoke(Method, Object, Object[])}, log messages resulting from this call take into account
     * the sequence of previous method calls.</p>
     *
     * @param method The method to invoke.
     * @param object The object to invoke the method on.
     * @param args The arguments to pass to the method call.
     */
    protected MethodCall invokeStateful(Method method, Object object, Object... args) {
        return new MethodCall(method, object, args, true);
    }

    /**
     * Prepares a method for asymptotic runtime complexity analysis.
     * @param description Textual description of what will be benchmarked.
     * @param caller Function returning the method's calling object for a given input size N.
     * @param action Action which will be benchmarked. This action takes two inputs: the method's calling object for
     *               a given input size N, and that number N.
     */
    protected AsymptoticRuntime asymptotic(String description, ThrowingFunction<Long, Object> caller, ThrowingBiConsumer<Object, Long> action) {
        return new AsymptoticRuntime(description, caller, action);
    }

    /**
     * Prepares a class for property inspection.
     * @param type The target type.
     */
    protected <T> ClassInspector<T> inspect(@NonNull Class<T> type) {
        return new ClassInspector<>(type);
    }

    /**
     * Prepares an object instance for inspection.
     * @param object The target object.
     */
    protected <T> ObjectInspector<T> inspect(@NonNull T object) {
        return new ObjectInspector<>(object);
    }

	/**
	 * Runs all tests in a submission testing class.
	 */
	public void runAllTests() throws IOException {
		// Include necessary external files
		Include include = this.getClass().getAnnotation(Include.class);
		if (include != null) {
			for (String path : include.value()) {
				File file = new File(path);
				Path dest = Path.of(submission.getPath(), file.getName());
				if (file.exists())
					java.nio.file.Files.copy(file.toPath(), dest, StandardCopyOption.REPLACE_EXISTING);
				else
					System.err.println("[" + submission.getName() + "] Could not include file: " + path + ". It does not exist!");
			}
		}

		// Precompile required files if necessary
		PrecompileIfPresent precompile = this.getClass().getAnnotation(PrecompileIfPresent.class);
		if (precompile != null) {
			for (String precomp : precompile.value()) {
				if (Files.findDescendant(submission.getDirectory(), precomp) != null)
					quietly(() -> getClass(precomp));
			}
		}

		// Run all BeforeAll method before running test methods
		invokeAll(getAnnotatedMethods(this.getClass(), BeforeAll.class), this);

		for (Method test : getAnnotatedMethods(this.getClass(), Test.class)) {
			// Invoke all BeforeEach methods before each test method
			invokeAll(getAnnotatedMethods(this.getClass(), BeforeEach.class), this);

			// Run the test method and collect results
			currentTest = test.getAnnotation(Test.class);
			invocations.putIfAbsent(currentTest, new ArrayList<>());
			results.putIfAbsent(currentTest, new HashSet<>());

			// Compile required classes beforehand
			Require required = test.getAnnotation(Require.class);
			boolean isAnyClassInvalid = false;
			if (required != null) {
				for (String req : required.value())
					isAnyClassInvalid = getClass(req) == null;
			}
			if (isAnyClassInvalid)
				continue;

			try {
				test.invoke(this);
			} catch (InvocationTargetException e) {
				Throwable target = e.getTargetException();
                switch (target) {
                    case ManualFailureException ex -> {
                        if (ex.getMessage() != null)
                            log(Result.failedRequirement(currentTest, ex.getMessage()));
                        else
                            log(Result.failure(currentTest));
                    }
                    case NoSuchMethodException ex -> log(new MethodNotImplementedError(currentTest, ex));
                    case NoSuchFieldException ex -> log(new NoSuchFieldError(currentTest, ex));
                    case NoClassDefFoundError ex -> log(new ReferencedClassNotFoundError(currentTest, ex));
                    case null, default -> log(Result.unexpectedException(currentTest, target));
                }
			} catch (Throwable ignored) { }
		}
	}

	/**
	 * Signals that a test has failed.
	 * @param message The error message.
	 */
	protected void fail(String message) throws ManualFailureException {
        if (!silent.get())
		    throw new ManualFailureException(message);
	}

	/**
	 * Signals that a test has failed.
	 */
	protected void fail() throws ManualFailureException {
        if (!silent.get())
		    throw new ManualFailureException(null);
	}

    /**
     * Issues a warning with a given message (equivalent to a non-test terminating failure message).
     * @param message Warning message.
     * @param global True if the warning is not related to a particular test; False, otherwise.
     */
    protected void warn(String message, boolean global) {
        if (!silent.get())
            log(Result.warning(global ? null : currentTest, message));
    }

    /**
     * Issues a warning with a given message (equivalent to a non-test terminating failure message).
     * @param message Warning message.
     */
    protected void warn(String message) {
        warn(message, true);
    }

    /**
     * Signals that a test has succeeded.
     */
    protected void ok() {
        if (!silent.get())
            log(Result.success(currentTest));
    }

	protected void assertTrue(boolean condition, String failMessage) throws ManualFailureException {
		if (condition) ok();
		else fail(failMessage);
	}

	protected void assertTrue(boolean condition) throws ManualFailureException {
		assertTrue(condition, null);
	}

	protected void assertFalse(boolean condition, String failMessage) throws ManualFailureException {
		assertTrue(!condition, failMessage);
	}

	protected void assertFalse(boolean condition) throws ManualFailureException {
		assertFalse(condition, null);
	}

    protected boolean isIterableClass(String name) {
        return implementsInterface(getClass(name), Iterable.class);
    }

    protected boolean isGenericClass(String name) {
        return isGeneric(getClass(name));
    }

    @SuppressWarnings("unchecked")
    protected <T> Optional<Iterator<T>> iterator(Object list) throws Exception {
        return quietly(() -> {
            Class<?> type = list.getClass();
            Method iterator = findMethod(type, Iterator.class, "iterator");
            Object result = invoke(iterator, list).result;
            return tryOrElse(() -> (Iterator<T>) result, null);
        });
    }

	private int getPassed(Test test) {
		return Extensions.countIf(results.get(test), Result::passed);
	}

	public double maxGrade() {
		double grade = 0.0;
		for (Test test : results.keySet()) {
			grade += test.weight();
		}
		return grade;
	}

	public double grade() {
		double grade = 0.0;
		for (Test test : results.keySet()) {
			Set<Result> res = results.get(test);
			int correct = Extensions.countIf(res, t -> t.passed() && !t.isWarning());
			int total = Extensions.countIf(res, t -> !t.isWarning());
			if (total > 0) {
                if (test == null)
                    Console.warning("Cannot compute grade for null test with results: " + Extensions.joinToString("; ", res));
                else {
                    if (correct == 0 || res.stream().anyMatch(Result::isFailure))
                        grade = Math.max(0.0, grade - test.penalty());
                    else grade += ((double) correct / total) * test.weight();
                }
			}
		}
		if (Double.isNaN(grade))
			return 0.0;
		return grade;
	}

    public static class ManualFailureException extends Exception {
        public ManualFailureException(String message) {
            super(message);
        }
    }

    public interface SideEffectChecker {
        String message(boolean success);
        boolean check() throws Exception;
    }

    protected abstract class DoublePredicate<T> {

        protected abstract String onFailMessage();

        public abstract boolean test(T expected, Object actual) throws ManualFailureException;

        public boolean assertTrue(T expected, Object actual) throws ManualFailureException {
            return assertTrue(expected, actual, false);
        }

        public boolean assertTrueOrFail(T expected, Object actual) throws ManualFailureException {
            return assertTrue(expected, actual, true);
        }

        private boolean assertTrue(T expected, Object actual, boolean fail) throws ManualFailureException {
            boolean result = this.test(expected, actual);
            if (result)
                ok();
            else if (fail)
                log(Result.failedRequirement(currentTest, onFailMessage()));
            return result;
        }
    }

    public class ObjectInstantiation {

        private final Test test;
        private final Constructor<?> constructor;
        private final Object[] initArgs;

        private ObjectInstantiation(Constructor<?> constructor, Object[] initArgs) {
            this.test = currentTest;
            this.constructor = constructor;

            this.initArgs = new Object[initArgs.length];
            for (int i = 0; i < initArgs.length; i++)
                this.initArgs[i] = Extensions.clone(initArgs[i]);
        }

        public Object getOrThrow() throws ExecutionException, InterruptedException, TimeoutException {
            return getInstance(constructor, initArgs);
        }

        public Object getOrFail() throws ManualFailureException {
            try {
                return getInstance(constructor, initArgs);
            }
            catch (TimeoutException e) {
                log(new ObjectInstantiationError(test, constructor.getDeclaringClass(), initArgs, e));
                fail();
                return null;
            }
            catch (InterruptedException ignored) {  }
            catch (Throwable e) {
                Throwable error = e;
                if (e instanceof ExecutionException) error = e.getCause();

                log(new ObjectInstantiationError(test, constructor.getDeclaringClass(), initArgs, error));
                fail();
                return null;
            }
            return null;
        }

        public <T extends Throwable> void assertThrows(Class<T> exception) throws ManualFailureException {
            Throwable thrown = null;
            try {
                getInstance(constructor, initArgs);
            } catch (Throwable e) {
                if (e instanceof ExecutionException) thrown = e.getCause();
                else thrown = e;
            }
            if (thrown == null || !exception.isAssignableFrom(thrown.getClass()))
                log(new ConstructorMissingExceptionError<>(test, this, exception, null));
        }

        @Override
        public String toString() {
            return "new " + constructor.getDeclaringClass().getSimpleName() + "(" + Extensions.joinToString(initArgs) + ")";
        }
    }

    public class MethodCall {

        private final Test test;
        private final Method method;
        private final Object caller;
        private final Object[] arguments;
        private Object result = NONE;
        private Throwable exception = null;
        private final boolean includeMethodCallHistory;

        private final List<MethodCall> previous;
        private final long timestamp;

        private MethodCall(Method method, Object caller, Object[] arguments, boolean includeMethodCallHistory) {
            this.test = currentTest;
            this.method = method;

            this.includeMethodCallHistory = includeMethodCallHistory;
            this.timestamp = System.nanoTime();

            if (includeMethodCallHistory)
                this.previous = invocations.get(test).stream().filter(call -> call.wasBefore(this)).toList();
            else
                this.previous = Collections.emptyList();

            this.caller = Extensions.clone(caller);
            this.arguments = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++)
                this.arguments[i] = Extensions.clone(arguments[i]);

            try {
                this.result = Extensions.clone(getInvocationResult(method, caller, arguments));
            } catch (ExecutionException ex) {
                this.exception = ex.getCause();
            } catch (Throwable ex) {
                this.exception = ex;
            }
        }

        private boolean wasBefore(MethodCall other) {
            return this.timestamp < other.timestamp;
        }

        public boolean isSuccess() {
            return !Objects.equals(result, NONE) && exception == null;
        }

        public boolean threwException() {
            return exception != null;
        }

        public Optional<Throwable> getException() {
            return Optional.of(exception);
        }

        public boolean isStateful() {
            return includeMethodCallHistory && !previous.isEmpty();
        }

        public List<MethodCall> getPreviousCalls() {
            return previous;
        }

        public Object getOrFail() throws ManualFailureException {
            log(this);
            if (!Objects.equals(result, NONE))
                return result;
            fail();
            return null;
        }

        public Object getOrFail(String message) throws ManualFailureException {
            log(this);
            if (!Objects.equals(result, NONE))
                return result;
            fail(message);
            return null;
        }

        public void assertTrue() throws ManualFailureException {
            log(this);
            if (Objects.equals(result, true)) ok();
            else fail();
        }

        public void assertTrue(String message) throws ManualFailureException {
            log(this);
            if (Objects.equals(result, true)) ok();
            else fail(message);
        }

        public void assertFalse() throws ManualFailureException {
            log(this);
            if (Objects.equals(result, false)) ok();
            else fail();
        }

        public void assertFalse(String message) throws ManualFailureException {
            log(this);
            if (Objects.equals(result, false)) ok();
            else fail(message);
        }

        public <T extends Throwable> void assertThrows(Class<T> type) throws ManualFailureException {
            assertThrows(type, false);
        }

        public <T extends Throwable> void assertThrowsOrFail(Class<T> type) throws ManualFailureException {
            assertThrows(type, true);
        }

        private <T extends Throwable> void assertThrows(Class<T> type, boolean fail) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                log(new MethodMissingExceptionError<>(test, this, type, result));
                if (fail) fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail) fail();
            } else if (threwException()) {
                Result res = new MethodInvocationException<>(test, this, type, exception.getClass());
                log(res);
                if (fail && !res.passed())
                    fail();
            }
        }

        public Optional<Throwable> assertDoesNotThrow() throws ManualFailureException {
            return assertDoesNotThrow(false);
        }

        public Optional<Throwable> assertDoesNotThrowOrFail() throws ManualFailureException {
            return assertDoesNotThrow(true);
        }

        private Optional<Throwable> assertDoesNotThrow(boolean fail) throws ManualFailureException {
            log(this);
            if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail) fail();
                return Optional.of(exception);
            } else if (threwException()) {
                log(new AssertDoesNotThrowFailedError(test, this, exception));
                if (fail) fail();
                return Optional.of(exception);
            } else {
                log(Result.success(test));
                return Optional.empty();
            }
        }

        public boolean assertProducesSideEffect(SideEffectChecker checker) throws ManualFailureException {
            return assertProducesSideEffect(checker, false);
        }

        public boolean assertProducesSideEffectOrFail(SideEffectChecker checker) throws ManualFailureException {
            return assertProducesSideEffect(checker, true);
        }

        private boolean assertProducesSideEffect(SideEffectChecker checker, boolean fail) throws ManualFailureException {
            log(this);
            Result res = new MethodInvocationSideEffect(test, this, checker);
            log(res);
            if (fail && !res.passed())
                fail();
            return res.passed();
        }

        public Object assertNotNull() throws ManualFailureException {
            return assertNotNull(false);
        }

        public Object assertNotNullOrFail() throws ManualFailureException {
            return assertNotNull(true);
        }

        private Object assertNotNull(boolean fail) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(test, this, null, result, AssertEqualsType.NOTNULL);
                log(res);
                if (fail && !res.passed())
                    fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail)
                    fail();
            } else if (threwException()) {
                log(new UnexpectedMethodCallExceptionError(test, this, null, exception, AssertEqualsType.NOTNULL));
                if (fail)
                    fail();
            }
            return result;
        }

        public <T> T assertIsInstance(Class<T> type) throws ManualFailureException {
            return assertIsInstance(type, false);
        }

        public <T> T assertIsInstanceOrFail(Class<T> type) throws ManualFailureException {
            return assertIsInstance(type, true);
        }

        private <T> T assertIsInstance(Class<T> type, boolean fail) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(test, this, type, result, AssertEqualsType.TYPEOF);
                log(res);
                if (fail && !res.passed())
                    fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail)
                    fail();
            } else if (threwException()) {
                log(new UnexpectedMethodCallExceptionError(test, this, type, exception, AssertEqualsType.TYPEOF));
                if (fail)
                    fail();
            }
            return type.cast(result);
        }

        public Object assertEquals(Object expected) throws ManualFailureException {
            return assertEquals(expected, false);
        }

        public Object assertEqualsOrFail(Object expected) throws ManualFailureException {
            return assertEquals(expected, true);
        }

        private Object assertEquals(Object expected, boolean fail) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(test, this, expected, result, AssertEqualsType.EXACT);
                log(res);
                if (fail && !res.passed())
                    fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail)
                    fail();
            } else if (threwException()) {
                log(new UnexpectedMethodCallExceptionError(test, this, expected, exception, AssertEqualsType.EXACT));
                if (fail)
                    fail();
            }
            return expected;
        }

        public <T, I extends Iterable<T>> I assertContentEquals(T[] expected) throws ManualFailureException {
            return assertContentEquals(expected, false);
        }

        public <T, I extends Iterable<T>> I assertContentEqualsOrFail(T[] expected) throws ManualFailureException {
            return assertContentEquals(expected, true);
        }

        @SuppressWarnings("unchecked")
        private <T, I extends Iterable<T>> I assertContentEquals(T[] expected, boolean fail) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(test, this, expected, result, AssertEqualsType.CONTENT);
                log(res);
                if (fail && !res.passed())
                    fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail)
                    fail();
            } else if (threwException()) {
                log(new UnexpectedMethodCallExceptionError(test, this, expected, exception, AssertEqualsType.CONTENT));
                if (fail)
                    fail();
            }
            return (I) Arrays.asList(expected);
        }

        public Object assertEqualsAny(Object... expected) throws ManualFailureException {
            return assertEqualsAny(false, expected);
        }

        public Object assertEqualsAnyOrFail(Object... expected) throws ManualFailureException {
            return assertEqualsAny(true, expected);
        }

        private Object assertEqualsAny(boolean fail, Object... expected) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(test, this, expected, result, AssertEqualsType.ANY);
                log(res);
                if (fail && !res.passed())
                    fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail)
                    fail();
            } else if (threwException()) {
                log(new UnexpectedMethodCallExceptionError(test, this, expected, exception, AssertEqualsType.ANY));
                if (fail)
                    fail();
            }
            return expected[0];
        }

        @SafeVarargs
        public final <T> T[] assertIsPermutation(T... expected) throws ManualFailureException {
            return assertIsPermutation(false, expected);
        }

        @SafeVarargs
        public final <T> T[] assertIsPermutationOrFail(T... expected) throws ManualFailureException {
            return assertIsPermutation(true, expected);
        }

        @SafeVarargs
        private final <T> T[] assertIsPermutation(boolean fail, T... expected) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(test, this, expected, result, AssertEqualsType.PERMUTATION);
                log(res);
                if (fail && !res.passed())
                    fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(test, this));
                if (fail)
                    fail();
            } else if (threwException()) {
                log(new UnexpectedMethodCallExceptionError(test, this, expected, exception, AssertEqualsType.PERMUTATION));
                if (fail)
                    fail();
            }
            return expected;
        }

        private String toStringAsync(Object o) {
            try {
                return async(() -> Extensions.toStringOrDefault(o));
            } catch (ExecutionException | InterruptedException | TimeoutException e) {
                return Objects.toIdentityString(o);
            }
        }

        @Override
        public String toString() {
            StringBuilder s = new StringBuilder(method.getName() + "(");
            if (arguments.length > 0) {
                s.append(toStringAsync(arguments[0]).trim());
                for (int i = 1; i < arguments.length; i++)
                    s.append(", ").append(toStringAsync(arguments[i]).trim());
            }
            s.append(")");
            if (caller != null)
                return "(" + toStringAsync(caller).trim() + ")." + s;
            return s.toString();
        }

        public String toStringWithoutCaller() {
            StringBuilder s = new StringBuilder(method.getName() + "(");
            if (arguments.length > 0) {
                s.append(toStringAsync(arguments[0]).trim());
                for (int i = 1; i < arguments.length; i++)
                    s.append(", ").append(toStringAsync(arguments[i]).trim());
            }
            s.append(")");
            return s.toString();
        }

        public String toStringWithHistoryOrDefault() {
            String previous = "";
            if (isStateful())
                previous = " after <%s>".formatted(
                        Extensions.joinToString("; ", getPreviousCalls(), Tester.MethodCall::toStringWithoutCaller)
                );
            return (isStateful() ? toStringWithoutCaller() : toString()) + previous;
        }
    }

    public class AsymptoticRuntime {
        private final ThrowingFunction<Long, Object> caller;
        private final String description;
        private final ThrowingBiConsumer<Object, Long> action;

        private AsymptoticRuntime(String description, ThrowingFunction<Long, Object> caller, ThrowingBiConsumer<Object, Long> action) {
            this.caller = caller;
            this.description = description;
            this.action = action;
        }

        private OrderOfGrowthEstimator.Fit fit(long initial, int steps, int repeats, boolean amortized) throws Exception {
            OrderOfGrowthEstimator<Long, Exception> estimator = new OrderOfGrowthEstimator<>() {
                private Object callingObject;

                @Override
                protected Long input(long n) throws Exception {
                    callingObject = caller.apply(n);
                    return n;
                }

                @Override
                protected void action(Long input)  {
                    try {
                        action.accept(callingObject, input);
                    } catch (Exception ignored) { }
                }

                @Override
                protected long update(long n) {
                    return (long) (1.5 * n);
                }
            };
            OrderOfGrowthEstimator.Fit fit =
                    amortized ? estimator.fitAmortized(initial, steps, repeats)
                            : estimator.fit(initial, steps, repeats);
            System.gc();
            return fit;
        }

        /**
         * Analyses an action's asymptotic time complexity using regression analysis.
         * @param complexity Expected asymptotic runtime order of growth.
         * @param initial Initial input size N.
         * @param steps Number of different input sizes to use.
         * @param repeats How many times each input size will be repeated (higher = more accuracy for each N).
         * @param confidence The expected growth order should be estimated with at least this much confidence.
         */
        public void assertTimeComplexity(OrderOfGrowth complexity, long initial, int steps, int repeats, double confidence) throws Exception {
            OrderOfGrowthEstimator.Fit fit = fit(initial, steps, repeats, false);
            log(new RuntimeComplexityEstimation(currentTest, description, complexity, confidence, fit, RuntimeComplexityEstimation.Comparison.EQUALS, false));
        }

        public void assertTimeComplexityLessThanOrEqual(OrderOfGrowth complexity, long initial, int steps, int repeats, double confidence) throws Exception {
            OrderOfGrowthEstimator.Fit fit = fit(initial, steps, repeats, false);
            log(new RuntimeComplexityEstimation(currentTest, description, complexity, confidence, fit, RuntimeComplexityEstimation.Comparison.LESS_THAN_OR_EQUAL, false));
        }

        public void assertTimeComplexityLessThan(OrderOfGrowth complexity, long initial, int steps, int repeats, double confidence) throws Exception {
            OrderOfGrowthEstimator.Fit fit = fit(initial, steps, repeats, false);
            log(new RuntimeComplexityEstimation(currentTest, description, complexity, confidence, fit, RuntimeComplexityEstimation.Comparison.LESS_THAN, false));
        }

        public void assertAmortizedTimeComplexity(OrderOfGrowth complexity, long initial, int steps, int repeats, double confidence) throws Exception {
            OrderOfGrowthEstimator.Fit fit = fit(initial, steps, repeats, true);
            log(new RuntimeComplexityEstimation(currentTest, description, complexity, confidence, fit,  RuntimeComplexityEstimation.Comparison.EQUALS, true));
        }
    }

    public class ClassInspector<T> {
        private final Class<T> type;

        public ClassInspector(Class<T> type) {
            if (type == null)
                throw new IllegalArgumentException("");
            this.type = type;
        }

        public Class<T> target() {
            return type;
        }

        public FieldAccess field(Class<?> fieldType, String name) {
            return new FieldAccess(fieldType, name);
        }

        public Class<?> assertHasNestedClass(String name) throws ManualFailureException {
            return assertHasNestedClass(name, false);
        }

        public Class<?> assertHasNestedClassOrFail(String name) throws ManualFailureException {
            return assertHasNestedClass(name, true);
        }

        private Class<?> assertHasNestedClass(String name, boolean fail) throws ManualFailureException {
            Class<?> nested = null;
            ClassNotFoundException exception = null;

            try {
                nested = Tester.this.getNestedClass(type, name);
            } catch (ClassNotFoundException e) {
                exception = e;
            }

            GetNestedClassResult result = new GetNestedClassResult(currentTest, type, name, nested, exception);
            log(result);
            if (fail && !result.passed())
                fail();

            return nested;
        }

        private SymbolicFunction assertMemoryEquals(SymbolicFunction expected, boolean fail) throws ManualFailureException {
            throw new UnsupportedOperationException("Not yet implemented!"); // TODO
        }

        public class FieldAccess {
            private final Class<?> fieldType;
            private final String fieldName;

            private final Field field;
            private final NoSuchFieldException exception;

            private FieldAccess(Class<?> fieldType, String fieldName) {
                if (fieldType == null || fieldName == null)
                    throw new IllegalArgumentException("");

                this.fieldType = fieldType;
                this.fieldName = fieldName;

                Field target = null;
                NoSuchFieldException error = null;
                try {
                    target = Tester.this.getField(type, fieldType, fieldName);
                } catch (NoSuchFieldException e) {
                    error = e;
                }

                field = target;
                exception = error;
            }

            private boolean fieldExists() {
                return field != null && exception == null;
            }

            private void noSuchField(boolean fail) throws ManualFailureException {
                String description = "%s %s.%s".formatted(fieldType.getSimpleName(), type.getSimpleName(), fieldName);
                NoSuchFieldError result = new NoSuchFieldError(currentTest, new NoSuchFieldException(description));
                log(result);
                if (fail) fail();
            }

            public boolean assertExists() throws ManualFailureException {
                return assertExists(false);
            }

            public boolean assertExistsOrFail() throws ManualFailureException {
                return assertExists(true);
            }

            private boolean assertExists(boolean fail) throws ManualFailureException {
                if (fieldExists()) ok();
                else noSuchField(fail);
                return fieldExists();
            }

            public <R> R assertStaticEquals(R expected) throws ManualFailureException {
                return assertStaticEquals(expected, false);
            }

            public <R> R assertStaticEqualsOrFail(R expected) throws ManualFailureException {
                return assertStaticEquals(expected, true);
            }

            private <R> R assertStaticEquals(R expected, boolean fail) throws ManualFailureException {
                if (fieldExists()) {
                    ClassGetStaticResult<R> result = new ClassGetStaticResult<>(currentTest, type, field, expected, AssertEqualsType.EXACT);
                    log(result);
                    if (fail && !result.passed())
                        fail();
                } else {
                    noSuchField(fail);
                }
                return expected;
            }
        }

        public class MethodAccess {
            private final String methodName;
            private final Class<?> methodReturnType;
            private final Class<?>[] methodParameterTypes;

            private final Method method;
            private final NoSuchMethodException exception;

            private MethodAccess(String methodName, Class<?> methodReturnType, Class<?>[] methodParameterTypes) {
                this.methodName = methodName;
                this.methodReturnType = methodReturnType;
                this.methodParameterTypes = methodParameterTypes;

                Method target = null;
                NoSuchMethodException error = null;
                try {
                    target = findMethod(type, methodReturnType, methodName, methodParameterTypes);
                } catch (NoSuchMethodException e) {
                    error = e;
                }
                this.method = target;
                this.exception = error;
            }

            private void noSuchMethod(boolean fail) throws ManualFailureException {
                MethodNotImplementedError result = new MethodNotImplementedError(currentTest, exception);
                log(result);
                if (fail) fail();
            }

            private boolean methodExists() {
                return method != null && exception == null;
            }

            public boolean assertExists() throws ManualFailureException {
                return assertExists(false);
            }

            public boolean assertExistsOrFail() throws ManualFailureException {
                return assertExists(true);
            }

            private boolean assertExists(boolean fail) throws ManualFailureException {
                if (methodExists()) ok();
                else noSuchMethod(fail);
                return methodExists();
            }

            public boolean assertHasModifier(int modifier) throws ManualFailureException {
                return assertHasModifier(modifier, false);
            }

            public boolean assertHasModifierOrFail(int modifier) throws ManualFailureException {
                return assertHasModifier(modifier, true);
            }

            private boolean assertHasModifier(int modifier, boolean fail) throws ManualFailureException {
                if (methodExists()) {
                    MethodCheckModifierResult result = new MethodCheckModifierResult(currentTest, method, modifier);
                    log(result);
                    if (fail && !result.passed())
                        fail();
                    return result.passed();
                } else noSuchMethod(fail);
                return false;
            }
        }
    }

    public class ObjectInspector<T> {
        private final T object;

        public ObjectInspector(T object) {
            this.object = object;
        }

        public T target() {
            return object;
        }

        public <R> PropertyAccess<R> property(Class<R> propertyType, String name) {
            return new PropertyAccess<>(propertyType, name);
        }

        public class PropertyAccess<R> {

            private final Class<R> fieldType;
            private final String fieldName;

            private final R value;
            private final Exception exception;

            public PropertyAccess(Class<R> fieldType, String fieldName) {
                this.fieldType = fieldType;
                this.fieldName = fieldName;

                R result = null;
                Exception error = null;
                try {
                    result = Tester.this.getProperty(object, fieldType, fieldName);
                } catch (Exception e) {
                    error = e;
                }

                value = result;
                exception = error;
            }

            private boolean isSuccess() {
                return exception == null;
            }

            public R get() {
                return value;
            }

            public R getOrFail() throws ManualFailureException {
                if (isSuccess())
                    return value;
                fail();
                return null;
            }

            public R assertEquals(R expected) throws ManualFailureException {
                return assertEquals(expected, false);
            }

            public R assertEqualsOrFail(R expected) throws ManualFailureException {
                return assertEquals(expected, true);
            }

            public R assertEquals(R expected, boolean fail) throws ManualFailureException {
                if (isSuccess()) {
                    Result res = new ObjectGetPropertyResult<>(currentTest, object, fieldType, fieldName, expected, value, AssertEqualsType.EXACT);
                    log(res);
                    if (fail && !res.passed())
                        fail();
                } else {
                    log(new UnexpectedObjectPropertyExceptionError<>(currentTest, object, fieldType, fieldName, expected, exception, AssertEqualsType.EXACT));
                    if (fail)
                        fail();
                }
                return expected;
            }
        }
    }
}
