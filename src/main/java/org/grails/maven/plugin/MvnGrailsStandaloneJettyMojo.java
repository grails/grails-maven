package org.grails.maven.plugin;

import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;


import java.io.File;

/**
 * <p>Creates a standalone jetty jar encapsulating the grails war
 *
 * @author Dirk Sigurdson
 * @version $Id$
 * @description Creates a jetty JAR archive and register it in maven.
 * @goal maven-grails-standalone-jetty
 * @phase package
 * @requiresDependencyResolution
 * @since 1.1
 */
public class MvnGrailsStandaloneJettyMojo extends AbstractGrailsMojo {
    protected File jarFile;

    /**
     * Executes the MvnGrailsStandaloneJettyMojo on the current project.
     *
     * @throws MojoExecutionException if an error occured while building the webapp
     */
    public void execute() throws MojoExecutionException, MojoFailureException {


        Build build = project.getBuild();
        String jarFileName = build.getFinalName() != null ? build.getFinalName() : project.getArtifactId() + "-" + project.getVersion();
        if(!jarFileName.endsWith(".jar")) {
            jarFileName += ".jar";
        }
        jarFile = new File(build.getDirectory(), jarFileName);

        if(getEnvironment() == null) {
            env = "prod";
        }

        runGrails("BuildStandalone", "--jetty " + jarFile.toString());
    }
}
