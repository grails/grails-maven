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
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Creates a new Grails unit test. A unit test requires that you mock out access
 * to dynamic methods, but executes a lot quicker.
 *
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 * @version $Id$
 * @description Creates a new Grails unit test. A unit test requires that you
 * mock out access to dynamic methods, but executes a lot quicker.
 * @since 0.1
 */
@Mojo(name = "create-unit-test",requiresProject = false, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class GrailsCreateUnitTestMojo extends AbstractGrailsMojo {

    /**
     * The name for the unit test to create.
     */
    @Parameter(property = "unitTestName")
    private String unitTestName;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        runGrails("CreateUnitTest", unitTestName);
    }
}
