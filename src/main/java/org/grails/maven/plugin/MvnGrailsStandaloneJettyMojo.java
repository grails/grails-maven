package org.grails.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
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
     * The maven artifact.
     *
     * @parameter expression="${project.artifact}"
     * @required
     * @readonly
     */
    private Artifact artifact;

    /**
     * The artifact handler.
     *
     * @parameter expression="${component.org.apache.maven.artifact.handler.ArtifactHandler#grails-app}"
     * @required
     * @readonly
     */
    private ArtifactHandler artifactHandler;

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

        // Make the WAR file the build artifact.
        artifact.setFile(jarFile);
        artifact.setArtifactHandler(artifactHandler);
    }
}
