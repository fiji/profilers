/*
 * #%L
 * A base class for light-weight, javassist-backed profilers.
 * %%
 * Copyright (C) 2013 Johannes Schindelin.
 * %%
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice,
 *    this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDERS OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 * The views and conclusions contained in the software and documentation are
 * those of the authors and should not be interpreted as representing official
 * policies, either expressed or implied, of any organization.
 * #L%
 */

package fiji;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassClassPath;
import javassist.ClassPool;
import javassist.CtBehavior;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.Loader;
import javassist.Modifier;
import javassist.NotFoundException;
import javassist.Translator;

/**
 *
 * Use it in one of the following ways:
 * <ul>
 * <li>
 * <p>
 * insert the following line at the start of the main() method calling the code
 * to profile:<br />
 * <code>if (SubClass.startProfiling(args)) return;</code><br />
 * Subsequently, you can start/stop profiling by calling
 * setActive(Class, boolean), print a report using
 * report(PrintStream), or print a report to a file with
 * report(File).
 * </p>
 *
 * @author Johannes Schindelin
 */
public abstract class ProfilerBase<T extends ProfilerBase<T>> implements Translator {
	private Set<String> only, skip;
	private Loader loader;
	private Field activeField;
	private static Map<Class<ProfilerBase<?>>, ProfilerBase<?>> instances = new HashMap<Class<ProfilerBase<?>>, ProfilerBase<?>>();

	protected final boolean debug = false;

	/**
	 * The constructor.
	 *
	 * It takes optional lists of white-space-delimited list of classes to 1)
	 * limit to, or 2) exclude from, profiling.
	 *
	 * @param only
	 *            a white-space delimited list of classes to limit profiling to
	 * @param skip
	 *            a white-space delimited list of classes to exclude from
	 *            profiling
	 */
	protected ProfilerBase(final String only, final String skip) {
		this(split(only), split(skip));
	}

	/**
	 * The constructor.
	 *
	 * It takes optional lists classes to 1) limit to, or 2) exclude from,
	 * profiling.
	 *
	 * @param only
	 *            a list of classes to limit profiling to
	 * @param skip
	 *            a list of classes to exclude from profiling
	 */
	protected ProfilerBase(final Collection<String> only, final Collection<String> skip) {
		synchronized(ProfilerBase.class) {
			@SuppressWarnings("unchecked")
			Class<ProfilerBase<?>> clazz = (Class<ProfilerBase<?>>) getClass();
			if (instances.containsKey(clazz)) throw new RuntimeException("Profilers must be singletons!");
			instances.put(clazz, this);
		}
		if (only != null) {
			this.only = new HashSet<String>();
			this.only.addAll(only);
		}
		if (skip != null) {
			this.skip = new HashSet<String>();
			this.skip.addAll(skip);
		}
	}

	private static Collection<String> split(final String string) {
		if (string == null) return null;
		return Arrays.asList(string.split(" +"));
	}

	// abstract methods

	protected abstract void init(ClassPool pool, Loader loader2);
	protected abstract void handle(CtClass clazz, CtBehavior behavior) throws CannotCompileException;
	protected abstract void report(final PrintStream out);

	// public methods

	@SuppressWarnings("unchecked")
	public static<T extends ProfilerBase<T>> T getInstance(Class<T> clazz) {
		synchronized(ProfilerBase.class) {
			return (T) instances.get(clazz);
		}
	}

	private static<T extends ProfilerBase<T>> T getOrCreateInstance(Class<T> clazz) {
		T instance = getInstance(clazz);
		if (instance != null) return instance;
		try {
			return clazz.newInstance();
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	// methods to be called by static wrappers in subclasses

	/*
	 * Example:
	 *
	 * public static void startProfiling(String mainClass, String... args) {
	 * 	startProfiling(SubClass.class, mainClass, args);
	 * }
	 *
	 * [...]
	 */
	protected static<T extends ProfilerBase<T>> boolean startProfiling(Class<T> clazz, String mainClass, final String... args) {
		return getOrCreateInstance(clazz).startProfilingAux(mainClass, args);
	}

	protected static<T extends ProfilerBase<T>> void main(Class<T> clazz, final String... args) throws Throwable {
		getOrCreateInstance(clazz).mainAux(args);
	}

	protected static<T extends ProfilerBase<T>> void setActive(Class<T> clazz, boolean active) {
		getOrCreateInstance(clazz).setActiveAux(active);
	}

	protected static<T extends ProfilerBase<T>> boolean isActive(Class<T> clazz) {
		return getInstance(clazz).isActiveAux();
	}

	protected static <T extends ProfilerBase<T>> void report(Class<T> clazz, File file) throws FileNotFoundException {
		getInstance(clazz).reportAux(file);
	}

	/**
	 * Convenience method to call in your main(String[]) method.
	 *
	 * Use it like this:
	 *
	 * {@code if (SubClass.startProfiling(null, args)) return; }
	 *
	 * It will start the profiling class loader, run the main method of the
	 * calling class (which will call startProfiling() again, but that will now
	 * return false since it is already profiling) and return true.
	 *
	 * @param mainClass the main class, or null to refer to the caller's class
	 * @param args the arguments for the main method
	 * @return true if profiling was started, false if profiling is already active
	 */
	protected boolean startProfilingAux(String mainClass, final String... args) {
		if (getClass().getClassLoader().getClass().getName().equals(Loader.class.getName())) return false;
		StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (mainClass == null) mainClass = stack[4].getClassName();
		try {
			doMain(mainClass, args);
		} catch (Throwable e) {
			throw new RuntimeException(e);
		}
		return true;
	}

	/**
	 * The main method.
	 *
	 * Use the class as a main class to start profiling any other main class
	 * contained in the class path.
	 *
	 * @param args the main class to profile, followed by the arguments to pass to the main method
	 * @throws Throwable
	 */
	protected void mainAux(final String... args) throws Throwable {
		Thread.currentThread().setContextClassLoader(ProfilerBase.class.getClassLoader());

		if (args.length == 0) {
			System.err.println("Usage: java " + ProfilerBase.class + " <main-class> [<argument>...]");
			System.exit(1);
		}

		String mainClass = args[0];
		String[] mainArgs = new String[args.length - 1];
		System.arraycopy(args, 1, mainArgs, 0, mainArgs.length);

		doMain(mainClass, mainArgs);
	}

	/**
	 * Start or stop profiling
	 *
	 * This method initializes the profiling class loader if necessary.
	 *
	 * @param active
	 */
	protected void setActiveAux(boolean active) {
		if (loader == null) initAux();
		try {
			activeField.setBoolean(null, active);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Reports whether profiling is in progress
	 *
	 * @return whether we're profilin'
	 */
	protected boolean isActiveAux() {
		try {
			return activeField.getBoolean(null);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return false;
	}

	/**
	 * Writes a report.
	 *
	 * @param file the output file
	 * @throws FileNotFoundException
	 */
	protected void reportAux(File file) throws FileNotFoundException {
		final PrintStream stream = new PrintStream(new FileOutputStream(file));
		report(stream);
		stream.close();
	}

	// Translator methods

	@Override
	public synchronized void start(ClassPool pool) throws NotFoundException, CannotCompileException {
		// ignore
	}

	@Override
	public synchronized void onLoad(ClassPool pool, String classname) throws NotFoundException {
		// do not instrument yourself
		if (classname.equals(getClass().getName())) {
			return;
		}

		// do not instrument anything javassist
		if (classname.startsWith("javassist.")) {
			return;
		}

		if (only != null && !only.contains(classname)) {
			return;
		}
		if (skip != null && skip.contains(classname)) {
			return;
		}

		if (debug)
			System.err.println("instrumenting " + classname);

		CtClass cc = pool.get(classname);
		if (cc.isFrozen())
			return;

		// instrument all methods and constructors
		if (debug)
			System.err.println("Handling class " + cc.getName());
		handleAux(cc, cc.getClassInitializer());
		for (CtMethod method : cc.getDeclaredMethods())
			handleAux(cc, method);
		for (CtConstructor constructor : cc.getDeclaredConstructors())
			handleAux(cc, constructor);
	}

	// private methods and classes

	private void initAux() {
		assert(loader == null);
		try {
			Class<?> thisClass = ProfilerBase.class;
			if (thisClass.getClassLoader().getClass().getName().equals(Loader.class.getName())) return;
			ClassPool pool = ClassPool.getDefault();
			pool.appendClassPath(new ClassClassPath(thisClass));
			loader = new Loader(thisClass.getClassLoader(), pool);

			// initialize a couple of things in the "other" profiler "instance"
			CtClass that = pool.get(thisClass.getName());

			// add the "active" flag
			CtField active = new CtField(CtClass.booleanType, "active", that);
			active.setModifiers(Modifier.PUBLIC | Modifier.STATIC);
			that.addField(active);

			Class<?> thatClass = loader.loadClass(that.getName());

			// get a reference to the "active" flag for use in setActive() and isActive()
			activeField = thatClass.getField("active");

			// make setActive() and isActive() work in the other "instance", too
			/*
			for (String fieldName : new String[] { "loader", "activeField", "counters", "realReport" }) {
				Field thisField = thisClass.getDeclaredField(fieldName);
				thisField.setAccessible(true);
				Field thatField = thatClass.getDeclaredField(fieldName);
				thatField.setAccessible(true);
				thatField.set(null, thisField.get(null));
			}
			*/

			init(pool, loader);

			// add the class definition translator
			loader.addTranslator(pool, this);
		} catch (Throwable t) {
			throw new RuntimeException(t);
		}
	}

	/**
	 * Instruments a constructor or method.
	 *
	 * @param clazz the declaring class
	 * @param behavior the constructor or method
	 */

	private synchronized void handleAux(final CtClass clazz, final CtBehavior behavior) {
		if (behavior == null) return;
		try {
			if (clazz != behavior.getDeclaringClass()) {
				if (debug)
					System.err.println("Skipping superclass' method: " + behavior.getName()
							+ " (" + behavior.getDeclaringClass().getName() + " is superclass of " + clazz);
				return;
			}
			if (debug)
				System.err.println("instrumenting " + behavior.getClass().getName() + "." + behavior.getName());
			handle(clazz, behavior);
		}
		catch (CannotCompileException e) {
			if (!e.getMessage().equals("no method body")) {
				System.err.println("Problem with " + behavior.getLongName() + ":");
				if (e.getCause() != null && e.getCause() instanceof NotFoundException) {
					System.err.println("(could not find " + e.getCause().getMessage() + ")");
				} else {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Checks whether the given class contains a field with the given name.
	 *
	 * @param clazz the class
	 * @param name the field name
	 * @return whether the class has the field
	 */
	protected static boolean hasField(final CtClass clazz, final String name) {
		try {
			return clazz.getField(name) != null;
		} catch (NotFoundException e) {
			return false;
		}
	}

	/**
	 * Reports a caller.
	 *
	 * @param writer where to write to
	 * @param level how many levels to go back in the stack trace
	 */
	protected static void reportCaller(PrintStream writer, int level) {
		if (writer == null) {
			return;
		}
		final StackTraceElement[] stack = Thread.currentThread().getStackTrace();
		if (stack == null || stack.length <= level || stack[level] == null) {
			writer.println("Could not determine caller");
		} else {
			final StackTraceElement caller = stack[level];
			writer.println("Report called by " + caller.toString());
		}
	}

	@SuppressWarnings("unchecked")
	private void doMain(final String mainClass, final String... args) throws Throwable {
		setActive(getClass(), true);
		loader.run(mainClass, args);
		report(System.err);
	}
}
