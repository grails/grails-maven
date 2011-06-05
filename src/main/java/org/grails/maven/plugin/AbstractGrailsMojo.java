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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.grails.launcher.GrailsLauncher;
import org.grails.launcher.RootLoader;
import org.grails.maven.plugin.tools.GrailsServices;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * Common services for all Mojos using Grails
 *
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @author Peter Ledbrook
 * @author Jonathan Pearlin
 * @author Andrew Keffalas
 * @version $Id$
 */
public abstract class AbstractGrailsMojo extends AbstractMojo {

    public static final String PLUGIN_PREFIX = "grails-";

    private static final String GRAILS_PLUGIN_TYPE = "grails-plugin";

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
     * Whether to run Grails in non-interactive mode or not. The default
     * is to run interactively, just like the Grails command-line.
     *
     * @parameter expression="${nonInteractive}" default-value="false"
     * @required
     */
    protected boolean nonInteractive;

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
    @SuppressWarnings("rawtypes")
    private List remoteRepositories;

    /**
     * @component
     * @readonly
     */
    private GrailsServices grailsServices;

    protected File getBasedir() {
        if(basedir == null) {
            throw new RuntimeException("Your subclass have a field called 'basedir'. Remove it and use getBasedir() " +
                "instead.");
        }

        return this.basedir;
    }

    /**
     * OutputStream to write the content of stdout.
     */
    @SuppressWarnings("unused")
    private OutputStream infoOutputStream = new OutputStream() {
        final StringBuilder buffer = new StringBuilder();

        public void write(int b) throws IOException {
            if (b == '\n') {
                getLog().info(buffer.toString());
                buffer.setLength(0);
            } else {
                buffer.append((char) b);
            }
        }
    };

    /**
     * OutputStream to write the content of stderr.
     */
    @SuppressWarnings("unused")
    private OutputStream warnOutputStream = new OutputStream() {
        final StringBuilder buffer = new StringBuilder();

        public void write(int b) throws IOException {
            if (b == '\n') {
                getLog().warn(buffer.toString());
                buffer.setLength(0);
            } else {
                buffer.append((char) b);
            }
        }
    };

    protected GrailsServices getGrailsServices() throws MojoExecutionException {
        grailsServices.setBasedir(basedir);
        return grailsServices;
    }

    protected void runGrails(String targetName) throws MojoExecutionException {
        runGrails(targetName, null, false);
    }

    protected void runGrails(String targetName, String args, boolean includeProjectDeps) throws MojoExecutionException {
        try {
            // Get the transitive set of dependencies for this project, including
            // any dependencies of Grails plugins referenced via Maven.
            final Set<Artifact> projectDependencies = getGrailsProjectDependencies();
            // Convert the Grails Home argument from a File to a String, if present.
            String grailsHomePath = null;
            if(grailsHome != null) {
                grailsHomePath = grailsHome.getPath();
            }

            // Set up the Grails executor to run the requested target.
            final RootLoader classloader = new RootLoader(buildGrailsClasspath(projectDependencies), ClassLoader.getSystemClassLoader());
            final GrailsLauncher launcher = new GrailsLauncher(classloader, grailsHomePath, basedir.getPath());
            configureBuildSettings(launcher);

            // Search for all Grails plugin dependencies and install
            // any that haven't already been installed.
            installGrailsPlugins(projectDependencies, launcher);

            // Un-comment following three lines to set output stream for debugging
//            mainClass.getDeclaredMethod("setOut", new Class[]{ PrintStream.class }).invoke(
//                    scriptRunner,
//                    new Object[] { new PrintStream(infoOutputStream) });

            // If the command is running in non-interactive mode, we
            // need to pass on the relevant argument.
            if (this.nonInteractive) {
                args = args == null ? "--non-interactive" : "--non-interactive " + args;
            }

            int retval = launcher.launch(targetName, args, env);
            if (retval != 0) {
                throw new MojoExecutionException("Grails returned non-zero value: " + retval);
            }
        } catch (MojoExecutionException ex) {
            // Simply rethrow it.
            throw ex;
        } catch (Exception ex) {
            throw new MojoExecutionException("Unable to start Grails", ex);
        }
    }

    /**
     * Fetches all the dependencies required by this project and returns
     * them as a set of Artifact instances. This method ensures that the
     * dependencies are downloaded to the local Maven cache.
     * @return The set of unique and resolved artifacts required for this execution.
     * @throws MojoExecutionException
     */
    @SuppressWarnings("unchecked")
    private Set<Artifact> getGrailsProjectDependencies() throws MojoExecutionException {
        final Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();
        final ArtifactResolutionResult result = retrieveProjectDependencies();
        if(result != null) {
            resolvedArtifacts.addAll(resolveArtifacts(result.getArtifacts()));
            getLog().info("Resolved " + resolvedArtifacts.size() + " dependencies.");
        } else {
            getLog().debug("This project does not contain any dependencies to resolve.");
        }
        return resolvedArtifacts;
    }


    /**
     * Retrieves all of the project dependencies (including transitive dependencies) from the given
     *  project.
     * @return An {@code ArtifactResolutionResult} instance containing the dependencies found in
     *  the project.
     * @throws MojoExecutionException
     */
    private ArtifactResolutionResult retrieveProjectDependencies() throws MojoExecutionException {
        // Create Maven Artifacts for each direct project dependency
        final Set<Artifact> dependencyArtifacts = createMavenArtifactsForDependencies(project.getDependencies());

        // Collect all of the transitive dependencies of this project
        return collectTransitiveMavenArtifactsForDependencies(dependencyArtifacts, project.getArtifact());
    }

    /**
     * Converts Maven Dependency objects to Maven Artifact objects.
     * @param dependencies A list of project dependencies.
     * @return A set of Maven artifacts (guaranteed to be non-null).
     * @throws MojoExecutionException
     */
    @SuppressWarnings("unchecked")
    private Set<Artifact> createMavenArtifactsForDependencies(final List<?> dependencies) throws MojoExecutionException {
        final Set<Artifact> dependencyArtifacts = new HashSet<Artifact>();
        try {
            // Create Maven Artifacts for each direct project dependency
            dependencyArtifacts.addAll(MavenMetadataSource.createArtifacts(artifactFactory, dependencies, null, null, null));

            getLog().debug("Created artifacts for direct project dependencies.");
            getLog().debug("Direct project artifacts: " + dependencyArtifacts);
        } catch(final InvalidDependencyVersionException e) {
            throw new MojoExecutionException("A dependency specified an invalid version", e);
        }

        return dependencyArtifacts;
    }

    /**
     * Collects the transitive dependencies of all of the first level dependencies of this project.
     * @param dependencyArtifacts Set of Artifacts to resolve transitive dependencies for.
     * @param originatingArtifact Original artifact that contains the direct artifacts.
     * @return A result object containing the dependencies if found.
     * @throws MojoExecutionException
     */
    private ArtifactResolutionResult collectTransitiveMavenArtifactsForDependencies(final Set<Artifact> dependencyArtifacts, final Artifact originatingArtifact) throws MojoExecutionException {
        ArtifactResolutionResult result = new ArtifactResolutionResult();
        try {
            result = artifactCollector.collect(
                        dependencyArtifacts,
                        originatingArtifact,
                        localRepository,
                        remoteRepositories,
                        artifactMetadataSource,
                        null,
                        Collections.EMPTY_LIST);
            getLog().debug("Transitive Artifacts: " + result.getArtifacts());
        } catch(final ArtifactResolutionException e) {
            throw new MojoExecutionException("Error occurred while resolving artifacts", e);
        }

        return result;
    }

    /**
     * Finds the requested artifacts in the set of transitive artifacts.  This method is used
     * to select a sub-set of the transitive dependencies of a project for further processing.
     * @param transitiveArtifacts The list of transitive artifacts extracted from the project.
     * @return The list of resolved artifacts or an empty list if there are no transitive
     *  dependency artifacts to resolve.
     * @throws MojoExecutionException
     */
    private List<Artifact> resolveArtifacts(final Set<Artifact> transitiveArtifacts) throws MojoExecutionException {
        final List<Artifact> resolvedArtifacts = new ArrayList<Artifact>();

        for(final Artifact artifact: transitiveArtifacts) {
            try {
                // Find the artifact(s) that we wish to resolve.
                if( artifact != null) {
                    artifactResolver.resolve(artifact, remoteRepositories, localRepository);
                    getLog().debug("Resolved artifact: " + artifact.getGroupId() + ":" + artifact.getArtifactId() + ":" + artifact.getVersion());
                    resolvedArtifacts.add(artifact);
                }
            } catch (final ArtifactResolutionException e) {
                throw new MojoExecutionException("Error occurred while resolving artifacts", e);
            } catch (final ArtifactNotFoundException e) {
                throw new MojoExecutionException("Artifact could not be found: " + artifact.getArtifactId(), e);
            }
        }

        return resolvedArtifacts;
    }

    @SuppressWarnings("unchecked")
    private void configureBuildSettings(final GrailsLauncher launcher)
            throws ClassNotFoundException, IllegalAccessException,
            InstantiationException, MojoExecutionException, NoSuchMethodException, InvocationTargetException {
        String targetDir = this.project.getBuild().getDirectory();
        launcher.setDependenciesExternallyConfigured(true);
        launcher.setCompileDependencies(artifactsToFiles(removePluginDependencies(this.project.getCompileArtifacts())));
        launcher.setTestDependencies(artifactsToFiles(removePluginDependencies(this.project.getTestArtifacts())));
        launcher.setRuntimeDependencies(artifactsToFiles(removePluginDependencies(this.project.getRuntimeArtifacts())));
        launcher.setProjectWorkDir(new File(targetDir));
        launcher.setClassesDir(new File(targetDir, "classes"));
        launcher.setTestClassesDir(new File(targetDir, "test-classes"));
        launcher.setResourcesDir(new File(targetDir, "resources"));
        launcher.setProjectPluginsDir(this.pluginsDir);
    }

    /**
     * Installs a Grails plugin into the current project if it isn't
     * already installed. It works by simply unpacking the plugin
     * artifact (a ZIP file) into the appropriate location and adding
     * the plugin to the application's metadata.
     * @param plugin The plugin artifact to install.
     * @param metadata The application metadata. An entry for the plugin
     * is added to this if the installation is successful.
     * @param launcher The launcher instance that contains information about
     * the various project directories. In particular, this is where the
     * method gets the location of the project's "plugins" directory
     * from.
     * @return <code>true</code> if the plugin is installed and the
     * metadata updated, otherwise <code>false</code>.
     * @throws IOException
     * @throws ArchiverException
     */
    private boolean installGrailsPlugin(
            final Artifact plugin,
            final Metadata metadata,
            final GrailsLauncher launcher) throws IOException, ArchiverException {
        String pluginName = plugin.getArtifactId();
        final String pluginVersion = plugin.getVersion();

        if (pluginName.startsWith(PLUGIN_PREFIX)) {
            pluginName = pluginName.substring(PLUGIN_PREFIX.length());
        }
        getLog().info("Installing plugin " + pluginName + ":" + pluginVersion);

        // The directory the plugin will be unzipped to.
        final File targetDir = new File(launcher.getProjectPluginsDir(), pluginName + "-" + pluginVersion);

        // Unpack the plugin if it hasn't already been.
        if (!targetDir.exists()) {
            targetDir.mkdirs();

            final ZipUnArchiver unzipper = new ZipUnArchiver();
            unzipper.enableLogging(new ConsoleLogger(Logger.LEVEL_ERROR, "zip-unarchiver"));
            unzipper.setSourceFile(plugin.getFile());
            unzipper.setDestDirectory(targetDir);
            unzipper.setOverwrite(true);
            unzipper.extract();

            // Now add it to the application metadata.
            getLog().debug("Updating project metadata");
            metadata.setProperty("plugins." + pluginName, pluginVersion);
            return true;
        } else {
            return false;
        }
    }

    private List<File> artifactsToFiles(Collection<Artifact> artifacts) {
        final List<File> files = new ArrayList<File>(artifacts.size());
        for (final Artifact artifact : artifacts) {
            files.add(artifact.getFile());
        }

        return files;
    }

    /**
     * Constructs the classpath that will be given to Grails to perform the requested operations.
     * The classpath is constructed by converting the Maven dependencies for this project into file
     * paths.
     * @param projectDependencies The set of resolved dependencies for this project.
     * @return An array of dependency file URL's that will represent the classpath.
     * @throws MalformedURLException
     */
    private URL[] buildGrailsClasspath(final Set<Artifact> projectDependencies) throws MalformedURLException {
        // Add 1 to the size to account for the tools.jar dependency.
        final URL[] classpath = new URL[projectDependencies.size() + 1];
        int index = 0;

        //add paths to dependencies to the classpath
        for (final Artifact artifact : projectDependencies) {
            // System scoped dependencies require a little extra TLC to get them on to the classpath.
            if(Artifact.SCOPE_SYSTEM.equals(artifact.getScope())) {
                String path = artifact.getFile().getPath();
                //If the path points to a non jar resource then include the path to the resource on the classpath
                //An alternative would be to copy the non jar resource into target/resources.
                if (!path.endsWith(".jar")) {
                    path = path.substring(0, path.lastIndexOf("/"));
                }
                classpath[index++] = new File(path).toURI().toURL();
            } else {
                classpath[index++] = artifact.getFile().toURI().toURL();
            }
        }

        // Add the "tools.jar" to the classpath so that the Grails
        // scripts can run native2ascii. First assume that "java.home"
        // points to a JRE within a JDK.
        String javaHome = System.getProperty("java.home");
        File toolsJar = new File(javaHome, "../lib/tools.jar");
        if (!toolsJar.exists()) {
            // The "tools.jar" cannot be found with that path, so
            // now try with the assumption that "java.home" points
            // to a JDK.
            toolsJar = new File(javaHome, "tools.jar");
        }
        classpath[classpath.length - 1] = toolsJar.toURI().toURL();
        return classpath;
    }

    /**
     * Installs all of the Grails plugins found within the Maven dependencies for this project.
     * @param projectDependencies The resolved dependency set for this project.
     * @param launcher The GrailsLauncher environment launcher.
     * @throws IOException
     * @throws ArchiverException
     */
    private void installGrailsPlugins(final Set<Artifact> projectDependencies, final GrailsLauncher launcher) throws IOException, ArchiverException {
        // Search for all Grails plugin dependencies and install
        // any that haven't already been installed.
        Metadata metadata = Metadata.getInstance(new File(getBasedir(), "application.properties"));
        boolean metadataModified = false;

        for (final Artifact artifact : projectDependencies) {
            if (artifact.getType() != null && (artifact.getType().equals(GRAILS_PLUGIN_TYPE) || artifact.getType().equals("zip"))) {
                metadataModified |= installGrailsPlugin(artifact, metadata,  launcher);
            }
        }

        if (metadataModified) {
            metadata.persist();
        }
    }

    /**
     * Removes any Grails plugin dependencies from the supplied list
     * of dependencies.  A Grails plugin is any dependency whose type
     * is equal to "grails-plugin" or "zip".
     * @param dependencies The list of dependencies to be cleansed.
     * @return The cleansed list of dependencies with all Grails plugin
     *   dependencies removed.
     */
    private List<Artifact> removePluginDependencies(final List<Artifact> dependencies) {
        if(dependencies != null) {
            for (final Iterator<Artifact> iter = dependencies.iterator(); iter.hasNext();) {
                final Artifact dep = (Artifact) iter.next();
                if (dep.getType() != null && (dep.getType().equals(GRAILS_PLUGIN_TYPE) || dep.getType().equals("zip"))) {
                    iter.remove();
                }
            }
        }
        return dependencies;
    }
}