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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.*;
import org.apache.maven.settings.Proxy;
import org.apache.maven.settings.Settings;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.util.filter.AndDependencyFilter;
import org.eclipse.aether.util.filter.ExclusionsDependencyFilter;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.grails.maven.plugin.tools.ForkedGrailsRuntime;
import org.grails.maven.plugin.tools.GrailsServices;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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
    public static final String APP_GRAILS_VERSION = "app.grails.version";
    public static final String APP_VERSION = "app.version";
    public static final String SPRING_LOADED_VERSION = "1.2.0.RELEASE";
    public static final List<String> COMPILE_PLUS_RUNTIME_SCOPE = Arrays.asList("compile", "runtime");

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
     * The Grails environment to use.
     *
     * @parameter expression="${grailsVersion}"
     */
    protected String grailsVersion;

    /**
     * The version of Groovy used
     */
    private String groovyVersion;

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
     * @parameter expression="${nonInteractive}" default-value="true"
     * @required
     */
    protected boolean nonInteractive = true;

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
     * The current repository/network configuration of Maven.
     *
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    private ArtifactRepository localRepository;

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
     * @readonly
     */
    private GrailsServices grailsServices;

    /**
     * Utility for resolving dependencies from Maven
     *
     * @component
     */
    private ProjectDependenciesResolver projectDependenciesResolver;

    /**
     * The entry point to Aether, i.e. the component doing all the work.
     *
     * @component
     */
    private RepositorySystem repoSystem;


    /**
     * For building metadata about projects
     *
     * @component
     */
    private ProjectBuilder projectBuilder;

    /**
     * The current repository/network configuration of Maven.
     *
     * @parameter default-value="${repositorySystemSession}"
     * @readonly
     */
    private RepositorySystemSession repoSession;

    /**
     * The project's remote repositories to use for the resolution of plugins and their dependencies.
     *
     * @parameter default-value="${project.remoteProjectRepositories}"
     * @readonly
     */
    private List<RemoteRepository> remoteRepos;


    protected AbstractGrailsMojo() {
    }


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
        configureMavenProxy();

        final String targetDir = this.project.getBuild().getDirectory();
        ForkedGrailsRuntime.ExecutionContext ec = new ForkedGrailsRuntime.ExecutionContext();
        ec.setBuildDependencies( resolveBuildDependencies() );
        List<File> providedDependencies = resolveArtifacts("provided");
        List<File> compileDependencies = resolveArtifacts("compile");

        List<File> runtimeDependencies = resolveArtifacts(COMPILE_PLUS_RUNTIME_SCOPE);

        Set<File> testDependencies = new HashSet<File>( resolveArtifacts("test") );
        testDependencies.addAll( providedDependencies );
        testDependencies.addAll( compileDependencies );
        testDependencies.addAll(runtimeDependencies);
        ec.setProvidedDependencies(providedDependencies);
        ec.setRuntimeDependencies(new ArrayList<File>(runtimeDependencies));
        ec.setCompileDependencies(compileDependencies);
        ec.setTestDependencies(new ArrayList<File>(testDependencies));

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
            File springLoadedJar = resolveArtifact("org.springframework:springloaded:" + SPRING_LOADED_VERSION);
            if(springLoadedJar != null) {
                fgr.setReloadingAgent(springLoadedJar);
            }else{
                getLog().warn("Grails Reloading: org.springframework:springloaded:"+SPRING_LOADED_VERSION+" not found");
                getLog().error("Grails Reloading: not enabled");
            }
        }
        fgr.setDebug(forkDebug);
        fgr.setMaxMemory(forkMaxMemory);
        fgr.setMaxPerm(forkPermGen);
        fgr.setMinMemory(forkMinMemory);
        try {
            handleVersionSync();
            getLog().info("Starting " + (fork ? "forked" : "unforked") + " grails project.");
            if(fork) {
                fgr.fork();
            } else {
                fgr.nofork();
            }
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    protected Collection<File> resolveArtifactIds(Collection<String> artifactIds) throws MojoExecutionException {
        Collection<ArtifactRequest> requests = new ArrayList<ArtifactRequest>();

        for (String artifactId : artifactIds) {
            ArtifactRequest request = new ArtifactRequest();
            request.setArtifact(
                    new DefaultArtifact(artifactId));
            request.setRepositories( remoteRepos );

            getLog().debug("Resolving artifact " + artifactId +
                    " from " + remoteRepos);

            requests.add(request);
        }

        Collection<File> files = new ArrayList<File>();
        try
        {
            List<ArtifactResult> result = repoSystem.resolveArtifacts(repoSession, requests);

            for (ArtifactResult artifactResult : result) {

                File file = artifactResult.getArtifact().getFile();
                files.add(file);
                getLog().debug("Resolved artifact " + artifactResult.getArtifact().getArtifactId() + " to " +
                        file + " from "
                        + artifactResult.getRepository());

            }
        } catch ( ArtifactResolutionException e ) {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        return files;
    }

    protected File resolveArtifact(String artifactId) throws MojoExecutionException {
        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact(
                new DefaultArtifact(artifactId));
        request.setRepositories( remoteRepos );

        getLog().debug("Resolving artifact " + artifactId +
                " from " + remoteRepos);

        ArtifactResult result;
        File file;
        try
        {
            result = repoSystem.resolveArtifact( repoSession, request );
            file = result.getArtifact().getFile();
        } catch ( ArtifactResolutionException e ) {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        getLog().debug("Resolved artifact " + artifactId + " to " +
                file + " from "
                + result.getRepository());
        return file;
    }



    /**
     * Resolves artifacts to files including transitive resolution
     *
     * @return
     * @throws MojoExecutionException
     */
    protected List<File> resolveArtifacts() throws MojoExecutionException {
        return resolveArtifacts( "compile");
    }

    protected List<File> resolveArtifacts(Collection<String> scopes) throws MojoExecutionException {
        MavenProject mavenProject = project;
        return resolveArtifacts(mavenProject, scopes);
    }

    protected List<File> resolveArtifacts(String scope) throws MojoExecutionException {
        MavenProject mavenProject = project;
        return resolveArtifacts(mavenProject, Arrays.asList(scope));
    }

    protected List<File> resolveArtifacts(MavenProject mavenProject, Collection<String> scopes) throws MojoExecutionException {
        return resolveArtifacts(mavenProject, scopes, null);
    }

    protected List<File> resolveArtifacts(MavenProject mavenProject, Collection<String> scopes, DependencyFilter filter) throws MojoExecutionException {
        try {
            DefaultDependencyResolutionRequest request = new DefaultDependencyResolutionRequest(mavenProject, repoSession);
            if(filter != null) {
                request.setResolutionFilter(new AndDependencyFilter(new ScopeDependencyFilter(scopes, Collections.<String>emptyList()), filter));
            }
            else {
                request.setResolutionFilter(new ScopeDependencyFilter(scopes, Collections.<String>emptyList()));
            }
            DependencyResolutionResult result = projectDependenciesResolver.resolve(request);
            List<org.eclipse.aether.graph.Dependency> dependencies = result.getDependencies();

            final List<File> files = new ArrayList<File>();

            for(org.eclipse.aether.graph.Dependency d : dependencies) {
                org.eclipse.aether.artifact.Artifact artifact = d.getArtifact();
                File file = artifact.getFile();
                if(file != null) {
                    String name = file.getName();
                    if(!name.contains("xml-apis") && !name.contains("commons-logging"))
                        files.add(file);
                }

            }
            return files;
        } catch (DependencyResolutionException e) {
            throw new MojoExecutionException("Dependency resolution failure: " + e.getMessage(), e);
        }
    }

    protected List<File> resolveBuildDependencies() throws MojoExecutionException {
        try {

            /*
                 * Get the Grails dependencies from the plugin's POM file first.
                */
            final MavenProject pluginProject = getPluginProject();

            /*
                * Add the plugin's dependencies and the project using the plugin's dependencies to the list
                * of unresolved dependencies.  This is done so they can all be resolved at the same time so
                * that we get the benefit of Maven's conflict resolution.
                */

            Set<File> jars = new HashSet<File>();
            // calculate the Grails version to use from the dependency or grailsVersion setting
            String grailsVersion = establishGrailsVersion();
            String groovyVersion = establishGroovyVersion();

            if(grailsVersion != null) {
                String scriptsId = "org.grails:grails-scripts:" + grailsVersion;
                String bootstrapId = "org.grails:grails-bootstrap:" + grailsVersion;
                String groovyId = "org.codehaus.groovy:groovy-all:" + groovyVersion;
                jars.addAll(resolveArtifactIds(Arrays.asList(scriptsId, bootstrapId, groovyId)));
            }

            jars.addAll(resolveArtifacts(pluginProject, COMPILE_PLUS_RUNTIME_SCOPE, new ExclusionsDependencyFilter(Arrays.asList("org.grails:grails-bootstrap", "org.codehaus.groovy:groovy-all", "org.codehaus.groovy:groovy"))));

            findAndAddToolsJar(jars);
            addExtraClassPathEntries(jars);

            return new ArrayList<File>(jars);
        } catch (final Exception e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
    }

    protected String establishGroovyVersion() throws ProjectBuildingException {
        if(this.groovyVersion == null) {
            Artifact groovyDependency = findGroovyDependency(project);
            if(groovyDependency != null) {
                this.groovyVersion = groovyDependency.getVersion();
            }
            else {
                String grailsVersion = establishGrailsVersion();
                if(grailsVersion.startsWith("2.3")) {
                    this.groovyVersion = "2.1.9";
                }
                else {
                    this.groovyVersion = findGroovyVersionFromPlugin();
                    if(this.groovyVersion == null) {
                        this.groovyVersion = "2.3.3";
                    }
                }
            }
        }
        return this.groovyVersion;
    }

    protected String establishGrailsVersion() throws ProjectBuildingException {
        if(this.grailsVersion == null) {
            Artifact grailsDependency = findGrailsDependency(project);
            if(grailsDependency != null) {
                this.grailsVersion = grailsDependency.getVersion();
            }
            else {
                this.grailsVersion = findGrailsVersionFromPlugin();
            }
        }
        return this.grailsVersion;
    }

    private String findGrailsVersionFromPlugin() throws ProjectBuildingException {
        return findArtefactVersionFromPlugin("org.grails", "grails-bootstrap");
    }

    private String findGroovyVersionFromPlugin() throws ProjectBuildingException {
        return findArtefactVersionFromPlugin("org.codehaus.groovy", "groovy");
    }

    private String findArtefactVersionFromPlugin(String group, String name) throws ProjectBuildingException {
        MavenProject pluginProject = getPluginProject();
        Set<Artifact> dependencyArtifacts = pluginProject.getArtifacts();
        if(dependencyArtifacts != null) {
            for (Artifact artifact : dependencyArtifacts) {
                if (artifact.getArtifactId().equals(name) &&
                        artifact.getGroupId().equals(group)) {
                    return artifact.getVersion();
                }
            }
        }
        return pluginProject.getVersion();
    }


    private void handleVersionSync() throws MojoExecutionException {
        // Search for all Grails plugin dependencies and install
        // any that haven't already been installed.
        final Properties metadata = new Properties();

        File metadataFile = new File(getBasedir(), "application.properties");
        if(metadataFile.exists()) {
            FileReader reader = null;
            FileWriter writer = null;
            try {
                reader = new FileReader(metadataFile);
                metadata.load(reader);

                boolean metadataModified = syncVersions(metadata);

                if (metadataModified) {

                    writer = new FileWriter(metadataFile);
                    metadata.store(writer, "Grails Metadata file");
                }
            } catch (IOException e) {
                throw new MojoExecutionException("Failed to sync application version with Maven plugin defined version");
            } finally {
                try {
                    if(reader != null)
                        reader.close();
                    if(writer != null)
                        writer.close();
                } catch (IOException e) {
                    // ignore
                }
            }


        }
    }

    private boolean syncVersions(Properties metadata) {

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
                metadata.put(APP_VERSION, project.getVersion());
                result = true;
            }
        }

        return result;
    }


    private Artifact findGrailsDependency(MavenProject project) {
        Set dependencyArtifacts = project.getDependencyArtifacts();
        for (Object o : dependencyArtifacts) {
            Artifact artifact = (Artifact) o;
            if (artifact.getArtifactId().equals("grails-dependencies") &&
                artifact.getGroupId().equals("org.grails")) {
                return artifact;
            }
        }
        return null;
    }

    private Artifact findGroovyDependency(MavenProject project) {
        Set dependencyArtifacts = project.getDependencyArtifacts();
        for (Object o : dependencyArtifacts) {
            Artifact artifact = (Artifact) o;
            String groupId = artifact.getGroupId();
            String artifactId = artifact.getArtifactId();

            if (groupId.equals("org.codehaus.groovy") &&
                    (artifactId.equals("groovy-all") || artifactId.equals("groovy"))) {
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



    private void addExtraClassPathEntries(Set<File> jars) {
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
                    this.getLog().warn("Grails extra classpath entry " + file + " does not exist.");
                }
                jars.add(file);
            }
        }
    }

    /*
    * Add the "tools.jar" to the classpath so that the Grails scripts can run native2ascii.
    * First assume that "java.home" points to a JRE within a JDK.  NOTE that this will not
    * provide a valid path on Mac OSX.  This is not a big deal, as the JDK on Mac OSX already
    * adds the required JAR's to the classpath.  This logic is really only for Windows/*Unix.
    */
    private void findAndAddToolsJar(Set<File> jars) {
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
    }


    private MavenProject getPluginProject() throws ProjectBuildingException {
        final Artifact pluginArtifact = findArtifact(this.project.getPluginArtifacts(), "org.grails", "grails-maven-plugin");

        DefaultProjectBuildingRequest request = new DefaultProjectBuildingRequest();

        request.setLocalRepository(localRepository);
        return projectBuilder.build(pluginArtifact, request).getProject();
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


}
