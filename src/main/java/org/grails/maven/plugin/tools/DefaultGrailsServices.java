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

import groovy.lang.GroovyClassLoader;
import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.groovy.grails.plugins.AstPluginDescriptorReader;
import org.codehaus.groovy.grails.plugins.GrailsPluginInfo;
import org.codehaus.plexus.logging.AbstractLogEnabled;
import org.codehaus.plexus.util.IOUtil;

import java.io.*;
import java.util.Date;
import java.util.Properties;

/**
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @version $Id$
 * @plexus.component role="org.grails.maven.plugin.tools.GrailsServices"
 * @since 0.1
 */
public class DefaultGrailsServices extends AbstractLogEnabled implements GrailsServices {

    private static final String FILE_SUFFIX = "GrailsPlugin.groovy";

    private File _basedir;

    /**
     * Converts foo-bar into FooBar. Empty and null strings are returned as-is.
     *
     * @param name The lower case hyphen separated name
     * @return The class name equivalent.
     */
    public static String getClassNameForLowerCaseHyphenSeparatedName(String name) {
        // Handle null and empty strings.
        if (name == null || name.length() == 0) return name;

        if (name.indexOf('-') == -1) {
            return name.substring(0,1).toUpperCase() + name.substring(1);
        }

        StringBuilder buf = new StringBuilder();
        String[] tokens = name.split("-");
        for (String token : tokens) {
            if (token == null || token.length() == 0) continue;
            buf.append(token.substring(0, 1).toUpperCase())
                    .append(token.substring(1));
        }
        return buf.toString();
    }
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
        final String pluginName = getClassNameForLowerCaseHyphenSeparatedName(getLogicalName(className, "GrailsPlugin"));
        pluginProject.setPluginName(pluginName);

        final GroovyClassLoader classLoader = new GroovyClassLoader();
        final AstPluginDescriptorReader reader = new AstPluginDescriptorReader(classLoader);
        final GrailsPluginInfo info = reader.readPluginInfo(new org.codehaus.groovy.grails.io.support.FileSystemResource(descriptor));
        final String version = info.getVersion();

        if (version == null || version.trim().length() == 0) {
            throw new MojoExecutionException("Plugin does not have a version!");
        }

        pluginProject.setVersion(version);
        return pluginProject;
    }

    /**
     * Retrieves the logical name of the class without the trailing name
     * @param name The name of the class
     * @param trailingName The trailing name
     * @return The logical name
     */
    public static String getLogicalName(String name, String trailingName) {
        if (trailingName == null || trailingName.length() == 0) {
            return getShortName(name);
        }

        String shortName = getShortName(name);
        if (shortName.indexOf(trailingName) == - 1) {
            return name;
        }

        return shortName.substring(0, shortName.length() - trailingName.length());
    }

    /**
     * Returns the class name without the package prefix.
     *
     * @param className The class name to get a short name for
     * @return The short name of the class
     */
    public static String getShortName(String className) {
        int i = className.lastIndexOf(".");
        if (i > -1) {
            className = className.substring(i + 1, className.length());
        }
        return className;
    }
}
