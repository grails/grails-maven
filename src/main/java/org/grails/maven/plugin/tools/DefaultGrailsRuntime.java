package org.grails.maven.plugin.tools;

import org.apache.maven.plugin.MojoExecutionException;
import org.grails.launcher.GrailsLauncher;

/**
 * @author Andrew Potter (ddcapotter)
 */
public class DefaultGrailsRuntime extends AbstractGrailsRuntime {

    public DefaultGrailsRuntime(ExecutionContext context) {
        super(context);
    }

    @Override
    public void run() throws MojoExecutionException {
        GrailsLauncher launcher = createGrailsLauncher(executionContext);
        int exit =  launcher.launch(executionContext.getScriptName(), executionContext.getArgs(), executionContext.getEnv());
        if(exit != 0) {
            throw new MojoExecutionException("Build step \"" + executionContext.getScriptName() + "\" exited with non-zero exit status: " + exit);
        }
    }
}
