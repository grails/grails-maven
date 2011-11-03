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
package org.grails.maven.plugin.tools;

import grails.util.GrailsNameUtils;
import groovy.lang.GroovyClassLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Date;
import java.util.Properties;

import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.groovy.grails.plugins.AstPluginDescriptorReader;
import org.codehaus.groovy.grails.plugins.GrailsPluginInfo;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.springframework.core.io.FileSystemResource;

/**
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @version $Id$
 * @plexus.component role="org.grails.maven.plugin.tools.GrailsServices"
 * @since 0.1
 */
public class DefaultGrailsServices extends AbstractLogEnabled implements GrailsServices {

    private static final String FILE_SUFFIX = "GrailsPlugin.groovy";

    private File _basedir;
//    private List _dependencyPaths;

    private File getBasedir() {
        if (_basedir != null) {
            return _basedir;
        }

        throw new RuntimeException("The basedir has to be set before any of the service methods are invoked.");
    }

    // -----------------------------------------------------------------------
    // GrailsServices Implementation
    // -----------------------------------------------------------------------

    public void setBasedir(final File basedir) {
        this._basedir = basedir;
    }

    public MavenProject createPOM(final String groupId, final GrailsProject grailsProjectDescriptor, final String mtgGroupId,
                                  final String grailsPluginArtifactId, final String mtgVersion) {
        return createPOM(groupId, grailsProjectDescriptor, mtgGroupId, grailsPluginArtifactId, mtgVersion, false);
    }

    public MavenProject createPOM(final String groupId, final GrailsProject grailsProjectDescriptor, final String mtgGroupId,
                                  final String grailsPluginArtifactId, final String mtgVersion, final boolean addEclipseSettings) {
        final MavenProject pom = new MavenProject();
        if (pom.getBuild().getPluginManagement() == null) {
            pom.getBuild().setPluginManagement(new PluginManagement());
        }
        final PluginManagement pluginMgt = pom.getPluginManagement();

        // Those four properties are needed.
        pom.setModelVersion("4.0.0");
        pom.setPackaging("grails-app");
        // Specific for GRAILS
        pom.getModel().getProperties().setProperty("grailsHome", "${env.GRAILS_HOME}");
        pom.getModel().getProperties().setProperty("grailsVersion", grailsProjectDescriptor.getAppGrailsVersion());
        // Add our own plugin
        final Plugin grailsPlugin = new Plugin();
        grailsPlugin.setGroupId(mtgGroupId);
        grailsPlugin.setArtifactId(grailsPluginArtifactId);
        grailsPlugin.setVersion(mtgVersion);
        grailsPlugin.setExtensions(true);
        pom.addPlugin(grailsPlugin);
        // Add compiler plugin settings
        final Plugin compilerPlugin = new Plugin();
        compilerPlugin.setGroupId("org.apache.maven.plugins");
        compilerPlugin.setArtifactId("maven-compiler-plugin");
        final Xpp3Dom compilerConfig = new Xpp3Dom("configuration");
        final Xpp3Dom source = new Xpp3Dom("source");
        source.setValue("1.5");
        compilerConfig.addChild(source);
        final Xpp3Dom target = new Xpp3Dom("target");
        target.setValue("1.5");
        compilerConfig.addChild(target);
        compilerPlugin.setConfiguration(compilerConfig);
        pom.addPlugin(compilerPlugin);
        // Add eclipse plugin settings
        if (addEclipseSettings) {
            final Plugin warPlugin = new Plugin();
            warPlugin.setGroupId("org.apache.maven.plugins");
            warPlugin.setArtifactId("maven-war-plugin");
            final Xpp3Dom warConfig = new Xpp3Dom("configuration");
            final Xpp3Dom warSourceDirectory = new Xpp3Dom("warSourceDirectory");
            warSourceDirectory.setValue("web-app");
            warConfig.addChild(warSourceDirectory);
            warPlugin.setConfiguration(warConfig);
            pluginMgt.addPlugin(warPlugin);

            final Plugin eclipsePlugin = new Plugin();
            eclipsePlugin.setGroupId("org.apache.maven.plugins");
            eclipsePlugin.setArtifactId("maven-eclipse-plugin");
            final Xpp3Dom configuration = new Xpp3Dom("configuration");
            final Xpp3Dom projectnatures = new Xpp3Dom("additionalProjectnatures");
            final Xpp3Dom projectnature = new Xpp3Dom("projectnature");
            projectnature.setValue("org.codehaus.groovy.eclipse.groovyNature");
            projectnatures.addChild(projectnature);
            configuration.addChild(projectnatures);
            final Xpp3Dom additionalBuildcommands = new Xpp3Dom(
                "additionalBuildcommands");
            final Xpp3Dom buildcommand = new Xpp3Dom("buildcommand");
            buildcommand.setValue("org.codehaus.groovy.eclipse.groovyBuilder");
            additionalBuildcommands.addChild(buildcommand);
            configuration.addChild(additionalBuildcommands);
            // Xpp3Dom additionalProjectFacets = new Xpp3Dom(
            // "additionalProjectFacets");
            // Xpp3Dom jstWeb = new Xpp3Dom("jst.web");
            // jstWeb.setValue("2.5");
            // additionalProjectFacets.addChild(jstWeb);
            // configuration.addChild(additionalProjectFacets);
            final Xpp3Dom packaging = new Xpp3Dom("packaging");
            packaging.setValue("war");
            configuration.addChild(packaging);

            eclipsePlugin.setConfiguration(configuration);
            pluginMgt.addPlugin(eclipsePlugin);
        }
        // Change the default output directory to generate classes
        pom.getModel().getBuild().setOutputDirectory("web-app/WEB-INF/classes");

        pom.setArtifactId(grailsProjectDescriptor.getAppName());
        pom.setName(grailsProjectDescriptor.getAppName());
        pom.setGroupId(groupId);
        pom.setVersion(grailsProjectDescriptor.getAppVersion());
        if (!grailsProjectDescriptor.getAppVersion().endsWith("SNAPSHOT")) {
            getLogger().warn("=====================================================================");
            getLogger().warn("If your project is currently in development, in accordance with maven ");
            getLogger().warn("standards, its version must be " + grailsProjectDescriptor.getAppVersion() + "-SNAPSHOT and not " + grailsProjectDescriptor.getAppVersion() + ".");
            getLogger().warn("Please, change your version in the application.properties descriptor");
            getLogger().warn("and regenerate your pom.");
            getLogger().warn("=====================================================================");
        }
        return pom;
    }

    public GrailsProject readProjectDescriptor() throws MojoExecutionException {
        // Load existing Grails properties
        FileInputStream fis = null;
        try {
            final Properties properties = new Properties();
            fis = new FileInputStream(new File(getBasedir(), "application.properties"));
            properties.load(fis);

            final GrailsProject grailsProject = new GrailsProject();
            grailsProject.setAppGrailsVersion(properties.getProperty("app.grails.version"));
            grailsProject.setAppName(properties.getProperty("app.name"));
            grailsProject.setAppVersion(properties.getProperty("app.version"));

            return grailsProject;
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to read grails project descriptor.", e);
        } finally {
            IOUtil.close(fis);
        }
    }

    public void writeProjectDescriptor(final File projectDir, final GrailsProject grailsProjectDescriptor) throws MojoExecutionException {
        final String description = "Grails Descriptor updated by grails-maven-plugin on " + new Date();

        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(new File(projectDir, "application.properties"));
            final Properties properties = new Properties();
            properties.setProperty("app.grails.version", grailsProjectDescriptor.getAppGrailsVersion());
            properties.setProperty("app.name", grailsProjectDescriptor.getAppName());
            properties.setProperty("app.version", grailsProjectDescriptor.getAppVersion());
            properties.store(fos, description);
        } catch (final IOException e) {
            throw new MojoExecutionException("Unable to write grails project descriptor.", e);
        } finally {
            IOUtil.close(fos);
        }
    }

    public GrailsPluginProject readGrailsPluginProject() throws MojoExecutionException {
        final GrailsPluginProject pluginProject = new GrailsPluginProject();

        final File[] files = getBasedir().listFiles(new FilenameFilter() {
            public boolean accept(final File file, final String s) {
                return s.endsWith(FILE_SUFFIX) && s.length() > FILE_SUFFIX.length();
            }
        });

        if(files == null || files.length != 1) {
            throw new MojoExecutionException("Could not find a plugin descriptor. Expected to find exactly one file " +
                "called FooGrailsPlugin.groovy in '" + getBasedir().getAbsolutePath() + "'.");
        }

        final File descriptor = files[0];
        pluginProject.setFileName(descriptor);

        final String className = descriptor.getName().substring(0, descriptor.getName().length() - ".groovy".length());
        final String pluginName = GrailsNameUtils.getScriptName(GrailsNameUtils.getLogicalName(className, "GrailsPlugin"));
        pluginProject.setPluginName(pluginName);

        final GroovyClassLoader classLoader = new GroovyClassLoader();
        final AstPluginDescriptorReader reader = new AstPluginDescriptorReader(classLoader);
        final GrailsPluginInfo info = reader.readPluginInfo(new FileSystemResource(descriptor));
        final String version = info.getVersion();

        if (version == null || version.trim().length() == 0) {
            throw new MojoExecutionException("Plugin does not have a version!");
        }

        pluginProject.setVersion(version);
        return pluginProject;
    }
}
