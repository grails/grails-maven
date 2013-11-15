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

/**
 * Runs a Grails applications unit tests and integration tests.
 *
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @version $Id$
 * @description Runs a Grails applications unit tests and integration tests.
 * @goal test-app
 * @requiresProject false
 * @requiresDependencyResolution test
 * @since 0.1
 */
public class GrailsTestAppMojo extends AbstractGrailsMojo {

    /**
     *  The space-separated list of test classes to run (e.g. *Controller)
     *
     * @parameter expression="${testPatterns}"
     */
    private String testPatterns;
    /**
     * The space-separated list of test types or phases (e.g unit: :spock)
     *
     * @parameter expression="${testTypesAndPhases}"
     */
    private String testTypesAndPhases;

    public void execute() throws MojoExecutionException, MojoFailureException {
        if(getEnvironment() == null) {
            env = "test";
        }

        String args = null;

        if (testTypesAndPhases != null) {
            args = testTypesAndPhases;
        }

        if (testPatterns != null) {
            args = (args != null) ? args + " " + testPatterns : testPatterns;
        }

        runGrails("TestApp", args);
    }
}
