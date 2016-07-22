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
import org.apache.maven.plugins.annotations.ResolutionScope;

/**
 * Upgrades a Grails application to the version of this plugin.
 *
 * @author Jonathan Pearlin
 * @version $Id$
 * @description Upgrades a Grails application.
 * @since 1.3.7
 */
@Mojo(name = "upgrade", requiresProject = false, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class GrailsUpgradeMojo extends AbstractGrailsMojo {

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        runGrails("Upgrade");
    }
}
