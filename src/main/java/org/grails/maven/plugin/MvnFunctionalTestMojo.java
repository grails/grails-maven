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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Runs a Grails application's functional tests.
 *
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @version $Id$
 * @description Runs a Grails application's functional tests.
 * @since 1.0
 */
@Mojo(name = "maven-functional-test", requiresDependencyResolution = ResolutionScope.TEST, defaultPhase = LifecyclePhase.INTEGRATION_TEST)
public class MvnFunctionalTestMojo extends AbstractGrailsMojo {

    /**
     * Set this to 'true' to bypass functional tests entirely. Its use is
     * NOT RECOMMENDED, but quite convenient on occasion.
      * @since 0.4
      */
    @Parameter(property = "skipTests", defaultValue = "false")
     private boolean skipTests;

     /**
      * Set this to 'true' to bypass functional tests entirely. Its use is
      * NOT RECOMMENDED, but quite convenient on occasion.
     * @since 0.3
     */
     @Parameter(property = "grails.test.skip")
    private boolean skip;

    /**
     * Set this to 'true' to bypass functional tests entirely. Its use is
     * NOT RECOMMENDED, but quite convenient on occasion.
     * @since 0.3
     */
    @Parameter(property = "maven.test.skip")
    private Boolean mavenSkip;

    /**
     * Set this to "true" to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
     * occasion.
     */
    @Parameter(property = "maven.test.failure.ignore" , defaultValue = "false")
    private boolean testFailureIgnore;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        if (skipTests || skip || (mavenSkip != null && mavenSkip.booleanValue())) {
            getLog().info("Functional tests are skipped.");
            return;
        }

        try {

            if(getEnvironment() == null) {
                env = "test";
            }
            runGrails("TestApp", "--unit --integration --functional");
        } catch (MojoExecutionException me) {
            if (!testFailureIgnore) {
                throw me;
            }
        }
    }
}
