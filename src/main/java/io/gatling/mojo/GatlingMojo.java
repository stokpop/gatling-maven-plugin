/**
 * Copyright 2011-2017 GatlingCorp (http://gatling.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * This file has been changed in the fork: events-gatling-maven-plugin
 */
package io.gatling.mojo;

import nl.stokpop.eventscheduler.EventScheduler;
import nl.stokpop.eventscheduler.EventSchedulerBuilder;
import nl.stokpop.eventscheduler.api.*;
import nl.stokpop.eventscheduler.exception.EventCheckFailureException;
import nl.stokpop.eventscheduler.exception.handler.KillSwitchException;
import org.apache.commons.exec.ExecuteException;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.toolchain.Toolchain;
import org.codehaus.plexus.util.DirectoryScanner;
import org.codehaus.plexus.util.ExceptionUtils;
import org.codehaus.plexus.util.SelectorUtils;
import org.codehaus.plexus.util.StringUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Modifier;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

import static io.gatling.mojo.MojoConstants.*;
import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.util.Arrays.stream;
import static org.codehaus.plexus.util.StringUtils.isBlank;

/**
 * Mojo to execute Gatling.
 */
@Mojo(name = "test",
  defaultPhase = LifecyclePhase.INTEGRATION_TEST,
  requiresDependencyResolution = ResolutionScope.TEST)
public class GatlingMojo extends AbstractGatlingExecutionMojo {

  private EventScheduler eventScheduler;

  /**
   * A name of a Simulation class to run.
   */
  @Parameter(property = "gatling.simulationClass")
  private String simulationClass;

  /**
   * Iterate over multiple simulations if more than one simulation file is found. By default false.
   * If multiple simulations are found but {@literal runMultipleSimulations} is false the execution will fail.
   */
  @Parameter(property = "gatling.runMultipleSimulations", defaultValue = "false")
  private boolean runMultipleSimulations;

  /**
   * List of include patterns to use for scanning. Includes all simulations by default.
   */
  @Parameter(property = "gatling.includes")
  private String[] includes;

  /**
   * List of exclude patterns to use for scanning. Excludes none by default.
   */
  @Parameter(property = "gatling.excludes")
  private String[] excludes;

  /**
   * Run simulation but does not generate reports. By default false.
   */
  @Parameter(property = "gatling.noReports", defaultValue = "false")
  private boolean noReports;

  /**
   * Generate the reports for the simulation in this folder.
   */
  @Parameter(property = "gatling.reportsOnly")
  private String reportsOnly;

  /**
   * A short description of the run to include in the report.
   */
  @Parameter(property = "gatling.runDescription")
  private String runDescription;

  /**
   * Will cause the project build to look successful, rather than fail, even
   * if there are Gatling test failures. This can be useful on a continuous
   * integration server, if your only option to be able to collect output
   * files, is if the project builds successfully.
   */
  @Parameter(property = "gatling.failOnError", defaultValue = "true")
  private boolean failOnError;

  /**
   * Continue execution of simulations despite assertion failure. If you have
   * some stack of simulations and you want to get results from all simulations
   * despite some assertion failures in previous one.
   */
  @Parameter(property = "gatling.continueOnAssertionFailure", defaultValue = "false")
  private boolean continueOnAssertionFailure;

  @Parameter(property = "gatling.useOldJenkinsJUnitSupport", defaultValue = "false")
  private boolean useOldJenkinsJUnitSupport;

  /**
   * Extra JVM arguments to pass when running Gatling.
   */
  @Parameter(property = "gatling.jvmArgs")
  private List<String> jvmArgs;

  /**
   * Override Gatling's default JVM args, instead of replacing them.
   */
  @Parameter(property = "gatling.overrideJvmArgs", defaultValue = "false")
  private boolean overrideJvmArgs;

  /**
   * Propagate System properties to forked processes.
   */
  @Parameter(property = "gatling.propagateSystemProperties", defaultValue = "true")
  private boolean propagateSystemProperties;

  /**
   * Extra JVM arguments to pass when running Zinc.
   */
  @Parameter(property = "gatling.compilerJvmArgs")
  private List<String> compilerJvmArgs;

  /**
   * Override Zinc's default JVM args, instead of replacing them.
   */
  @Parameter(property = "gatling.overrideCompilerJvmArgs", defaultValue = "false")
  private boolean overrideCompilerJvmArgs;

  /**
   * Extra options to be passed to scalac when compiling the Scala code
   */
  @Parameter(property = "gatling.extraScalacOptions")
  private List<String> extraScalacOptions;

  /**
   * Disable the Scala compiler, if scala-maven-plugin is already in charge
   * of compiling the simulations.
   */
  @Parameter(property = "gatling.disableCompiler", defaultValue = "false")
  private boolean disableCompiler;

  /**
   * Use this folder to discover simulations that could be run.
   */
  @Parameter(property = "gatling.simulationsFolder", defaultValue = "${project.basedir}/src/test/scala")
  private File simulationsFolder;

  /**
   * Use this folder as the folder where feeders are stored.
   */
  @Parameter(property = "gatling.resourcesFolder", defaultValue = "${project.basedir}/src/test/resources")
  private File resourcesFolder;

  @Parameter(defaultValue = "${plugin.artifacts}", readonly = true)
  private List<Artifact> artifacts;

  /**
   * Specify a different working directory.
   */
  @Parameter(property = "gatling.workingDirectory")
  private File workingDirectory;

  private Set<File> existingDirectories;

  /**
   * EventScheduler: enable or disable calls to EventScheduler, default is enabled
   */
  @Parameter(property = "gatling.eventSchedulerEnabled", defaultValue = "true")
  private boolean eventSchedulerEnabled;

  /**
   * EventScheduler: list of custom event definitions
   */
  @Parameter(property = "gatling.events")
  private Map<String, Properties> events;

  /**
   * EventScheduler: schedule script with events, one event per line, such as: PT1M|scale-down|replicas=2
   */
  @Parameter(property = "gatling.eventScheduleScript")
  private String eventScheduleScript;

  /**
   * EventScheduler: name of system under test.
   */
  @Parameter(property = "gatling.eventSystemUnderTest", defaultValue = "UNKNOWN_SYSTEM_UNDER_TEST")
  private String eventSystemUnderTest;

  /**
   * EventScheduler: work load for this test, for instance load or stress
   */
  @Parameter(property = "gatling.eventWorkload", defaultValue = "UNKNOWN_WORKLOAD")
  private String eventWorkload;

  /**
   * EventScheduler: environment for this test.
   */
  @Parameter(property = "gatling.eventTestEnvironment", defaultValue = "UNKNOWN_TEST_ENVIRONMENT")
  private String eventTestEnvironment;

  /**
   * EventScheduler: name of product that is being tested.
   */
  @Parameter(property = "gatling.eventProductName",  defaultValue = "ANONYMOUS_PRODUCT")
  private String eventProductName;

  /**
   * EventScheduler: name of performance dashboard for this test.
   */
  @Parameter(property = "gatling.eventDashboardName", defaultValue = "ANONYMOUS_DASHBOARD")
  private String eventDashboardName;

  /**
   * EventScheduler: test run id.
   */
  @Parameter(property = "gatling.eventTestRunId",  defaultValue = "ANONYMOUS_TEST_ID")
  private String eventTestRunId;

  /**
   * EventScheduler: build results url is where the build results of this load test can be found.
   */
  @Parameter(property = "gatling.eventBuildResultsUrl")
  private String eventBuildResultsUrl;

  /**
   * EventScheduler: the version number of the system under test.
   */
  @Parameter(property = "gatling.eventVersion",  defaultValue = "1.0.0-SNAPSHOT")
  private String eventVersion;

  /**
   * EventScheduler: test rampup time in seconds.
   */
  @Parameter(property = "gatling.eventRampupTimeInSeconds",  defaultValue = "30")
  private String eventRampupTimeInSeconds;

  /**
   * EventScheduler: test constant load time in seconds.
   */
  @Parameter(property = "gatling.eventConstantLoadTimeInSeconds", defaultValue = "570")
  private String eventConstantLoadTimeInSeconds;

  /**
   * EventScheduler: test run annotations passed via environment variable
   */
  @Parameter(property = "gatling.eventAnnotations", alias = "ann")
  private String eventAnnotations;

  /**
   * EventScheduler: test run variables passed via environment variable
   */
  @Parameter(property = "gatling.eventVariables")
  private Properties eventVariables;

  /**
   * EventScheduler: test run comma separated tags via environment variable
   */
  @Parameter(property = "gatling.eventTags")
  private List<String> eventTags;

  /**
   * EventScheduler: enable debug logging for events. Note: maven -X debug should
   * also be active.
   */
  @Parameter(property = "gatling.eventDebugEnabled")
  private boolean eventDebugEnabled;

  /**
   * EventScheduler: how often is keep alive event fired. Default is 30 seconds.
   */
  @Parameter(property = "gatling.eventKeepAliveIntervalInSeconds", defaultValue = "30")
  private Integer eventKeepAliveIntervalInSeconds;

  /**
   * Executes Gatling simulations.
   */
  @Override
  public void execute() throws MojoExecutionException, MojoFailureException {
    if (skip) {
      getLog().info("Skipping events-gatling-maven-plugin");
      return;
    }

    boolean abortEventScheduler = false;

    eventScheduler = eventSchedulerEnabled
            ? createEventScheduler()
            : null;

    // Create results directories
    if (!resultsFolder.exists() && !resultsFolder.mkdirs()) {
      throw new MojoExecutionException("Could not create resultsFolder " + resultsFolder.getAbsolutePath());
    }
    existingDirectories = directoriesInResultsFolder();
    Exception ex = null;

    try {
      List<String> testClasspath = buildTestClasspath();

      Toolchain toolchain = toolchainManager.getToolchainFromBuildContext("jdk", session);
      if (!disableCompiler) {
        executeCompiler(compilerJvmArgs(), testClasspath, toolchain);
      }

      List<String> jvmArgs = gatlingJvmArgs();

      if (reportsOnly != null) {
        executeGatling(jvmArgs, gatlingArgs(null), testClasspath, toolchain);

      } else {
        List<String> simulations = simulations();
        iterateBySimulations(toolchain, jvmArgs, testClasspath, simulations);
      }

    } catch (Exception e) {
      getLog().debug(">>> Inside catch exception: " + e);
      // AbortSchedulerException should just fall through and be handled like other exceptions
      // For KillSwitchException, go on with check results and assertions instead
      if (!(e instanceof KillSwitchException)) {
        if (failOnError) {
          getLog().debug(">>> Fail on error");
          abortEventScheduler = true;
          if (e instanceof GatlingSimulationAssertionsFailedException) {
            throw new MojoFailureException(e.getMessage(), e);
          } else if (e instanceof MojoFailureException) {
            throw (MojoFailureException) e;
          } else if (e instanceof MojoExecutionException) {
            throw (MojoExecutionException) e;
          } else {
            throw new MojoExecutionException("Gatling failed.", e);
          }
        } else {
          getLog().warn("There were some errors while running your simulation, but failOnError was set to false won't fail your build.");
        }
      ex = e instanceof GatlingSimulationAssertionsFailedException ? null : e;
      }
    } finally {
      recordSimulationResults(ex);
      if (eventScheduler != null && abortEventScheduler) {
        getLog().debug(">>> Abort in finally");
        eventScheduler.abortSession();
      }
      else {
        getLog().debug(">>> No abort called because abortEventScheduler is false");
      }
    }

    if (eventScheduler != null && !eventScheduler.isSessionStopped()) {
      getLog().debug(">>> Stop session (because not isSessionStopped())");
      eventScheduler.stopSession();
      try {
        getLog().debug(">>> Call check results");
        eventScheduler.checkResults();
      } catch (EventCheckFailureException e) {
        getLog().debug(">>> EventCheckFailureException: " + e.getMessage());
        if (!continueOnAssertionFailure) {
          throw  e;
        }
        else {
          getLog().warn("EventCheck failures found, but continue on assert failure is enabled:" + e.getMessage());
        }
      }

    }
  }

  private void startScheduler(EventScheduler eventScheduler, SchedulerExceptionHandler schedulerExceptionHandler) {
      eventScheduler.addKillSwitch(schedulerExceptionHandler);
      eventScheduler.startSession();
      addShutdownHookForEventScheduler(eventScheduler);
  }

  private void addShutdownHookForEventScheduler(EventScheduler eventScheduler) {
    final Thread main = Thread.currentThread();
    Runnable shutdowner = () -> {
        if (!eventScheduler.isSessionStopped()) {
          getLog().info("Shutdown Hook: abort event scheduler session!");
          eventScheduler.abortSession();
        }

        // try to hold on to main thread to let the abort event tasks finish properly
        try {
            main.join(4000);
        } catch (InterruptedException e) {
            getLog().warn("Interrupt while waiting for abort to finish.");
        }
    };
    Thread eventSchedulerShutdownThread = new Thread(shutdowner, "eventSchedulerShutdownThread");
    Runtime.getRuntime().addShutdownHook(eventSchedulerShutdownThread);
  }

  private Set<File> directoriesInResultsFolder() {
    File[] directories = resultsFolder.listFiles(File::isDirectory);
    return (directories == null)
            ? Collections.emptySet()
            : new HashSet<>(Arrays.asList(directories));
  }

  private void iterateBySimulations(Toolchain toolchain, List<String> jvmArgs, List<String> testClasspath, List<String> simulations) throws Exception {
    Exception exc = null;
    int simulationsCount = simulations.size();
    for (int i = 0; i < simulationsCount; i++) {
      try {
        executeGatling(jvmArgs, gatlingArgs(simulations.get(i)), testClasspath, toolchain);
      } catch (GatlingSimulationAssertionsFailedException e) {
        if (exc == null && i == simulationsCount - 1) {
          throw e;
        }

        if (continueOnAssertionFailure) {
          if (exc != null) {
            continue;
          }
          exc = e;
          continue;
        }
        throw e;
      }
    }

    if (exc != null) {
      getLog().warn("There were some errors while running your simulation, but continueOnAssertionFailure was set to true, so your simulations continue to perform.");
      throw exc;
    }
  }

  private void executeCompiler(List<String> zincJvmArgs, List<String> testClasspath, Toolchain toolchain) throws Exception {
    List<String> compilerClasspath = buildCompilerClasspath();
    compilerClasspath.addAll(testClasspath);
    List<String> compilerArguments = compilerArgs();

    Fork forkedCompiler = new Fork(COMPILER_MAIN_CLASS, compilerClasspath, zincJvmArgs, compilerArguments, toolchain, false, getLog());
    try {
      forkedCompiler.run();
    } catch (ExecuteException e) {
      throw new CompilationException(e);
    }
  }

  private void executeGatling(List<String> gatlingJvmArgs, List<String> gatlingArgs, List<String> testClasspath, Toolchain toolchain) throws Exception {
    Fork forkedGatling = new Fork(GATLING_MAIN_CLASS, testClasspath, gatlingJvmArgs, gatlingArgs, toolchain, propagateSystemProperties, getLog(), workingDirectory);

    if (eventSchedulerEnabled) {
      SchedulerExceptionHandler exceptionHandler = forkedGatling.getSchedulerExceptionHandler();
      startScheduler(eventScheduler, exceptionHandler);
    }
    else {
      getLog().warn("The Event Scheduler is disabled. Use 'eventSchedulerEnabled' property to enable.");
    }

    try {
      forkedGatling.run();
    } catch (ExecuteException e) {
      if (e.getExitValue() == 2)
        throw new GatlingSimulationAssertionsFailedException(e);
      else
        throw e; /* issue 1482*/
    }
  }

  private void recordSimulationResults(Exception exception) throws MojoExecutionException {
    try {
      saveSimulationResultToFile(exception);
      copyJUnitReports();
    } catch (IOException e) {
      throw new MojoExecutionException("Could not record simulation results.", e);
    }
  }

  private void saveSimulationResultToFile(Exception exception) throws IOException {
    Path resultsFile = resultsFolder.toPath().resolve(LAST_RUN_FILE);

    try (BufferedWriter writer = Files.newBufferedWriter(resultsFile)) {
      saveListOfNewRunDirectories(writer);
      writeExceptionIfExists(writer, exception);
    }
  }

  private void saveListOfNewRunDirectories(BufferedWriter writer) throws IOException {
      for (File directory : directoriesInResultsFolder()) {
        if (isNewDirectory(directory)) {
          writer.write(directory.getName() + System.lineSeparator());
        }
      }
  }

  private void writeExceptionIfExists(BufferedWriter writer, Exception exception) throws IOException {
    if (exception != null) {
      writer.write(LAST_RUN_FILE_ERROR_LINE + getRecursiveCauses(exception) + System.lineSeparator());
    }
  }

  private String getRecursiveCauses(Throwable e) {
    return stream(ExceptionUtils.getThrowables(e))
            .map(ex -> joinNullable(ex.getClass().getName(), ex.getMessage()))
            .collect(Collectors.joining(" | "));
  }

  private String joinNullable(String s, String sNullable) {
    return isBlank(sNullable) ? s : s + ": " + sNullable;
  }

  private boolean isNewDirectory(File directory) {
    return !existingDirectories.contains(directory);
  }

  private void copyJUnitReports() throws MojoExecutionException {

    try {
      if (useOldJenkinsJUnitSupport) {
        for (File directory: directoriesInResultsFolder()) {
          File jsDir = new File(directory, "js");
          if (jsDir.exists() && jsDir.isDirectory()) {
            File assertionFile = new File(jsDir, "assertions.xml");
            if (assertionFile.exists()) {
              File newAssertionFile = new File(resultsFolder, "assertions-" + directory.getName() + ".xml");
              Files.copy(assertionFile.toPath(), newAssertionFile.toPath(), COPY_ATTRIBUTES, REPLACE_EXISTING);
              getLog().info("Copying assertion file " + assertionFile.getCanonicalPath() + " to " + newAssertionFile.getCanonicalPath());
            }
          }
        }
      }
    } catch (IOException e) {
      throw new MojoExecutionException("Failed to copy JUnit reports", e);
    }
  }

  private List<String> buildCompilerClasspath() throws Exception {

    List<String> compilerClasspathElements = new ArrayList<>();
    for (Artifact artifact: artifacts) {
      String groupId = artifact.getGroupId();
      if (!groupId.startsWith("org.codehaus.plexus")
        && !groupId.startsWith("org.apache.maven")
        && !groupId.startsWith("org.sonatype")) {
        compilerClasspathElements.add(artifact.getFile().getCanonicalPath());
      }
    }

    String gatlingVersion = getGatlingVersion();
    Set<Artifact> gatlingCompilerAndDeps = resolveCompilerAndDeps(gatlingVersion).getArtifacts();
    for (Artifact artifact : gatlingCompilerAndDeps) {
      compilerClasspathElements.add(artifact.getFile().getCanonicalPath());
    }

    // Add plugin jar to classpath (used by MainWithArgsInFile)
    compilerClasspathElements.add(MojoUtils.locateJar(GatlingMojo.class));
    return compilerClasspathElements;
  }

  private String getGatlingVersion() {
    for (Artifact artifact : mavenProject.getArtifacts()) {
      if (artifact.getGroupId().equals("io.gatling") && artifact.getArtifactId().equals("gatling-core")) {
        return artifact.getBaseVersion();
      }
    }
    throw new UnsupportedOperationException("Couldn't locate io.gatling:gatling-core in classpath");
  }

  private ArtifactResolutionResult resolveCompilerAndDeps(String gatlingVersion) {
    Artifact artifact = repository.createArtifact("io.gatling", "gatling-compiler", gatlingVersion, Artifact.SCOPE_RUNTIME, "jar");
    ArtifactResolutionRequest request =
      new ArtifactResolutionRequest()
        .setArtifact(artifact)
        .setResolveRoot(true)
        .setResolveTransitively(true)
        .setServers(session.getRequest().getServers())
        .setMirrors(session.getRequest().getMirrors())
        .setProxies(session.getRequest().getProxies())
        .setLocalRepository(session.getLocalRepository())
        .setRemoteRepositories(session.getCurrentProject().getRemoteArtifactRepositories());
    return repository.resolve(request);
  }

  private List<String> gatlingJvmArgs() {
    return computeArgs(jvmArgs, GATLING_JVM_ARGS, overrideJvmArgs);
  }

  private List<String> compilerJvmArgs() {
    return computeArgs(compilerJvmArgs, COMPILER_JVM_ARGS, overrideCompilerJvmArgs);
  }

  private List<String> computeArgs0(List<String> custom, List<String> defaults, boolean override) {
    if (custom.isEmpty()) {
      return defaults;
    }
    if (override) {
      List<String> merged = new ArrayList<>(custom);
      merged.addAll(defaults);
      return merged;
    }
    return custom;
  }

  private List<String> computeArgs(List<String> custom, List<String> defaults, boolean override) {
    List<String> result = new ArrayList<>(computeArgs0(custom, defaults, override));
    // force disable disableClassPathURLCheck because Debian messed up and takes forever to fix, see https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=911925
    result.add("-Djdk.net.URLClassPath.disableClassPathURLCheck=true");
    return result;
  }

  private List<String> simulations() throws MojoFailureException {
    // Solves the simulations, if no simulation file is defined
    if (simulationClass != null) {
      return Collections.singletonList(simulationClass);

    } else {
      List<String> simulations = resolveSimulations();

      if (simulations.isEmpty()) {
        getLog().error("No simulations to run");
        throw new MojoFailureException("No simulations to run");
      }

      if (simulations.size() > 1 && !runMultipleSimulations) {
        String message = "More than 1 simulation to run, need to specify one, or enable runMultipleSimulations";
        getLog().error(message);
        throw new MojoFailureException(message);
      }

      return simulations;
    }
  }

  private List<String> gatlingArgs(String simulationClass) throws Exception {
    // Arguments
    List<String> args = new ArrayList<>();
    addArg(args, "rsf", resourcesFolder.getCanonicalPath());
    addArg(args, "rf", resultsFolder.getCanonicalPath());
    addArg(args, "sf", simulationsFolder.getCanonicalPath());

    addArg(args, "rd", runDescription);

    if (noReports) {
      args.add("-nr");
    }

    addArg(args, "s", simulationClass);
    addArg(args, "ro", reportsOnly);

    return args;
  }

  private List<String> compilerArgs() throws Exception {
    List<String> args = new ArrayList<>();
    addArg(args, "sf", simulationsFolder.getCanonicalPath());
    addArg(args, "bf", compiledClassesFolder.getCanonicalPath());

    if (!extraScalacOptions.isEmpty()) {
      addArg(args, "eso", StringUtils.join(extraScalacOptions.iterator(), ","));
    }

    return args;
  }

  /**
   * Resolve simulation files to execute from the simulation folder.
   *
   * @return a comma separated String of simulation class names.
   */
  private List<String> resolveSimulations() {

    try {
      ClassLoader testClassLoader = new URLClassLoader(testClassPathUrls());

      Class<?> simulationClass = testClassLoader.loadClass("io.gatling.core.scenario.Simulation");
      List<String> includes = MojoUtils.arrayAsListEmptyIfNull(this.includes);
      List<String> excludes = MojoUtils.arrayAsListEmptyIfNull(this.excludes);

      List<String> simulationsClasses = new ArrayList<>();

      for (String classFile: compiledClassFiles()) {
        String className = pathToClassName(classFile);

        boolean isIncluded = includes.isEmpty() || match(includes, className);
        boolean isExcluded =  match(excludes, className);

        if (isIncluded && !isExcluded) {
          // check if the class is a concrete Simulation
          Class<?> clazz = testClassLoader.loadClass(className);
          if (simulationClass.isAssignableFrom(clazz) && isConcreteClass(clazz)) {
            simulationsClasses.add(className);
          }
        }
      }

      return simulationsClasses;

    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private static boolean match(List<String> patterns, String string) {
    for (String pattern : patterns) {
      if (SelectorUtils.match(pattern, string)) {
        return true;
      }
    }
    return false;
  }

  private URL[] testClassPathUrls() throws DependencyResolutionRequiredException, MalformedURLException {

    List<String> testClasspathElements = mavenProject.getTestClasspathElements();

    URL[] urls = new URL[testClasspathElements.size()];
    for (int i = 0; i < testClasspathElements.size(); i++) {
      String testClasspathElement = testClasspathElements.get(i);
      URL url = Paths.get(testClasspathElement).toUri().toURL();
      urls[i] = url;
    }

    return urls;
  }

  private String[] compiledClassFiles() throws IOException {
    DirectoryScanner scanner = new DirectoryScanner();
    scanner.setBasedir(compiledClassesFolder.getCanonicalPath());
    scanner.setIncludes(new String[]{"**/*.class"});
    scanner.scan();
    String[] files = scanner.getIncludedFiles();
    Arrays.sort(files);
    return files;
  }

  private String pathToClassName(String path) {
    return path.substring(0, path.length() - ".class".length()).replace(File.separatorChar, '.');
  }

  private boolean isConcreteClass(Class<?> clazz) {
    return !clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers());
  }

  private EventScheduler createEventScheduler() {

    EventLogger logger = new EventLogger() {
      @Override
      public void info(String message) {
        getLog().info(message);
      }

      @Override
      public void warn(String message) {
        getLog().warn(message);
      }

      @Override
      public void error(String message) {
        getLog().error(message);
      }

      @Override
      public void error(String message, Throwable throwable) {
        getLog().error(message, throwable);
      }

      @Override
      public void debug(final String message) {
        if (isDebugEnabled()) getLog().debug(message);
      }

      @Override
      public boolean isDebugEnabled() {
          return eventDebugEnabled;
      }

    };

    // there might be null values for empty <tag></tag>
    List<String> filteredEventTags = eventTags.stream().filter(Objects::nonNull).collect(Collectors.toList());

    TestContext testContext = new TestContextBuilder()
            .setTestRunId(eventTestRunId)
            .setSystemUnderTest(eventSystemUnderTest)
            .setVersion(eventVersion)
            .setWorkload(eventWorkload)
            .setTestEnvironment(eventTestEnvironment)
            .setCIBuildResultsUrl(eventBuildResultsUrl)
            .setRampupTimeInSeconds(eventRampupTimeInSeconds)
            .setConstantLoadTimeInSeconds(eventConstantLoadTimeInSeconds)
            .setAnnotations(eventAnnotations)
            .setTags(filteredEventTags)
            .setVariables(eventVariables)
            .build();

    EventSchedulerSettings settings = new EventSchedulerSettingsBuilder()
            .setKeepAliveInterval(Duration.ofSeconds(eventKeepAliveIntervalInSeconds))
            .build();

    EventSchedulerBuilder eventSchedulerBuilder = new EventSchedulerBuilder()
            .setEventSchedulerSettings(settings)
            .setTestContext(testContext)
            .setAssertResultsEnabled(eventSchedulerEnabled)
            .setCustomEvents(eventScheduleScript)
            .setLogger(logger);

    if (events != null) {
      events.forEach(eventSchedulerBuilder::addEvent);
    }

    return eventSchedulerBuilder.build();
  }

}
