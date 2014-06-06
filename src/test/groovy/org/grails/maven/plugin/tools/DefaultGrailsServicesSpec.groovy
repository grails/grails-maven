package org.grails.maven.plugin.tools

import spock.lang.Specification
import spock.lang.Unroll

/**
 * @author Ryan Gardner
 * @date 6/6/14
 */
class DefaultGrailsServicesSpec extends Specification {

    @Unroll
    def "the string {string} is converted to {expected} properly when calling the getLowerCaseHyphenSeparatedName method"() {
        expect:
            DefaultGrailsServices.getLowerCaseHyphenSeparatedName(string) == expected
        where:
            string      | expected
            'FooBarBaz' | 'foo-bar-baz'
            'Foo'       | 'foo'
            ''          | ''
            'fooNASA'   | 'foo-nasa'
            null        | null

    }

}
