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
 * Runs a Grails console inside the current project.
 *
 * @author Peter Ledbrook
 * @version $Id$
 * @description Runs a Grails console inside the current project.
 * @goal console
 * @requiresProject true
 * @requiresDependencyResolution runtime
 * @since 1.0
 */
public class GrailsConsoleMojo extends AbstractGrailsMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        runGrails("Console");
    }
}
