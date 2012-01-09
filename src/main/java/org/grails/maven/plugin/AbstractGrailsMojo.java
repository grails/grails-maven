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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import jline.Terminal;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectBuilder;
import org.apache.maven.project.artifact.MavenMetadataSource;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.logging.console.ConsoleLogger;
import org.grails.launcher.GrailsLauncher;
import org.grails.launcher.RootLoader;
import org.grails.maven.plugin.tools.GrailsServices;

/**
 * Common services for all Mojos using Grails
 *
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @author Peter Ledbrook
 * @author Jonathan Pearlin
 * @version $Id$
 */
public abstract class AbstractGrailsMojo extends AbstractMojo {

    public static final String PLUGIN_PREFIX = "grails-";

	private static final String GRAILS_PLUGIN_NAME_FORMAT = "plugins.%s:%s";

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
    private List<?> remoteRepositories;

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
     * @return The base directory.
     */
    protected File getBasedir() {
        if(basedir == null) {
            throw new RuntimeException("Your subclass have a field called 'basedir'. Remove it and use getBasedir() " +
                "instead.");
        }

        return this.basedir;
    }

    /**
     * Returns the {@code GrailsServices} instance used by the plugin with the base directory
     * of the services object set to the configured base directory.
     * @return The underlying {@code GrailsServices} instance.
     */
    protected GrailsServices getGrailsServices()  {
        grailsServices.setBasedir(basedir);
        return grailsServices;
    }

    /**
     * Executes the requested Grails target.  The "targetName" must match a known
     * Grails script provided by grails-scripts.
     * @param targetName The name of the Grails target to execute.
     * @throws MojoExecutionException if an error occurs while attempting to execute the target.
     */
    protected void runGrails(final String targetName) throws MojoExecutionException {
        runGrails(targetName, null);
    }

    /**
     * Executes the requested Grails target.  The "targetName" must match a known
     * Grails script provided by grails-scripts.
     * @param targetName The name of the Grails target to execute.
     * @param args String of arguments to be passed to the executed Grails target.
     * @throws MojoExecutionException if an error occurs while attempting to execute the target.
     */
    protected void runGrails(final String targetName, String args) throws MojoExecutionException {
        InputStream currentIn = System.in;
        PrintStream currentOutput = System.out;
        try {
            final URL[] classpath = generateGrailsExecutionClasspath();

            final String grailsHomePath = (grailsHome != null) ? grailsHome.getAbsolutePath() : null;
            final RootLoader rootLoader = new RootLoader(classpath, ClassLoader.getSystemClassLoader());
            final GrailsLauncher launcher = new GrailsLauncher(rootLoader, grailsHomePath, basedir.getAbsolutePath());
            launcher.setPlainOutput(true);
            configureBuildSettings(launcher);

            // Search for all Grails plugin dependencies and install
            // any that haven't already been installed.
            final Metadata metadata = Metadata.getInstance(new File(getBasedir(), "application.properties"));
            boolean metadataModified = false;
            for(@SuppressWarnings("unchecked")
            final Iterator<Artifact> iter = this.project.getDependencyArtifacts().iterator(); iter.hasNext();) {
                final Artifact artifact = iter.next();
                if (artifact.getType() != null && (artifact.getType().equals("grails-plugin") || artifact.getType().equals("zip"))) {
                    metadataModified |= installGrailsPlugin(artifact, metadata,  launcher);
                }
            }

            if (metadataModified) 
				metadata.persist();

            // If the command is running in non-interactive mode, we
            // need to pass on the relevant argument.
            if (this.nonInteractive) {
                args = (args != null) ? "--non-interactive" + args : "--non-interactive ";
            }

			// Enable the plain output for the Grails command to fix an issue with JLine
			// consuming the standard output after execution via Maven.
			args = (args != null) ? "--plain-output " + args : "--plain-output";

            final int retval = launcher.launch(targetName, args, env);
            if (retval != 0) {
                throw new MojoExecutionException("Grails returned non-zero value: " + retval);
            }
        } catch (final MojoExecutionException ex) {
            // Simply rethrow it.
            throw ex;
        } catch (final Exception ex) {
            throw new MojoExecutionException("Unable to start Grails", ex);
        }
        finally {
            Terminal.resetTerminal();
            System.setIn(currentIn);
            System.setOut(currentOutput);
        }
    }

    /**
     * Generates the classpath to be used by the launcher to execute the requested Grails script.
     * @return An array of {@code URL} objects representing the dependencies required on the classpath to
     * 	execute the selected Grails script.
     * @throws MojoExecutionException if an error occurs while attempting to resolve the dependencies and
     * 	generate the classpath array.
     */
    @SuppressWarnings("unchecked")
    private URL[] generateGrailsExecutionClasspath() throws MojoExecutionException {
        try {
            final List<Dependency> unresolvedDependencies = new ArrayList<Dependency>();
            final Set<Artifact> resolvedArtifacts = new HashSet<Artifact>();

            /*
             * Get the Grails dependencies from the plugin's POM file first.
             */
            final Artifact pluginArtifact = findArtifact(this.project.getPluginArtifacts(), "org.grails", "grails-maven-plugin");
            final MavenProject pluginProject = this.projectBuilder.buildFromRepository(pluginArtifact, this.remoteRepositories, this.localRepository);

            /*
             * Add the plugin's dependencies and the project using the plugin's dependencies to the list
             * of unresolved dependencies.  This is done so they can all be resolved at the same time so
             * that we get the benefit of Maven's conflict resolution.
             */
            unresolvedDependencies.addAll(filterDependencies(pluginProject.getDependencies(), "org.grails"));
            unresolvedDependencies.addAll(this.project.getDependencies());

            /*
             * Convert the Maven dependencies into Maven artifacts so that they can be resolved.
             */
            final List<Artifact> unresolvedArtifacts = dependenciesToArtifacts(unresolvedDependencies);

            /*
             * Resolve each artifact.  This will get all transitive artifacts AND eliminate conflicts.
             */
            for (final Iterator<Artifact> iter = unresolvedArtifacts.iterator(); iter.hasNext();) {
                resolvedArtifacts.addAll(resolveDependenciesToArtifacts(iter.next(),unresolvedDependencies));
            }

            /*
             * Remove any Grails plugins that may be in the resolved artifact set.  This is because we
             * do not need them on the classpath, as they will be handled later on by a separate call to
             * "install" them.
             */
            removePluginArtifacts(resolvedArtifacts);

            /*
             * Convert each resolved artifact into a URL/classpath element.
             */
            final URL[] classpath = new URL[resolvedArtifacts.size() + 1];
            int index = 0;
            for (final Iterator<Artifact> iter = resolvedArtifacts.iterator(); iter.hasNext();) {
                final File file = iter.next().getFile();
                if(file != null) {
                    classpath[index++] = file.toURI().toURL();
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
            classpath[classpath.length - 1] = toolsJar.toURI().toURL();

            return classpath;
        } catch(final Exception e) {
            throw new MojoExecutionException("Failed to create classpath for Grails execution.", e);
        }
    }

    /**
     * Returns only the dependencies matching the supplied group ID value, filtering out
     * all others.
     * @param dependencies A list of dependencies to be filtered.
     * @param groupId The group ID of the requested dependencies.
     * @return The filtered list of dependencies.
     */
    private List<Dependency> filterDependencies(final List<Dependency> dependencies, final String groupId) {
        final List<Dependency> filteredDependencies = new ArrayList<Dependency>();
        for(final Iterator<Dependency> iter = dependencies.iterator(); iter.hasNext();) {
            final Dependency dependency = iter.next();
            if(dependency.getGroupId().equals(groupId)) {
                filteredDependencies.add(dependency);
            }
        }
        return filteredDependencies;
    }

    /**
     * Resolves the given Maven artifact (by getting its transitive dependencies and eliminating conflicts) against
     * the supplied list of dependencies.
     * @param artifact The artifact to be resolved.
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
            for (final Iterator<Artifact> iter = artifacts.iterator(); iter.hasNext();) {
                final Artifact currentArtifact = iter.next();
                if(!currentArtifact.getArtifactId().equals("tools") && !currentArtifact.getGroupId().equals("com.sun")) {
                    this.artifactResolver.resolve(currentArtifact, this.remoteRepositories, this.localRepository);
                }
            }

            return artifacts;
        } catch ( final Exception ex ) {
            throw new MojoExecutionException("Encountered problems resolving dependencies of the executable " +
                                             "in preparation for its execution.", ex);
        }
    }

    /**
     * Configures the launcher for execution.
     * @param launcher The {@code GrailsLauncher} instance to be configured.
     */
    @SuppressWarnings("unchecked")
    private void configureBuildSettings(final GrailsLauncher launcher) {
        final String targetDir = this.project.getBuild().getDirectory();
        launcher.setDependenciesExternallyConfigured(true);
        launcher.setCompileDependencies(artifactsToFiles(this.project.getCompileArtifacts()));
        launcher.setTestDependencies(artifactsToFiles(this.project.getTestArtifacts()));
        launcher.setRuntimeDependencies(artifactsToFiles(this.project.getRuntimeArtifacts()));
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
			metadata.setProperty(String.format(GRAILS_PLUGIN_NAME_FORMAT, plugin.getGroupId(), pluginName), pluginVersion);
            return true;
        } else {
            return false;
        }
    }

    /**
     * Converts a collection of Maven artifacts to files.  For this method to function properly,
     * the artifacts MUST be resolved first.
     * @param artifacts A collection of artifacts.
     * @return The list of files pointed to by the artifacts.
     */
    private List<File> artifactsToFiles(final Collection<Artifact> artifacts) {
        final List<File> files = new ArrayList<File>(artifacts.size());
        for (final Iterator<Artifact> iter = artifacts.iterator(); iter.hasNext();) {
            files.add(iter.next().getFile());
        }

        return files;
    }

    /**
     * Finds the requested artifact in the supplied artifact collection.
     * @param artifacts A collection of artifacts.
     * @param groupId The group ID of the artifact to be found.
     * @param artifactId The artifact ID of the artifact to be found.
     * @return The artifact from the collection that matches the group ID and
     * 	artifact ID value or {@code null} if no match is found.
     */
    private Artifact findArtifact(final Collection<Artifact> artifacts, final String groupId, final String artifactId) {
        for (final Iterator<Artifact> iter = artifacts.iterator(); iter.hasNext();) {
            final Artifact artifact = iter.next();
            if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
                return artifact;
            }
        }

        return null;
    }

    /**
     * Converts a collection of Dependency objects to a list of
     * corresponding Artifact objects.
     * @param deps The collection of dependencies to convert.
     * @return A list of Artifact instances.
     */
    private List<Artifact> dependenciesToArtifacts(final Collection<Dependency> deps) {
        final List<Artifact> artifacts = new ArrayList<Artifact>(deps.size());
        for (final Iterator<Dependency> iter = deps.iterator(); iter.hasNext();) {
            artifacts.add(dependencyToArtifact(iter.next()));
        }

        return artifacts;
    }

    /**
     * Uses the injected artifact factory to convert a single Dependency
     * object into an Artifact instance.
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

    /**
     * Removes any Grails plugin artifacts from the supplied list
     * of dependencies.  A Grails plugin is any artifact whose type
     * is equal to "grails-plugin" or "zip".
     * @param artifact The list of artifacts to be cleansed.
     */
    private void removePluginArtifacts(final Set<Artifact> artifact) {
        if(artifact != null) {
            for (final Iterator<Artifact> iter = artifact.iterator(); iter.hasNext();) {
                final Artifact dep = iter.next();
                if (dep.getType() != null && (dep.getType().equals("grails-plugin") || dep.getType().equals("zip"))) {
                    iter.remove();
                }
            }
        }
    }
}
