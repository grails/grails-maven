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
 * Set the grails application version from the Maven POM version.
 *
 * @author <a href="mailto:michael.lawler@selera.com">Michael Lawler</a>
 * @version $Id$
 * @description Set the grails application version from the Maven POM version.
 * @goal set-version
 * @phase validate
 * @requiresProject true
 * @requiresDependencyResolution
 * @since 1.2.1
 */
public class GrailsSetVersionMojo extends AbstractGrailsMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        runGrails("SetVersion", project.getVersion());
    }
}
