package org.grails.maven.plugin;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * <p>Creates a WAR archive and register it in maven. This differs from
 * the MvnWarMojo in that it makes the WAR file the build artifact for
 * the "grails-app" packaging. The standard "maven-war" goal is designed
 * for the normal "war" packaging.</p>
 * <p>So why have two versions? Well, version 1.0 of the plugin was
 * released with the <code>war</code> packaging as the default for
 * Grails projects. That hasn't worked out so well, but we still need
 * to support it, so we need one war target for the <code>war</code>
 * packaging type, and one for the <code>grails-app</code> packaging
 * type (which is now preferred over <code>war</code>).
 *
 * @author Peter Ledbrook
 * @version $Id$
 * @description Creates a WAR archive and register it in maven.
 * @since 1.1
 */
@Mojo(name = "maven-grails-app-war", defaultPhase = LifecyclePhase.PACKAGE)
public class MvnGrailsAppWarMojo extends MvnWarMojo {
    /**
     * The maven artifact.
     */
    @Parameter(defaultValue = "${project.artifact}")
    private Artifact artifact;

    /**
     * The artifact handler.
     */
    @Component(hint = "gails-app")
    private ArtifactHandler artifactHandler;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        // Make the WAR file the build artifact.
        artifact.setFile(warFile);
        artifact.setArtifactHandler(artifactHandler);
    }
}
