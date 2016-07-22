package org.grails.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Build;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;


import java.io.File;

/**
 * <p>Creates a standalone jetty jar encapsulating the grails war
 *
 * @author Dirk Sigurdson
 * @version $Id$
 * @description Creates a jetty JAR archive and register it in maven.
 * @since 1.1
 */
@Mojo(name = "maven-grails-standalone-jetty", defaultPhase = LifecyclePhase.PACKAGE)
public class MvnGrailsStandaloneJettyMojo extends AbstractGrailsMojo {
    protected File jarFile;

    /**
     * The maven artifact.
     */
    @Component
    private Artifact artifact;

    /**
     * The artifact handler.
     */
    @Component(hint = "grails-app")
    private ArtifactHandler artifactHandler;

    @Override
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
