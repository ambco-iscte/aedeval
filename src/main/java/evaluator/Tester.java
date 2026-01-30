package evaluator;

import com.google.common.collect.Iterables;
import evaluator.annotations.*;
import evaluator.messages.*;
import extensions.Extensions;
import extensions.Files;
import extensions.Levenshtein;
import loading.ClassLoader;
import loading.ClassLoadingException;
import loading.CompilationException;
import reflection.Reflector;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;

import static evaluator.messages.Result.FAILURE_ERROR_CODE;

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
public class Tester extends Reflector {

	public static class ManualFailureException extends Exception {

		public ManualFailureException(String message) {
			super(message);
		}
	}

	public interface SideEffectChecker {
		String message(boolean success);
		boolean check();
	}

	public class ObjectInstantiation {

		private final Constructor<?> constructor;

		private final Object[] initArgs;

		public ObjectInstantiation(Constructor<?> constructor, Object[] initArgs) {
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

			if (thrown == null) {
				log(new ConstructorMissingExceptionError<>(currentTest, this, exception, null));
				fail();
			} else if (!exception.isAssignableFrom(thrown.getClass())) {
				log(new ConstructorMissingExceptionError<>(currentTest, this, exception, thrown));
				fail();
			}
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

		public MethodCall(Method method, Object caller, Object[] arguments) {
			this.method = method;
			this.caller = caller;

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

		public boolean isSuccess() {
			return result != NONE && exception == null;
		}

		public boolean threwException() {
			return exception != null;
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
			if (Objects.equals(result, true))
				log(Result.success(currentTest, null));
			else
				fail();
		}

		public void assertTrue(String message) throws ManualFailureException {
			log(this);
			if (Objects.equals(result, true))
				log(Result.success(currentTest, null));
			else
				fail(message);
		}

		public void assertFalse() throws ManualFailureException {
			log(this);
			if (Objects.equals(result, false))
				log(Result.success(currentTest, null));
			else
				fail();
		}

		public void assertFalse(String message) throws ManualFailureException {
			log(this);
			if (Objects.equals(result, false))
				log(Result.success(currentTest, null));
			else
				fail(message);
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

		public void assertDoesNotThrow() throws ManualFailureException {
			log(this);
			if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
				fail();
			} else if (threwException()) {
				log(new AssertDoesNotThrowFailedError(currentTest, this, exception));
				fail();
			} else
				log(Result.success(currentTest, "Method shouldn't have thrown any exception, and it didn't! Hooray!"));
		}

		public boolean assertProducesSideEffect(SideEffectChecker checker) throws ManualFailureException {
			log(this);
			Result res = new MethodInvocationSideEffect(currentTest, this, checker, checker.check());
			log(res);
			if (!res.passed())
				fail();
			return res.passed();
		}

		public Object assertEquals(Object expected) throws ManualFailureException {
			log(this);
			if (isSuccess()) {
				Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.ExpectedType.EXACT);
				log(res);
				if (!res.passed())
					fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
				fail();
			} else if (threwException()) {
				log(new UnexpectedExceptionError(currentTest, this, expected, exception, false));
				fail();
			}
			return expected;
		}

		public Object assertEqualsAny(Object... expected) throws ManualFailureException {
			log(this);
			if (isSuccess()) {
				Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.ExpectedType.ANY);
				log(res);
				if (!res.passed())
					fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
				fail();
			} else if (threwException()) {
				log(new UnexpectedExceptionError(currentTest, this, expected, exception, true));
				fail();
			}
			return expected[0];
		}

		public <T> T[] assertIsPermutation(T... expected) throws ManualFailureException {
			log(this);
			if (isSuccess()) {
				Result res = new MethodInvocationResult(currentTest, this, expected, result, MethodInvocationResult.ExpectedType.PERMUTATION);
				log(res);
				if (!res.passed())
					fail();
			} else if (exception instanceof TimeoutException) {
				log(new MethodTimeoutError(currentTest, this));
				fail();
			} else if (threwException()) {
				log(new UnexpectedExceptionError(currentTest, this, expected, exception, true));
				fail();
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
	}

	private final Map<Test, List<MethodCall>> invocations = new HashMap<>();

	private final Map<Test, List<Result>> results = new HashMap<>();

	private final Map<String, String> fileNames = new HashMap<>();

	private final Map<String, Class<?>> compiledTypes = new HashMap<>(); // Only compile class once, reuse if possible

	private final List<String> invalidClassNames = new ArrayList<>(); // If an error is raised, don't try loading again

	private Test currentTest;

	private final Submission submission;

	/**
	 * Creates an instance of a tester for a directory containing Java source code files.
	 * @param submission Submission to be tested.
	 */
	public Tester(Submission submission) {
		this.submission = submission;
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

	public Map<Test, List<Result>> getResults() {
		return results;
	}

	public Submission getSubmission() {
		return submission;
	}

	protected void log(Result message) {
		if (!results.containsKey(currentTest))
			results.put(currentTest, new ArrayList<>());
		results.get(currentTest).add(message);
	}

	private void log(MethodCall call) {
		if (!invocations.containsKey(currentTest))
			invocations.put(currentTest, new ArrayList<>());
		invocations.get(currentTest).add(call);
	}

	protected ObjectInstantiation instantiate(Class<?> type, Class<?>[] parameterTypes, Object... initArgs) throws ManualFailureException {
		try {
			if (type == null)
				fail();
            assert type != null;
            Constructor<?> constructor = type.getDeclaredConstructor(parameterTypes);
			constructor.setAccessible(true); // Access private constructors through Reflection magic
			return new ObjectInstantiation(constructor, initArgs);
		} catch (NoSuchMethodException e) {
			log(new ConstructorNotImplementedError(currentTest, type, parameterTypes));
			fail();
		} catch (SecurityException e) {
			log(Result.error(currentTest, e));
			fail();
		}
		return null;
	}

	/**
	 * Gets the compiled class from a .java file. Stores already-compiled files for re-utilisation to avoid compiling
	 * and loading the same class more than once.
	 * @param javaFile The .java file.
	 * @return The compiled class stored in the specified .java file.
	 */
	protected Class<?> getClass(String javaFile) {
		javaFile = fileNames.getOrDefault(javaFile, javaFile);

		if (!invalidClassNames.contains(javaFile) && !compiledTypes.containsKey(javaFile)) {
			File source = Files.findClosestDescendant(submission.getDirectory(), javaFile);

			if (source == null || !source.exists()) {
				invalidClassNames.add(javaFile);
				log(new MissingFileError(null, submission.getDirectory(), javaFile));
				return null;
			}

			fileNames.putIfAbsent(javaFile, source.getName());

			if (!source.getName().equals(javaFile)) {
				log(new IncorrectFileNameError(null, javaFile, source.getName()));
			}

			try {
				Class<?> loaded = ClassLoader.load(source, true);
				if (loaded == null) {
					invalidClassNames.add(javaFile);
					return null;
				}
				compiledTypes.put(javaFile, loaded);
				return loaded;
			}
			catch (ClassLoadingException ex) { log(new ClassLoadingError(null, source, ex)); }
			catch (CompilationException ex) { log(new CompilationError(null, source, ex)); }
			catch (IOException ignored) { }

			invalidClassNames.add(javaFile);
			return null;
		}
		return compiledTypes.get(javaFile);
	}

	/**
	 * Finds a method in a given class. Case-insensitive.
	 * @param type The class.
	 * @param name The method to find.
	 * @param parameterTypes The method's parameter types.
	 * @return The first method found which equals the name, ignoring case.
	 * @throws NoSuchMethodException If no matching method is found.
	 */
	protected Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
		Levenshtein lev = new Levenshtein();
		for (Method method : type.getDeclaredMethods()) {
			boolean nameIsSimilar = method.getName().equals(name) || lev.similar(method.getName(), name, 0.2);
			if (Arrays.equals(method.getParameterTypes(), parameterTypes) && nameIsSimilar) {
				if (!method.getName().equals(name)) {
					//System.err.println("[" + submission.getName() + "] Could not find method " + type.getSimpleName() + "." + name + ", but found close viable match: " + method.getName());
					log(new IncorrectMethodNameError(currentTest, type, name, method.getName()));
				}
				return method;
			}
		}
		throw new NoSuchMethodException(type.getName() + "." + name + Arrays.toString(parameterTypes).replace('[', '(').replace(']', ')'));
	}

	/**
	 * Invokes a method on a given object and returns the result. Logs any method exceptions.
	 * @param method The method to invoke.
	 * @param object The object to invoke the method on.
	 * @param args The arguments to pass to the method call.
	 * @return The return value of the method invocation, as a general Java object.
	 */
	protected MethodCall invoke(Method method, Object object, Object... args) {
		return new MethodCall(method, object, args);
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
				if (Files.findDescendant(submission.getDirectory(), precomp) != null) {
					//System.out.println("[" + submission.getName() + "]: Precompiling additional required class " + precomp);
					getClass(precomp);
				}
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
			results.putIfAbsent(currentTest, new ArrayList<>());

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

				if (target instanceof ManualFailureException ex && ex.getMessage() != null) {
					//System.err.println("[" + submission.getName() + "] " + "Fail: " + ex.getMessage());
					log(Result.failure(currentTest, ex.getMessage()));
				}
				else if (target instanceof NoSuchMethodException ex)
					log(new MethodNotImplementedError(currentTest, ex));
				else if (target instanceof NoSuchFieldException ex)
					log(new AttributeNotImplementedError(currentTest, ex));
				else if (target instanceof NoClassDefFoundError ex)
					log(new ReferencedClassNotFoundError(currentTest, ex));
				else if (!(target instanceof ManualFailureException))  {
					target.printStackTrace();
					log(Result.error(currentTest, target));
				}
			} catch (Throwable e) {
				e.printStackTrace();
			}
		}
	}

	/**
	 * Signals that a test has failed.
	 * @param message The error message.
	 */
	protected void fail(String message) throws ManualFailureException {
		throw new ManualFailureException(message);
	}

	/**
	 * Signals that a test has failed.
	 */
	protected void fail() throws ManualFailureException {
		throw new ManualFailureException(null);
	}

	protected void assertTrue(boolean condition, String failMessage) throws ManualFailureException {
		if (condition)
			log(Result.success(currentTest, null));
		else
			fail(failMessage);
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

	/**
	 * Are any of the objects null?
	 * @param objects A list of objects.
	 * @return True if any of the objects are null; False, otherwise.
	 */
	protected boolean anyNull(Object... objects) {
		for (Object obj : objects) {
			if (obj == null)
				return true;
		}
		return false;
	}

	/**
	 * Runs a callable task.
	 * @param task The task to execute.
	 * @return The result of running the task.
	 */
	protected Object tryGetResult(Callable<?> task) {
		try {
			Object res = task.call();
			return res == null ? NONE : res;
		} catch (Exception e) {
			return NONE;
		}
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
			List<Result> res = results.get(test);
			int correct = Extensions.countIf(res, Result::passed);
			int total = res.size();
			if (total > 0) {
				if (correct == 0 || res.stream().anyMatch(it -> it.errorCode().equals(FAILURE_ERROR_CODE)))
					grade = Math.max(0.0, grade - test.penalty());
				else grade += ((double) correct / total) * test.weight();
			}
		}
		if (Double.isNaN(grade))
			return 0.0;
		return grade;
	}
}
