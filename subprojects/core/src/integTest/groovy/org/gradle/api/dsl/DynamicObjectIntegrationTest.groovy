/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class DynamicObjectIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        file('settings.gradle') << "rootProject.name = 'test'"
    }

    def canInjectMethodsFromParentProject() {
        file("settings.gradle").writelns("include 'child1', 'child2'");
        file("build.gradle") << """
            subprojects {
                ext.useSomeProperty = { project.name }
                ext.useSomeMethod = { file(it) }
            }
        """
        file("child1/build.gradle") << """
            task testTask << {
               assert useSomeProperty() == 'child1'
               assert useSomeMethod('f') == file('f')
            }
        """

        expect:
        succeeds("testTask")
        // TODO: Does this fail if we try to assert something about output?
    }

    // TODO: Why does ScriptPluginCLIntTest have to run before DFWFactTest? Can we emulate
    // TODO: What are the reasons this only fails on Linux?
    // TODO: how is this test different than others in DOIntTest?
       // It's the only one that asserts output
    // TODO: what does result.output actually contain? Is it more or less than expected? What does it contain when this test passes?
    def canCallMethodWithClassArgumentType() {
        buildFile << """
interface Transformer {}

class Impl implements Transformer {}

class MyTask extends DefaultTask {
    public void transform(Class<? extends Transformer> c) {
        logger.lifecycle("transform(Class)")
    }

    public void transform(Transformer t) {
        logger.lifecycle("transform(Transformer)")
    }
}

task first {
    logger.lifecycle("Logged at configuration")
    doLast {
        logger.lifecycle("Logged at execution")
    }
}

task print(type: MyTask) {
    transform(Impl) // should call transform(Class)
}
        """

        expect:
        succeeds("first", "print")

        println """
output:
------
$result.output
------
"""
        result.output.contains("transform(Class)")
    }
}
