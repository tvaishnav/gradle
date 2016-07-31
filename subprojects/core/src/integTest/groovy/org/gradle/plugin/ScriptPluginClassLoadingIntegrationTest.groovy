/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.plugin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ScriptPluginClassLoadingIntegrationTest extends AbstractIntegrationSpec {

    // TODO: no-op test doesn't fail, so what in this test is causing the eff up?
    def "methods defined in script are available to used script plugins"() {
        given:
        buildScript """
            task("hello") {
                doLast {
                    println "hello from method"
                }
            }
        """

        when:
        succeeds "hello"

        then:
        output.contains "hello from method"
    }
}
