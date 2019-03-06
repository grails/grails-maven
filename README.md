Grails Maven Plugin
============

This is the Maven plugin for Grails. It provides the following Maven goals:

    grails:console
    grails:create-controller
    grails:create-domain-class
    grails:create-integration-test
    grails:create-pom
    grails:create-script
    grails:create-service
    grails:create-tag-lib
    grails:create-unit-test
    grails:exec
    grails:generate-all
    grails:generate-controller
    grails:generate-views
    grails:install-templates
    grails:list-plugins
    grails:package
    grails:run-app
    grails:run-app-https

As well as hooks into the normal Maven lifecycle like `compile`, `package` and `test` for Grails applications. 

You can create an example POM with any Grails application by doing:

    $ grails create-pom [GROUP ID]

Configuration
=============

The maven grails plugin now supports any grails version. You can specify the grails version to use by setting the grailsVersion property in the <configuration> section of the maven grails plugin:

    <plugin>
        <groupId>org.grails</groupId>
        <artifactId>grails-maven-plugin</artifactId>
        <version>2.4.3</version>
        <configuration>
            <grailsVersion>2.3.10</grailsVersion>
            <!-- In the 2.4.3 release of grails-maven-plugin, you need to explicitly set the grailsVersion -->
            <!-- otherwise 2.4.2 will be used -->
            <!--<grailsVersion>2.4.3</grailsVersion>-->
        </configuration>
    </plugin>
