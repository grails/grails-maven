/*
 * Copyright 2007 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.grails.maven.plugin;

import grails.util.Metadata;
import jline.TerminalFactory;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.*;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.grails.launcher.GrailsLauncher;
import org.grails.launcher.RootLoader;
import org.grails.maven.plugin.tools.ForkedGrailsRuntime;
import org.grails.maven.plugin.tools.GrailsServices;

import java.io.File;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.*;

/**
 * Common services for all Mojos using Grails
 *
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @author Peter Ledbrook
 * @author Jonathan Pearlin
 * @author Graeme Rocher
 *
 * @version $Id$
 */
public abstract class AbstractGrailsMojo extends AbstractMojo {

    public static final String DEPENDENCY_FILE_LOC = "org.grails.ide.eclipse.dependencies.filename";
    public static final String GRAILS_BUILD_LISTENERS = "grails.build.listeners";
    public static final String PLUGIN_PREFIX = "grails-";
    private static final String GRAILS_PLUGIN_NAME_FORMAT = "plugins.%s:%s";
    public static final String APP_GRAILS_VERSION = "app.grails.version";
    public static final String APP_VERSION = "app.version";
    public static final String SPRING_LOADED_VERSION = "1.1.1";

    /**
     * Whether to activate the reloading agent (forked mode only) for this command
	 *
	 * @parameter expression="${activateAgent}"
     */
    protected boolean activateAgent;
    /**
     * The directory where is launched the mvn command.
     *
     * @parameter default-value="${basedir}"
     * @required
     */
    protected File basedir;

    /**
     * The Grails environment to use.
     *
     * @parameter expression="${grails.env}"
     */
    protected String env;

    /**
     * The Grails environment to use.
     *
     * @parameter expression="${environment}"
     */
    protected String grailsEnv;

    /**
     * The Grails work directory to use.
     *
     * @parameter expression="${grails.grailsWorkDir}" default-value="${project.build.directory}/work"
     */
    protected String grailsWorkDir;

    /**
     * Whether to run Grails in non-interactive mode or not. The default
     * is to run interactively, just like the Grails command-line.
     *
     * @parameter expression="${nonInteractive}" default-value="false"
     * @required
     */
    protected boolean nonInteractive;

	/**
	 * Turns on/off stacktraces in the console output for Grails commands.
	 *
	 * @parameter expression="${showStacktrace}" default-value="false"
	 */
	protected boolean showStacktrace;

    /**
     * Whether the JVM is forked for executing Grails commands
     *
     * @parameter expression="${fork}" default-value="false"
     */
    protected boolean fork = false;

    /**
     * List of arguments passed to the forked VM
     *
     * @parameter
     */
    protected List forkedVmArgs;

    /**
     * Whether the JVM is forked for executing Grails commands
     *
     * @parameter expression="${forkDebug}" default-value="false"
     */
    protected boolean forkDebug = false;

    /**
     * Whether the JVM is forked for executing Grails commands
     *
     * @parameter expression="${forkPermGen}" default-value="256"
     */
    protected int forkPermGen = 256;

    /**
     * Whether the JVM is forked for executing Grails commands
     *
     * @parameter expression="${forkMaxMemory}" default-value="1024"
     */
    protected int forkMaxMemory = 1024;

    /**
     * Whether the JVM is forked for executing Grails commands
     *
     * @parameter expression="${forkMinMemory}" default-value="512"
     */
    protected int forkMinMemory = 512;    

    /**
     * The directory where plugins are stored.
     *
     * @parameter expression="${pluginsDirectory}" default-value="${basedir}/plugins"
     * @required
     */
    protected File pluginsDir;

    /**
     * The path to the Grails installation.
     *
     * @parameter expression="${grailsHome}"
     */
    protected File grailsHome;


    /**
     * The Maven settings reference.
     *
     * @parameter expression="${settings}"
     * @required
     * @readonly
     */
    protected Settings settings;

    /**
     * POM
     *
     * @parameter expression="${project}"
     * @readonly
     * @required
     */
    protected MavenProject project;

    /**
     * @component
     */
    private ArtifactResolver artifactResolver;

    /**
     * @component
     */
    private ArtifactFactory artifactFactory;

    /**
     * @component
     */
    private ArtifactCollector artifactCollector;

    /**
     * @component
     */
    private ArtifactMetadataSource artifactMetadataSource;

    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

    /**
     * @parameter expression="${project.remoteArtifactRepositories}"
     * @required
     * @readonly
     */
    private List<?> remoteRepositories;
    
    /**
     * Extra classpath entries as a comma separated list of file names.
     * For entries with a comma in their name, use backslash to escape.
     * 
     * INTERNAL This parameter is not meant to be used externally.  It is used by IDEs
     * that require extra classpath entries to execute grails commands.
     * 
     * @parameter
     */
    private String extraClasspathEntries;
    
    
    /**
     * Fully qualified classname of a grails build listener to attach 
     * to the Grails command
     * 
     * @parameter
     */
    private String grailsBuildListener;
    
    /**
     * Fully qualified path name of the project dependency file to create.
     * 
     * INTERNAL This parameter is not meant to be used externally. This parameter is used by 
     * IDEs to generate project dependency information during builds.
     */
    private String dependencyFileLocation;
    
    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

    /**
     * @component
     * @readonly
     */
    private GrailsServices grailsServices;

    
    /**
     * Returns the configured base directory for this execution of the plugin.
     *
     * @return The base directory.
     */
    protected File getBasedir() {
        if (basedir == null) {
            throw new RuntimeException("Your subclass have a field called 'basedir'. Remove it and use getBasedir() " +
                    "instead.");
        }

        return this.basedir;
    }

    protected String getEnvironment() {
       if(env == null) {
           return grailsEnv;
       }
       return env;
    }

    /**
     * Returns the {@code GrailsServices} instance used by the plugin with the base directory
     * of the services object set to the configured base directory.
     *
     * @return The underlying {@code GrailsServices} instance.
     */
    protected GrailsServices getGrailsServices() {
        grailsServices.setBasedir(basedir);
        return grailsServices;
    }

    /**
     * Executes the requested Grails target.  The "targetName" must match a known
     * Grails script provided by grails-scripts.
     *
     * @param targetName The name of the Grails target to execute.
     * @throws MojoExecutionException if an error occurs while attempting to execute the target.
     */
    protected void runGrails(final String targetName) throws MojoExecutionException {
        runGrails(targetName, System.getProperty("grails.cli.args"));
    }

    /**
     * Executes the requested Grails target.  The "targetName" must match a known
     * Grails script provided by grails-scripts.
     *
     * @param targetName The name of the Grails target to execute.
     * @param args       String of arguments to be passed to the executed Grails target.
     * @throws MojoExecutionException if an error occurs while attempting to execute the target.
     */
    protected void runGrails(final String targetName, String args) throws MojoExecutionException {


        if (fork) {
            final String targetDir = this.project.getBuild().getDirectory();
            ForkedGrailsRuntime.ExecutionContext ec = new ForkedGrailsRuntime.ExecutionContext();
            ec.setBuildDependencies(resolveGrailsExecutionPathJars(true));
            List<File> providedDependencies = resolveArtifacts(getProvidedArtifacts(project));            
            List<File> compileDependencies = resolveArtifacts(getCompileArtifacts(project));

            Set<File> runtimeDependencies = new HashSet<File>( resolveArtifacts(getRuntimeArtifacts(project)) );
            runtimeDependencies.addAll( compileDependencies );

            Set<File> testDependencies = new HashSet<File>( resolveArtifacts(getTestArtifacts(project)) );


            testDependencies.addAll( providedDependencies );
            testDependencies.addAll( compileDependencies );
            testDependencies.addAll( runtimeDependencies );
            testDependencies.addAll( testDependencies );

            ec.setProvidedDependencies(providedDependencies);
            ec.setCompileDependencies(compileDependencies);
            ec.setTestDependencies( new ArrayList<File>(testDependencies) );
            ec.setRuntimeDependencies( new ArrayList<File>(runtimeDependencies) );

            ec.setGrailsWorkDir(new File(grailsWorkDir));
            ec.setProjectWorkDir(new File(targetDir));
            ec.setClassesDir(new File(targetDir, "classes"));
            ec.setTestClassesDir(new File(targetDir, "test-classes"));
            ec.setResourcesDir(new File(targetDir, "resources"));
            ec.setProjectPluginsDir(this.pluginsDir);
            ec.setForkedVmArgs(this.forkedVmArgs);

            // If the command is running in non-interactive mode, we
            // need to pass on the relevant argument.
            if (this.nonInteractive) {
                args = (args != null) ? "--non-interactive " + args : "--non-interactive ";
            }

            // If the project has specified to print stacktraces to the console
            // turn on the flag in the arguments.
            if (this.showStacktrace) {
                args = (args != null) ? "--stacktrace " + args : "--stacktrace ";
            }
            
            // these two settings are only used if running inside of an ide
            if (this.grailsBuildListener != null) {
                ec.setGrailsBuildListener(grailsBuildListener);
            }
            if (this.dependencyFileLocation != null) {
                ec.setDependencyFileLocation(new File(dependencyFileLocation));
            }

            // Enable the plain output for the Grails command to fix an issue with JLine
            // consuming the standard output after execution via Maven.
            args = (args != null) ? "--plain-output " + args : "--plain-output";
            ec.setArgs(args);
            ec.setScriptName(targetName);
            ec.setBaseDir(project.getBasedir());
            ec.setEnv(getEnvironment());
            ForkedGrailsRuntime fgr = new ForkedGrailsRuntime(ec);
            if(activateAgent) {
                List<File> springLoadedJar = resolveArtifacts(Collections
                        .singleton(artifactFactory.createArtifact(
                                "org.springsource.springloaded",
                                "springloaded-core", SPRING_LOADED_VERSION,
                                Artifact.SCOPE_COMPILE, "jar")));
                if(!springLoadedJar.isEmpty()) {
                    fgr.setReloadingAgent(springLoadedJar.get(0));
                }else{
                    getLog().warn("Grails Start with Reloading: org.springsource.springloaded:springloaded-core"+SPRING_LOADED_VERSION+" not found");
                    getLog().error("Grails Start with Reloading: not enabled");
                }
            }
            fgr.setDebug(forkDebug);
            fgr.setMaxMemory(forkMaxMemory);
            fgr.setMaxPerm(forkPermGen);
            fgr.setMinMemory(forkMinMemory);
            try {
                handleVersionSync();
                fgr.fork();
            } catch (Exception e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }
        } else {
            getLog().warn("Grails Start with out fork");
            runGrailsInline(targetName, args);
        }
    }

    private List<File> getCompileFiles() throws MojoExecutionException {
        List compileClasspathElements = null;
        try {
            compileClasspathElements = project.getCompileClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
        List<File> dependencyFiles = getDependencyFiles(compileClasspathElements);

        Collection<Artifact> compileArtifacts = getCompileArtifacts(project);
        List<File> files = artifactsToFiles(compileArtifacts);
        dependencyFiles.addAll(zipFilesOnly(files));

        return dependencyFiles;
    }

    private Collection<? extends File> zipFilesOnly(List<File> files) {
        List<File> newFiles = new ArrayList<File>();
        for (File file : files) {
            if(file != null && file.getName().endsWith(".zip")) {
                newFiles.add(file);
            }
        }
        return newFiles;
    }


    private List<File> getRuntimeFiles() throws MojoExecutionException {
        List runtimeClasspathElements;
        try {
            runtimeClasspathElements = project.getRuntimeClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
        List<File> dependencyFiles = getDependencyFiles(runtimeClasspathElements);
        Collection<Artifact> compileArtifacts = getRuntimeArtifacts(project);
        List<File> files = artifactsToFiles(compileArtifacts);
        dependencyFiles.addAll(zipFilesOnly(files));

        return dependencyFiles;
    }

    private List<File> getTestFiles() throws MojoExecutionException {
        List testClasspathElements;
        try {
            testClasspathElements = project.getTestClasspathElements();
        } catch (DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
        List<File> dependencyFiles = getDependencyFiles(testClasspathElements);
        Collection<Artifact> testArtifacts = getTestArtifacts(project);
        List<File> files = artifactsToFiles(testArtifacts);
        dependencyFiles.addAll(zipFilesOnly(files));
        return dependencyFiles;
    }

    private List<File> getDependencyFiles(List compileClasspathElements) {
        List<File> files = new ArrayList<File>();
        for (Object compileClasspathElement : compileClasspathElements) {
            files.add(new File(compileClasspathElement.toString()));
        }
        return files;
    }

    /**
     * Resolves artifacts to files including transitive resolution
     *
     * @param artifacts The artifacts
     * @return
     * @throws MojoExecutionException
     */
    protected List<File> resolveArtifacts(Collection<Artifact> artifacts) throws MojoExecutionException {
        try {
            ArtifactResolutionResult result = artifactCollector.collect(new HashSet<Artifact>(artifacts),
                    project.getArtifact(),
                    this.localRepository,
                    this.remoteRepositories,
                    this.artifactMetadataSource,
                    null,
                    Collections.EMPTY_LIST);
            Set<Artifact> newArtifacts = result.getArtifacts();
            //resolve all dependencies transitively to obtain a comprehensive list of assemblies
            for (final Artifact currentArtifact : newArtifacts) {
                if (!currentArtifact.getArtifactId().equals("tools") && !currentArtifact.getGroupId().equals("com.sun")) {
                    this.artifactResolver.resolve(currentArtifact, this.remoteRepositories, this.localRepository);
                }
            }
            return artifactsToFiles(newArtifacts);
        } catch (ArtifactResolutionException e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        } catch (ArtifactNotFoundException e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }


    }

    protected void runGrailsInline(String targetName, String args) throws MojoExecutionException {
        InputStream currentIn = System.in;
        PrintStream currentOutput = System.out;

        try {
            configureMavenProxy();

            final URL[] classpath = generateGrailsExecutionClasspath();

            final String grailsHomePath = (grailsHome != null) ? grailsHome.getAbsolutePath() : null;
            final RootLoader rootLoader = new RootLoader(classpath, ClassLoader.getSystemClassLoader());
            System.setProperty("grails.console.enable.terminal", "false");
            System.setProperty("grails.console.enable.interactive", "false");

            Class cls = rootLoader.loadClass("org.springframework.util.Log4jConfigurer");
            invokeStaticMethod(cls, "initLogging", new Object[]{"classpath:grails-maven/log4j.properties"});
            final GrailsLauncher launcher = new GrailsLauncher(rootLoader, grailsHomePath, basedir.getAbsolutePath());
            launcher.setPlainOutput(true);
            configureBuildSettings(launcher);
            handleVersionSync();


            // If the command is running in non-interactive mode, we
            // need to pass on the relevant argument.
            if (this.nonInteractive) {
                args = (args != null) ? "--non-interactive " + args : "--non-interactive ";
            }

            // If the project has specified to print stacktraces to the console
            // turn on the flag in the arguments.
            if (this.showStacktrace) {
                args = (args != null) ? "--stacktrace " + args : "--stacktrace ";
            }

            // Enable the plain output for the Grails command to fix an issue with JLine
            // consuming the standard output after execution via Maven.
            args = (args != null) ? "--plain-output " + args : "--plain-output";
            
            // set system properties specific for ides, but be nice and unset when done
            String oldBuildListeners = System.getProperty(GRAILS_BUILD_LISTENERS);
            if (this.grailsBuildListener != null) {
                System.setProperty(GRAILS_BUILD_LISTENERS, this.grailsBuildListener);
                getLog().info("Grails build listener: " + this.grailsBuildListener);
            }
            String oldDependenciesFile = System.getProperty(DEPENDENCY_FILE_LOC);
            if (this.dependencyFileLocation != null) {
                System.setProperty(DEPENDENCY_FILE_LOC, this.dependencyFileLocation);
                getLog().info("Dependency file location: " + this.dependencyFileLocation);
            }
            try {
                final int retval = launcher.launch(targetName, args, getEnvironment());
                if (retval != 0) {
                    throw new MojoExecutionException("Grails returned non-zero value: " + retval);
                }

            } finally {
                if (oldBuildListeners != null) {
                    System.setProperty(GRAILS_BUILD_LISTENERS, oldBuildListeners);
                }
                if (oldDependenciesFile != null) {
                    System.setProperty(DEPENDENCY_FILE_LOC, oldDependenciesFile);
                }
            }
        } catch (final MojoExecutionException ex) {
            // Simply rethrow it.
            throw ex;
        } catch (final Exception ex) {
            throw new MojoExecutionException("Unable to start Grails", ex);
        } finally {
            TerminalFactory.reset();
            System.setIn(currentIn);
            System.setOut(currentOutput);
        }
    }

    private void handleVersionSync() {
        // Search for all Grails plugin dependencies and install
        // any that haven't already been installed.
        final Metadata metadata = Metadata.getInstance(new File(getBasedir(), "application.properties"));
        boolean metadataModified = syncVersions(metadata);

        if (metadataModified)
            metadata.persist();
    }

    private boolean syncVersions(Metadata metadata) {

        boolean result = false;

        Object grailsVersion = metadata.get(APP_GRAILS_VERSION);
        Artifact grailsDependency = findGrailsDependency(project);
        if (grailsDependency != null) {
            if (!grailsDependency.getVersion().equals(grailsVersion)) {
                metadata.put(APP_GRAILS_VERSION, grailsDependency.getVersion());
                result = true;
            }
        }

        Object appVersion = metadata.get(APP_VERSION);
        if (appVersion != null) {
            if (!project.getVersion().equals(appVersion)) {
                metadata.put(Metadata.APPLICATION_VERSION, project.getVersion());
                result = true;
            }
        }

        return result;
    }

    private boolean isGrailsPlugin(File basedir) {
        try {
            File[] files = basedir.listFiles();
            if(files != null) {
                for (File file : files) {
                    if(file.getName().endsWith("GrailsPlugin.groovy")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
        return false;
    }

    private Artifact findGrailsDependency(MavenProject project) {
        Set dependencyArtifacts = project.getDependencyArtifacts();
        for (Object o : dependencyArtifacts) {
            Artifact artifact = (Artifact) o;
            if (artifact.getArtifactId().equals("grails-dependencies")) {
                return artifact;
            }
        }
        return null;
    }


    private void configureMavenProxy() {
        if (settings != null) {
            Proxy activeProxy = settings.getActiveProxy();
            if (activeProxy != null) {
                String host = activeProxy.getHost();
                int port = activeProxy.getPort();
                String username = activeProxy.getUsername();
                String password = activeProxy.getPassword();

                System.setProperty("http.proxyHost", host);
                System.setProperty("http.proxyPort", String.valueOf(port));
                if (username != null) {
                    System.setProperty("http.proxyUser", username);
                }
                if (password != null) {
                    System.setProperty("http.proxyPassword", password);
                }
            }
        }
    }

    /**
     * Invokes the named method on a target object using reflection.
     * The method signature is determined by the classes of each argument.
     *
     * @param target The object to call the method on.
     * @param name   The name of the method to call.
     * @param args   The arguments to pass to the method (may be an empty array).
     * @return The value returned by the method.
     */
    private Object invokeStaticMethod(Class target, String name, Object[] args) {
        Class<?>[] argTypes = new Class[args.length];
        for (int i = 0; i < args.length; i++) {
            argTypes[i] = args[i].getClass();
        }

        try {
            return target.getMethod(name, argTypes).invoke(target, args);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Generates the classpath to be used by the launcher to execute the requested Grails script.
     *
     * @return An array of {@code URL} objects representing the dependencies required on the classpath to
     *         execute the selected Grails script.
     * @throws MojoExecutionException if an error occurs while attempting to resolve the dependencies and
     *                                generate the classpath array.
     */
    @SuppressWarnings("unchecked")
    private URL[] generateGrailsExecutionClasspath() throws MojoExecutionException {
        try {
            List<File> jars = resolveGrailsExecutionPathJars();
            final List<URL> classpath = new ArrayList<URL>();
            for (File jar : jars) {
                URL url = jar.toURI().toURL();
                if (url != null) {
                    classpath.add(url);
                }
            }

            return classpath.toArray(new URL[classpath.size()]);
        } catch (final Exception e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
    }

    private List<File> resolveGrailsExecutionPathJars() throws MojoExecutionException {
        return resolveGrailsExecutionPathJars(false);
    }
    
    private static List<String> BOOTSTRAP_DEPENDENCIES = new ArrayList<String>() {{
        add("grails-launcher");
        add("ivy");
        add("ant");
        add("jansi");
        add("jline");
        add("grails-maven-plugin");
    }};
    private List<File> resolveGrailsExecutionPathJars(boolean pluginOnly) throws MojoExecutionException {
        try {
            final List<Dependency> unresolvedDependencies = new ArrayList<Dependency>();
            final Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();

            /*
                 * Get the Grails dependencies from the plugin's POM file first.
                */
            final MavenProject pluginProject = getPluginProject();

            /*
                * Add the plugin's dependencies and the project using the plugin's dependencies to the list
                * of unresolved dependencies.  This is done so they can all be resolved at the same time so
                * that we get the benefit of Maven's conflict resolution.
                */

            if(pluginOnly) {
                Dependency mavenPluginDependency = new Dependency();
                Artifact currentArtifact = getPluginProject().getArtifact();
                mavenPluginDependency.setGroupId(currentArtifact.getGroupId());
                mavenPluginDependency.setVersion(currentArtifact.getVersion());
                mavenPluginDependency.setArtifactId(currentArtifact.getArtifactId());
                unresolvedDependencies.add(mavenPluginDependency);
                Dependency grailsLauncherDep = findDependency(getPluginProject().getDependencies(), "grails-launcher");
                unresolvedDependencies.add(grailsLauncherDep);
                
            }
            else {
                unresolvedDependencies.addAll(filterDependencies(pluginProject.getDependencies(), "org.grails"));
                unresolvedDependencies.addAll(this.project.getDependencies());                
            }

            /*
                * Convert the Maven dependencies into Maven artifacts so that they can be resolved.
                */
            final List<Artifact> unresolvedArtifacts = dependenciesToArtifacts(unresolvedDependencies);

            /*
                * Resolve each artifact.  This will get all transitive artifacts AND eliminate conflicts.
                */
            for (Artifact unresolvedArtifact : unresolvedArtifacts) {
                resolvedArtifacts.addAll(resolveDependenciesToArtifacts(unresolvedArtifact, unresolvedDependencies));
            }

            /*
                * Convert each resolved artifact into a URL/classpath element.
                */
            List<String> pluginDependencies = new ArrayList<String>();
            for (Object o : pluginProject.getDependencies()) {
                Dependency d = (Dependency) o;
                pluginDependencies.add(d.getArtifactId());
            }
            pluginDependencies.addAll(BOOTSTRAP_DEPENDENCIES);
            
            Set<File> jars = new HashSet<File>();
            int index = 0;
            for (Artifact resolvedArtifact : resolvedArtifacts) {
                final File file = resolvedArtifact.getFile();
                if (file != null && !pluginOnly) {
                    jars.add(file);
                }
                else if(file != null) {

                    for (String dependency : pluginDependencies) {

                        if(file.getName().contains(dependency)) {
                            jars.add(file); break;
                        }
                    }
                }
            }

            /*
                * Add the "tools.jar" to the classpath so that the Grails scripts can run native2ascii.
                * First assume that "java.home" points to a JRE within a JDK.  NOTE that this will not
                * provide a valid path on Mac OSX.  This is not a big deal, as the JDK on Mac OSX already
                * adds the required JAR's to the classpath.  This logic is really only for Windows/*Unix.
                */
            final String javaHome = System.getProperty("java.home");
            File toolsJar = new File(javaHome, "../lib/tools.jar");
            if (!toolsJar.exists()) {
                // The "tools.jar" cannot be found with that path, so
                // now try with the assumption that "java.home" points
                // to a JDK.
                toolsJar = new File(javaHome, "tools.jar");
            }
            if (toolsJar.exists()) {
                if (toolsJar != null) {
                    jars.add(toolsJar);
                }
            }
            
            if (extraClasspathEntries != null) {
                String[] entriesArr = extraClasspathEntries.split(",");
                for (int i = 0; i < entriesArr.length; i++) {
                    // check for comma
                    String entry;
                    if (entriesArr[i].endsWith("\\") && i < entriesArr.length-1) {
                        entry = entriesArr[i] + "," + entriesArr[++i];
                    } else {
                        entry = entriesArr[i];
                    }
                    File file = new File(entry);
                    if (!file.exists()) {
                        this.getLog().warn("Grails extra classpath entry " + file + " does not exist.", new Exception());
                    }
                    jars.add(file);
                }
            }
            
            return new ArrayList<File>(jars);
        } catch (final Exception e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
    }

    private Dependency findDependency(List dependencies, String artifactId) {
        for (Object o : dependencies) {
            Dependency d = (Dependency) o;
            if(d.getArtifactId().equals(artifactId))
                return d;
        }
        return null;
    }

    private MavenProject getPluginProject() throws ProjectBuildingException {
        final Artifact pluginArtifact = findArtifact(this.project.getPluginArtifacts(), "org.grails", "grails-maven-plugin");
        return this.projectBuilder.buildFromRepository(pluginArtifact, this.remoteRepositories, this.localRepository);
    }

    /**
     * Returns only the dependencies matching the supplied group ID value, filtering out
     * all others.
     *
     * @param dependencies A list of dependencies to be filtered.
     * @param groupId      The group ID of the requested dependencies.
     * @return The filtered list of dependencies.
     */
    private List<Dependency> filterDependencies(final List<Dependency> dependencies, final String groupId) {
        final List<Dependency> filteredDependencies = new ArrayList<Dependency>();
        for (final Dependency dependency : dependencies) {
            if (dependency.getGroupId().equals(groupId)) {
                filteredDependencies.add(dependency);
            }
        }
        return filteredDependencies;
    }

    /**
     * Resolves the given Maven artifact (by getting its transitive dependencies and eliminating conflicts) against
     * the supplied list of dependencies.
     *
     * @param artifact     The artifact to be resolved.
     * @param dependencies The list of dependencies for the "project" (to aid with conflict resolution).
     * @return The resolved set of artifacts from the given artifact.  This includes the artifact itself AND its transitive artifacts.
     * @throws MojoExecutionException if an error occurs while attempting to resolve the artifact.
     */
    @SuppressWarnings("unchecked")
    private Set<Artifact> resolveDependenciesToArtifacts(final Artifact artifact, final List<Dependency> dependencies) throws MojoExecutionException {
        try {
            final MavenProject project = this.projectBuilder.buildFromRepository(artifact,
                    this.remoteRepositories,
                    this.localRepository);

            //make Artifacts of all the dependencies
            final Set<Artifact> artifacts = MavenMetadataSource.createArtifacts(this.artifactFactory, dependencies, null, null, null);

            final ArtifactResolutionResult result = artifactCollector.collect(
                    artifacts,
                    project.getArtifact(),
                    this.localRepository,
                    this.remoteRepositories,
                    this.artifactMetadataSource,
                    null,
                    Collections.EMPTY_LIST);
            artifacts.addAll(result.getArtifacts());

            //not forgetting the Artifact of the project itself
            artifacts.add(project.getArtifact());

            //resolve all dependencies transitively to obtain a comprehensive list of assemblies
            for (final Artifact currentArtifact : artifacts) {
                if (!currentArtifact.getArtifactId().equals("tools") && !currentArtifact.getGroupId().equals("com.sun")) {
                    this.artifactResolver.resolve(currentArtifact, this.remoteRepositories, this.localRepository);
                }
            }

            return artifacts;
        } catch (final Exception ex) {
            throw new MojoExecutionException("Encountered problems resolving dependencies of the executable " +
                    "in preparation for its execution.", ex);
        }
    }

    /**
     * Configures the launcher for execution.
     *
     * @param launcher The {@code GrailsLauncher} instance to be configured.
     */
    @SuppressWarnings("unchecked")
    private void configureBuildSettings(final GrailsLauncher launcher) throws ProjectBuildingException, MojoExecutionException {
        final String targetDir = this.project.getBuild().getDirectory();
        launcher.setDependenciesExternallyConfigured(true);
        launcher.setProvidedDependencies(artifactsToFiles(getProvidedArtifacts(project)));
        launcher.setCompileDependencies(artifactsToFiles(getCompileArtifacts(this.project)));
        launcher.setTestDependencies(artifactsToFiles(getTestArtifacts(project)));
        launcher.setRuntimeDependencies(artifactsToFiles(getRuntimeArtifacts(project)));
        launcher.setGrailsWorkDir(new File(grailsWorkDir));
        launcher.setProjectWorkDir(new File(targetDir));
        launcher.setClassesDir(new File(targetDir, "classes"));
        launcher.setTestClassesDir(new File(targetDir, "test-classes"));
        launcher.setResourcesDir(new File(targetDir, "resources"));
        launcher.setProjectPluginsDir(this.pluginsDir);

        final MavenProject pluginProject = getPluginProject();
        final List<Dependency> unresolvedDependencies = new ArrayList<Dependency>();
        final Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();

        unresolvedDependencies.addAll(filterDependencies(pluginProject.getDependencies(), "org.grails"));

        /*
        * Convert the Maven dependencies into Maven artifacts so that they can be resolved.
        */
        final List<Artifact> unresolvedArtifacts = dependenciesToArtifacts(unresolvedDependencies);

        /*
        * Resolve each artifact.  This will get all transitive artifacts AND eliminate conflicts.
        */
        for (Artifact unresolvedArtifact : unresolvedArtifacts) {
            resolvedArtifacts.addAll(resolveDependenciesToArtifacts(unresolvedArtifact, unresolvedDependencies));
        }
        List<File> files = artifactsToFiles(resolvedArtifacts);
        launcher.setBuildDependencies(files);
    }

    private Collection<Artifact> getRuntimeArtifacts(MavenProject project) {
        List runtimeArtifacts = project.getRuntimeArtifacts();
        List<Artifact> artifacts = new ArrayList<Artifact>(runtimeArtifacts);

        copyScopedDependenciesToTarget(project.getDependencyArtifacts(), artifacts, "runtime");
        return artifacts;
    }

    private Collection<Artifact> getTestArtifacts(MavenProject project) {
        List testArtifacts = project.getTestArtifacts();
        List<Artifact> artifacts = new ArrayList<Artifact>(testArtifacts);

        copyScopedDependenciesToTarget(project.getDependencyArtifacts(), artifacts, "test");
        return artifacts;
    }

    private Collection<Artifact> getCompileArtifacts(MavenProject project) {
        List compileArtifacts = project.getCompileArtifacts();
        List<Artifact> artifacts = new ArrayList<Artifact>(compileArtifacts);

        copyScopedDependenciesToTarget(project.getDependencyArtifacts(), artifacts, "compile");
        return artifacts;
    }

    private Collection<Artifact> getProvidedArtifacts(MavenProject project) {
        Set dependencyArtifacts = project.getDependencyArtifacts();
        List<Artifact> provided = new ArrayList<Artifact>();

        copyScopedDependenciesToTarget(dependencyArtifacts, provided, "provided");
        return provided;
    }

    private void copyScopedDependenciesToTarget(Set dependencyArtifacts, List<Artifact> targetArtifacts, String scope) {
        for (Object dependencyArtifact : dependencyArtifacts) {
            Artifact artifact = (Artifact) dependencyArtifact;

            if (artifact.getScope().equals(scope)) {
                targetArtifacts.add(artifact);
            }
        }
    }

    /**
     * Converts a collection of Maven artifacts to files.  For this method to function properly,
     * the artifacts MUST be resolved first.
     *
     * @param artifacts A collection of artifacts.
     * @return The list of files pointed to by the artifacts.
     */
    private List<File> artifactsToFiles(final Collection<Artifact> artifacts) {
        final List<File> files = new ArrayList<File>(artifacts.size());
        for (Artifact artifact : artifacts) {
            File file = artifact.getFile();
            if(file != null) {
                String name = file.getName();
                if(!name.contains("xml-apis") && !name.contains("commons-logging"))
                    files.add(file);
            }
        }

        return files;
    }

    /**
     * Finds the requested artifact in the supplied artifact collection.
     *
     * @param artifacts  A collection of artifacts.
     * @param groupId    The group ID of the artifact to be found.
     * @param artifactId The artifact ID of the artifact to be found.
     * @return The artifact from the collection that matches the group ID and
     *         artifact ID value or {@code null} if no match is found.
     */
    private Artifact findArtifact(final Collection<Artifact> artifacts, final String groupId, final String artifactId) {
        for (final Artifact artifact : artifacts) {
            if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
                return artifact;
            }
        }

        return null;
    }

    /**
     * Converts a collection of Dependency objects to a list of
     * corresponding Artifact objects.
     *
     * @param deps The collection of dependencies to convert.
     * @return A list of Artifact instances.
     */
    private List<Artifact> dependenciesToArtifacts(final Collection<Dependency> deps) {
        final List<Artifact> artifacts = new ArrayList<Artifact>(deps.size());
        for (Dependency dep : deps) {
            artifacts.add(dependencyToArtifact(dep));
        }

        return artifacts;
    }

    /**
     * Uses the injected artifact factory to convert a single Dependency
     * object into an Artifact instance.
     *
     * @param dep The dependency to convert.
     * @return The resulting Artifact.
     */
    private Artifact dependencyToArtifact(final Dependency dep) {
        return this.artifactFactory.createBuildArtifact(
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                "pom");
    }

}
