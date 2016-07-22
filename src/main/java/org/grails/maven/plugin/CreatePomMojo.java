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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.grails.maven.plugin.tools.GrailsProject;
import org.grails.maven.plugin.tools.GrailsServices;

/**
 * Creates a creates a maven 2 POM for an existing Grails project.
 *
 * @author Graeme Rocher
 * @author <a href="mailto:aheritier@gmail.com">Arnaud HERITIER</a>
 *
 * @version $Id$
 * @description Creates a creates a maven 2 POM for on an existing Grails
 * project.
 * @since 0.1
 */
@Mojo(name = "create-pom",requiresProject = false, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class CreatePomMojo extends AbstractGrailsMojo {

    /**
     * The Group Id of the project to be build.
     */
    @Parameter(required = true)
    private String groupId;

    @Component
    protected GrailsServices grailsServices;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        grailsServices.setBasedir(getBasedir());
        runGrails("CreatePom", this.groupId);
    }
}
