package evaluator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import evaluator.algorithmic.OrderOfGrowth;
import evaluator.algorithmic.OrderOfGrowthEstimator;
import evaluator.annotations.*;
import evaluator.messages.*;
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
import reflection.Reflector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InaccessibleObjectException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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

	public static class ManualFailureException extends Exception {
		public ManualFailureException(String message) {
			super(message);
		}
	}

	public interface SideEffectChecker {
		String message(boolean success);
		boolean check() throws Exception;
	}

	public class ObjectInstantiation {

		private final Constructor<?> constructor;
		private final Object[] initArgs;

        private ObjectInstantiation(Constructor<?> constructor, Object[] initArgs) {
			this.constructor = constructor;
			this.initArgs = initArgs;
		}

		public Object getOrThrow() throws ExecutionException, InterruptedException, TimeoutException {
			return getInstance(constructor, initArgs);
		}

		public Object getOrFail() throws ManualFailureException {
			try {
				return getInstance(constructor, initArgs);
			}
			catch (TimeoutException e) {
				log(new ObjectInstantiationError(currentTest, constructor.getDeclaringClass(), initArgs, e));
				fail();
				return null;
			}
			catch (InterruptedException ignored) {  }
			catch (Throwable e) {
				Throwable error = e;
				if (e instanceof ExecutionException) error = e.getCause();

				log(new ObjectInstantiationError(currentTest, constructor.getDeclaringClass(), initArgs, error));
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
				log(new ConstructorMissingExceptionError<>(currentTest, this, exception, null));
		}

		@Override
		public String toString() {
			return "new " + constructor.getDeclaringClass().getSimpleName() + "(" + Extensions.joinToString(initArgs) + ")";
		}
	}

	public class MethodCall {

		private final Method method;
		private final Object caller;
		private final Object[] arguments;
		private Object result = NONE;
		private Throwable exception = null;
        private final boolean includeMethodCallHistory;

        private final List<MethodCall> previous;
        private final long timestamp;

        private MethodCall(Method method, Object caller, Object[] arguments, boolean includeMethodCallHistory) {
            this.method = method;
            this.caller = caller;

            this.includeMethodCallHistory = includeMethodCallHistory;
            this.timestamp = System.nanoTime();

            if (includeMethodCallHistory)
                this.previous = invocations.get(currentTest).stream().filter(call -> call.wasBefore(this)).toList();
            else
                this.previous = Collections.emptyList();

            this.arguments = new Object[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                Object argument = arguments[i];
                if (argument != null && argument.getClass().isArray())
                    this.arguments[i] = Arrays.copyOf((Object[]) argument, ((Object[]) argument).length);
                else if (argument != null && Iterable.class.isAssignableFrom(argument.getClass()))
                    this.arguments[i] = Extensions.copy((Iterable<?>) argument);
                else
                    this.arguments[i] = argument;
            }

            try {
                Object res = getInvocationResult(method, caller, arguments);
                if (res == null)
                    this.result = null;
                else if (res.getClass().isArray())
                    this.result = Arrays.copyOf((Object[]) res, ((Object[]) res).length);
                else if (Iterable.class.isAssignableFrom(res.getClass()))
                    this.result = Extensions.copy((Iterable<?>) res);
                else
                    this.result = res;
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
			return result != NONE && exception == null;
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
			if (result != NONE)
				return result;
			fail();
			return null;
		}

		public Object getOrFail(String message) throws ManualFailureException {
			log(this);
			if (result != NONE)
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
			log(this);
			if (isSuccess()) {
				log(new MethodMissingExceptionError<>(currentTest, this, type, result));
				fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
                fail();
			} else if (threwException()) {
				Result res = new MethodInvocationException<>(currentTest, this, type, exception.getClass());
				log(res);
                if (!res.passed())
                    fail();
			}
		}

		public Optional<Throwable> assertDoesNotThrow() throws ManualFailureException {
			log(this);
			if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
                fail();
                return Optional.of(exception);
			} else if (threwException()) {
				log(new AssertDoesNotThrowFailedError(currentTest, this, exception));
                fail();
                return Optional.of(exception);
			} else {
                log(Result.success(currentTest, this + " shouldn't have thrown any exceptions, and it didn't! Hooray!"));
                return Optional.empty();
            }
		}

		public boolean assertProducesSideEffect(SideEffectChecker checker) throws ManualFailureException {
			log(this);
			Result res = new MethodInvocationSideEffect(currentTest, this, checker);
			log(res);
            //if (!res.passed())
                //fail();
			return res.passed();
		}

		public Object assertEquals(Object expected) throws ManualFailureException {
			log(this);
			if (isSuccess()) {
				Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.EqualsType.EXACT);
				log(res);
                //if (!res.passed())
                    //fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
                //fail();
			} else if (threwException()) {
				log(new UnexpectedExceptionError(currentTest, this, expected, exception, MethodInvocationResult.EqualsType.EXACT));
                //fail();
			}
			return expected;
		}

        @SuppressWarnings("unchecked")
        public <T, I extends Iterable<T>> I assertContentEquals(T[] expected) throws ManualFailureException {
            log(this);
            if (isSuccess()) {
                Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.EqualsType.CONTENT);
                log(res);
                //if (!res.passed())
                    //fail();
            } else if (exception instanceof TimeoutException) {
                log(new MethodTimeoutError(currentTest, this));
                //fail();
            } else if (threwException()) {
                log(new UnexpectedExceptionError(currentTest, this, expected, exception, MethodInvocationResult.EqualsType.CONTENT));
                //fail();
            }
            return (I) Arrays.asList(expected);
        }

		public Object assertEqualsAny(Object... expected) throws ManualFailureException {
			log(this);
			if (isSuccess()) {
				Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.EqualsType.ANY);
				log(res);
                //if (!res.passed())
                    //fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
                //fail();
			} else if (threwException()) {
				log(new UnexpectedExceptionError(currentTest, this, expected, exception, MethodInvocationResult.EqualsType.ANY));
                //fail();
			}
			return expected[0];
		}

		@SafeVarargs
        public final <T> T[] assertIsPermutation(T... expected) throws ManualFailureException {
			log(this);
			if (isSuccess()) {
				Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.EqualsType.PERMUTATION);
				log(res);
                //if (!res.passed())
                    //fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
                //fail();
			} else if (threwException()) {
				log(new UnexpectedExceptionError(currentTest, this, expected, exception, MethodInvocationResult.EqualsType.PERMUTATION));
                //fail();
			}
			return expected;
		}

		@Override
		public String toString() {
			StringBuilder s = new StringBuilder(method.getName() + "(");
			if (arguments.length > 0) {
				s.append(Extensions.toStringOrDefault(arguments[0]).trim());
				for (int i = 1; i < arguments.length; i++)
					s.append(", ").append(Extensions.toStringOrDefault(arguments[i]).trim());
			}
			s.append(")");
			if (caller != null)
				return "(" + Extensions.toStringOrDefault(caller).trim() + ")." + s;
			return s.toString();
		}

        public String toStringWithoutCaller() {
            StringBuilder s = new StringBuilder(method.getName() + "(");
            if (arguments.length > 0) {
                s.append(Extensions.toStringOrDefault(arguments[0]).trim());
                for (int i = 1; i < arguments.length; i++)
                    s.append(", ").append(Extensions.toStringOrDefault(arguments[i]).trim());
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

	private final Map<Test, List<MethodCall>> invocations = new HashMap<>();
	private final Map<Test, Set<Result>> results = new HashMap<>();
	private final Map<String, Class<?>> compiledTypes = new HashMap<>(); // Only compile class once, reuse if possible
	private final List<String> invalidClassNames = new ArrayList<>(); // If an error is raised, don't try loading again
    private final Map<String, CompilationUnit> compilationUnits = new HashMap<>();
    private Test currentTest;
	private final Submission submission;
    private double fileNameSimilarityThreshold = 0.8;
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
     * Executes a task which returns a value without logging any result messages.
     * @param block Task to execute.
     * @return Task result.
     * @param <T> The result type of the task.
     * @throws Exception If the task throws an exception during execution.
     */
    protected <T> Optional<T> quietly(Callable<T> block) throws Exception {
        silent.set(true);
        Optional<T> result = Optional.empty();
        try {
            result = Optional.of(block.call());
        } catch (Throwable ignored) { }
        silent.set(false);
        return result;
    }

    /**
     * Executes a task without logging any result messages.
     * @param block Task to execute.
     * @throws Exception If the task throws an exception during execution.
     */
    protected void quietly(ThrowingRunnable block) throws Exception {
        silent.set(true);
        block.run();
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
			log(Result.exception(currentTest, e));
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
     * Gets the JavaParser declaration of the primary class from a given file.
     * @param javaFile The name of the .java file (including the extension).
     * @return JavaParser class declaration for the file's primary type.
     */
    protected ClassOrInterfaceDeclaration getSyntaxTree(String javaFile) {
        CompilationUnit unit = getCompilationUnit(javaFile);
        if (unit == null)
            return null;
        for (TypeDeclaration<?> type : unit.getTypes()) {
            if (type.isPublic() && type.isClassOrInterfaceDeclaration())
                return type.asClassOrInterfaceDeclaration();
        }
        return null;
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
                log(new FileAndClassNameMismatchError(null, javaFile, mismatch.actual(), mismatch.clazz()));

            File fixed = Path.of(source.getParentFile().getPath(), javaFile).toFile();
            if (!source.getName().equals(javaFile)) {
                try {
                    java.nio.file.Path renamed = Source.renamePrimaryType(source, FilenameUtils.getBaseName(javaFile));
                    if (renamed != null) {
                        source = renamed.toFile();
                        CompilationUnit unit = tryOrElse(() -> StaticJavaParser.parse(renamed), null);
                        if (unit != null)
                            compilationUnits.putIfAbsent(javaFile, unit);
                    }
                } catch (IOException e) {
                    Console.warning("Failed to rename class of file " + source.getPath() + ", which does not match " + javaFile);
                }
                if (source.renameTo(fixed)) source = fixed;
                else
                    Console.warning("Failed to rename file " + source.getPath() + ", which does not match " + javaFile);
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
            paramTypeNames[i] = parameterTypes[i].getName();

		throw new NoSuchMethodException(returnType.getName() + " " + type.getName() + "." + name + Arrays.toString(paramTypeNames).replace('[', '(').replace(']', ')'));
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
		return new MethodCall(method, object, args, false);
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
	 * Runs all tests in a submission testing class.
	 */
	public void runAllTests() throws Exception {
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

				if (target instanceof ManualFailureException ex && ex.getMessage() != null)
					log(Result.failedRequirement(currentTest, ex.getMessage()));
				else if (target instanceof NoSuchMethodException ex)
					log(new MethodNotImplementedError(currentTest, ex));
				else if (target instanceof NoSuchFieldException ex)
					log(new AttributeNotImplementedError(currentTest, ex));
				else if (target instanceof NoClassDefFoundError ex)
					log(new ReferencedClassNotFoundError(currentTest, ex));
				else if (!(target instanceof ManualFailureException))  {
					log(Result.exception(currentTest, target));
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

    protected void warn(String message) {
        if (!silent.get())
            log(Result.warning(null, message));
    }

    /**
     * Signals that a test has succeeded.
     */
    protected void ok() {
        if (!silent.get())
            log(Result.success(currentTest, null));
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
			int correct = Extensions.countIf(res, Result::passed);
			int total = res.size();
			if (total > 0) {
                if (test == null)
                    Console.warning("Cannot compute grade for null test with results: " + Extensions.joinToString("; ", res));
                else {
                    if (correct == 0 || res.stream().anyMatch(Result::isFailedRequirement))
                        grade = Math.max(0.0, grade - test.penalty());
                    else grade += ((double) correct / total) * test.weight();
                }
			}
		}
		if (Double.isNaN(grade))
			return 0.0;
		return grade;
	}
}
