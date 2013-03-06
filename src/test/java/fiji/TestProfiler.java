package fiji;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintStream;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.Loader;

public class TestProfiler extends ProfilerBase<TestProfiler> {

	public TestProfiler() {
		super(System.getenv("TEST_PROFILE_ONLY"), System.getenv("TEST_PROFILE_SKIP"));
	}

	// implemented abstract methods

	@Override
	protected void init(final ClassPool pool, final Loader loader) {
		System.err.println("Initialized");
	}

	@Override
	protected void handle(CtClass clazz, CtBehavior behavior)
			throws CannotCompileException {
		System.err.println("Saw " + behavior.getLongName());
	}

	@Override
	protected void report(PrintStream out) {
		out.println("reporting");
	}

	// convenience methods

	public static ProfilerBase<?> getInstance() {
		return getInstance(TestProfiler.class);
	}

	public static boolean startProfiling(String mainClass, String... args) {
		return startProfiling(TestProfiler.class, mainClass, args);
	}

	public static void setActive(final boolean active) {
		setActive(TestProfiler.class, active);
	}

	protected static void main(final String... args) throws Throwable {
		main(TestProfiler.class, args);
	}

	protected static boolean isActive() {
		return isActive(TestProfiler.class);
	}

	protected static void report(final File file) throws FileNotFoundException {
		report(TestProfiler.class, file);
	}
}
