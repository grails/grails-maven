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
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

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
import org.apache.maven.project.ProjectBuildingException;
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
 * @version $Id$
 */
public abstract class AbstractGrailsMojo extends AbstractMojo {

    public static final String PLUGIN_PREFIX = "grails-";

    private static final String SYSTEM_SCOPE = "system";

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
    private List remoteRepositories;

    /**
     * @component
     */
    private MavenProjectBuilder projectBuilder;

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
    private OutputStream infoOutputStream = new OutputStream() {
        StringBuffer buffer = new StringBuffer();

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
    private OutputStream warnOutputStream = new OutputStream() {
        StringBuffer buffer = new StringBuffer();

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
        // First get the dependencies specified by the plugin.
        Set deps = getGrailsPluginDependencies();

        // Add any system dependencies if necessary.
        List systemDeps = new ArrayList();
        try {
            Iterator dependancies = this.project.getDependencies().iterator();
            while (dependancies.hasNext()) {
                Dependency dep = (Dependency) dependancies.next();
                if (SYSTEM_SCOPE.equals(dep.getScope())) {
                    systemDeps.add(dep.getSystemPath());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Now add the project dependencies if necessary.
        if (includeProjectDeps) {
            deps.addAll(this.project.getRuntimeArtifacts());
        }

        URL[] classpath;
        try {
            classpath = new URL[deps.size() + systemDeps.size() +1];
            int index = 0;
            for (Iterator iter = deps.iterator(); iter.hasNext();) {
                classpath[index++] = ((Artifact) iter.next()).getFile().toURI().toURL();
            }
            //add paths to system dependencies to the classpath
            for (Iterator iter = systemDeps.iterator(); iter.hasNext();) {
                String path = (String) iter.next();
                //If the path points to a non jar resource then include the path to the resource on the classpath
                //An alternative would be to copy the non jar resource into target/resources.
                if (!path.endsWith(".jar")) {
                    path = path.substring(0, path.lastIndexOf("/"));
                }
                classpath[index++] = new File(path).toURI().toURL();
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

            // Convert the Grails Home argument from a File to a String, if present.
            String grailsHomePath = null;
            if(grailsHome != null) {
                grailsHomePath = grailsHome.getPath();
            }

            // Set up the Grails executor to run the requested target.
            final RootLoader classloader = new RootLoader(classpath);
            final GrailsLauncher launcher = new GrailsLauncher(classloader, grailsHomePath, basedir.getPath());
            configureBuildSettings(launcher);

            // Search for all Grails plugin dependencies and install
            // any that haven't already been installed.
            Metadata metadata = Metadata.getInstance(new File(getBasedir(), "application.properties"));
            boolean metadataModified = false;
            for (Iterator iter = deps.iterator(); iter.hasNext();) {
                Artifact dep = (Artifact) iter.next();
                if (dep.getType() != null && (dep.getType().equals("grails-plugin") || dep.getType().equals("zip"))) {
                    metadataModified |= installGrailsPlugin(dep, metadata,  launcher);
                }
            }

            if (metadataModified) metadata.persist();

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
     * Fetches all the dependencies required by this plugin and returns
     * them as a set of Artifact instances. This method ensures that the
     * dependencies are downloaded to the local Maven cache.
     * @return
     * @throws MojoExecutionException
     */
    private Set getGrailsPluginDependencies() throws MojoExecutionException {
        Artifact pluginArtifact = findArtifact(this.project.getPluginArtifacts(), "org.grails", "grails-maven-plugin");
        MavenProject project = null;
        try {
            project = this.projectBuilder.buildFromRepository(pluginArtifact,
                                                              this.remoteRepositories,
                                                              this.localRepository);
        } catch (ProjectBuildingException ex) {
            throw new MojoExecutionException("Failed to get information about Grails Maven Plugin", ex);
        }

        // Extract the Grails dependencies from the project. We want
        // to know what version of Grails to link in.
        Dependency firstDep = null;
        for (Iterator iter = this.project.getDependencies().iterator(); iter.hasNext();) {
            Dependency d = (Dependency) iter.next();
            if ("org.grails".equals(d.getGroupId())) {
                firstDep = d;
                break;
            }
        }

        List pluginDeps = project.getDependencies();
        if (firstDep != null) {
            String grailsVersion = firstDep.getVersion();
            getLog().info("Using Grails " + grailsVersion);

            List grailsDeps = new ArrayList();
            for (Iterator iter = pluginDeps.iterator(); iter.hasNext();) {
                Dependency d = (Dependency) iter.next();
                if ("org.grails".equals(d.getGroupId()) && !"grails-maven-archetype".equals(d.getArtifactId())) {
                    d.setVersion(grailsVersion);
                    grailsDeps.add(d);
                }
            }

            pluginDeps = grailsDeps;
        }

        List deps = artifactsByGroupId(dependenciesToArtifacts(pluginDeps), "org.grails");
        Set pluginDependencies = new HashSet();
        for (Iterator iter = deps.iterator(); iter.hasNext();) {
            pluginDependencies.addAll(getPluginDependencies((Artifact) iter.next()));
        }

        return pluginDependencies;
    }

    private void configureBuildSettings(final GrailsLauncher launcher)
            throws ClassNotFoundException, IllegalAccessException, InstantiationException,
            MojoExecutionException, NoSuchMethodException, InvocationTargetException {
        final String targetDir = this.project.getBuild().getDirectory();
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

    private Set getPluginDependencies(Artifact pom) throws MojoExecutionException {
        try {
            MavenProject project = this.projectBuilder.buildFromRepository(pom,
                                                                           this.remoteRepositories,
                                                                           this.localRepository);

            //get all of the dependencies for the executable project
            List dependencies = project.getDependencies();

            //make Artifacts of all the dependencies
            Set dependencyArtifacts =
                MavenMetadataSource.createArtifacts(this.artifactFactory, dependencies, null, null, null);

            ArtifactResolutionResult result = artifactCollector.collect(
                    dependencyArtifacts,
                    project.getArtifact(),
                    this.localRepository,
                    this.remoteRepositories,
                    this.artifactMetadataSource,
                    null,
                    Collections.EMPTY_LIST);
            dependencyArtifacts.addAll(result.getArtifacts());

            //not forgetting the Artifact of the project itself
            dependencyArtifacts.add(project.getArtifact());

            //resolve all dependencies transitively to obtain a comprehensive list of assemblies
            for (Iterator iter = dependencyArtifacts.iterator(); iter.hasNext();) {
                Artifact artifact = (Artifact) iter.next();
                this.artifactResolver.resolve(artifact, this.remoteRepositories, this.localRepository);
            }

            return dependencyArtifacts;
        } catch ( Exception ex ) {
            throw new MojoExecutionException("Encountered problems resolving dependencies of the executable " +
                                             "in preparation for its execution.", ex);
        }
    }

    /**
     * Installs a Grails plugin into the current project if it isn't
     * already installed. It works by simply unpacking the plugin
     * artifact (a ZIP file) into the appropriate location and adding
     * the plugin to the application's metadata.
     * @param plugin The plugin artifact to install.
     * @param metadata The application metadata. An entry for the plugin
     * is added to this if the installation is successful.
     * @ param launcher The Grails launcher instance that contains information
     * about the various project directories.  In particular, this is where the
     * method gets the location of the project's "plugins" directory from.
     * @return <code>true</code> if the plugin is installed and the
     * metadata updated, otherwise <code>false</code>.
     * @throws IOException
     * @throws ArchiverException
     */
    private boolean installGrailsPlugin(
            Artifact plugin,
            Metadata metadata,
            GrailsLauncher launcher) throws IOException, ArchiverException {
        String pluginName = plugin.getArtifactId();
        String pluginVersion = plugin.getVersion();

        if (pluginName.startsWith(PLUGIN_PREFIX)) {
            pluginName = pluginName.substring(PLUGIN_PREFIX.length());
        }
        getLog().info("Installing plugin " + pluginName + ":" + pluginVersion);

        // The directory the plugin will be unzipped to.
        File targetDir = new File(launcher.getProjectPluginsDir(), pluginName + "-" + pluginVersion);

        // Unpack the plugin if it hasn't already been.
        if (!targetDir.exists()) {
            targetDir.mkdirs();

            ZipUnArchiver unzipper = new ZipUnArchiver();
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

    private List artifactsToFiles(Collection artifacts) {
        List files = new ArrayList(artifacts.size());
        for (Iterator iter = artifacts.iterator(); iter.hasNext();) {
            files.add(((Artifact) iter.next()).getFile());
        }

        return files;
    }

    private Artifact findArtifact(Collection artifacts, String groupId, String artifactId) {
        for (Iterator iter = artifacts.iterator(); iter.hasNext();) {
            Artifact artifact = (Artifact) iter.next();
            if (artifact.getGroupId().equals(groupId) && artifact.getArtifactId().equals(artifactId)) {
                return artifact;
            }
        }

        return null;
    }

    /**
     * Examines a collection of artifacts and extracts all those that
     * have the given group ID.
     * @param artifacts The collection of artifacts to examine.
     * @param groupId The group ID of interest.
     * @return A list of artifacts with the given group ID.
     */
    private List artifactsByGroupId(Collection artifacts, String groupId) {
        List inGroup = new ArrayList(artifacts.size());
        for (Iterator iter = artifacts.iterator(); iter.hasNext();) {
            Artifact artifact = (Artifact) iter.next();
            if (artifact.getGroupId().equals(groupId)) {
                inGroup.add(artifact);
            }
        }

        return inGroup;
    }

    /**
     * Converts a collection of Dependency objects to a list of
     * corresponding Artifact objects.
     * @param deps The collection of dependencies to convert.
     * @return A list of Artifact instances.
     */
    private List dependenciesToArtifacts(Collection deps) {
        List artifacts = new ArrayList(deps.size());
        for (Iterator iter = deps.iterator(); iter.hasNext();) {
            artifacts.add(dependencyToArtifact((Dependency) iter.next()));
        }

        return artifacts;
    }

    /**
     * Uses the injected artifact factory to convert a single Dependency
     * object into an Artifact instance.
     * @param dep The dependency to convert.
     * @return The resulting Artifact.
     */
    private Artifact dependencyToArtifact(Dependency dep) {
        return this.artifactFactory.createBuildArtifact(
                dep.getGroupId(),
                dep.getArtifactId(),
                dep.getVersion(),
                "pom");
    }

    /**
     * Removes any Grails plugin dependencies from the supplied list
     * of dependencies.  A Grails plugin is any dependency whose type
     * is equal to "grails-plugin" or "zip".
     * @param dependencies The list of dependencies to be cleansed.
     * @return The cleansed list of dependencies with all Grails plugin
     *   dependencies removed.
     */
    private List removePluginDependencies(final List dependencies) {
        if(dependencies != null) {
            for (final Iterator iter = dependencies.iterator(); iter.hasNext();) {
                final Artifact dep = (Artifact) iter.next();
                if (dep.getType() != null && (dep.getType().equals("grails-plugin") || dep.getType().equals("zip"))) {
                    iter.remove();
                }
            }
        }
        return dependencies;
    }
}
