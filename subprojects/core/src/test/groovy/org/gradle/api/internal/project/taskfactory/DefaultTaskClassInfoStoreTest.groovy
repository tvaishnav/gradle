/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.AbstractTask
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Console
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.Nested
import org.gradle.api.tasks.OrderSensitive
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.OutputFiles
import spock.lang.Issue
import spock.lang.Specification

import javax.inject.Inject
import java.util.concurrent.Callable

class DefaultTaskClassInfoStoreTest extends Specification {
    def classInfoStore = new DefaultClassInfoStore()
    def taskClassInfoStore = new DefaultTaskClassInfoStore(classInfoStore)

    @SuppressWarnings("GrDeprecatedAPIUsage")
    private static class SimpleTask extends DefaultTask {
        @Input String inputString
        @InputFile File inputFile
        @InputDirectory File inputDirectory
        @InputFiles File inputFiles
        @OutputFile File outputFile
        @OutputFiles Set<File> outputFiles
        @OutputDirectory File outputDirectory
        @OutputDirectories Set<File> outputDirectories
        @Inject Object injectedService
        @Internal Object internal
        @Console boolean console
    }

    def "can get annotated properties of simple task"() {
        def info = taskClassInfoStore.getTaskClassInfo(SimpleTask)

        expect:
        !info.incremental
        !info.cacheable
        propertyNamesOf(info) == ["inputDirectory", "inputFile", "inputFiles", "inputString", "outputDirectories", "outputDirectory", "outputFile", "outputFiles"]
        info.nonAnnotatedPropertyNames.empty
    }

    @CacheableTask
    private static class MyCacheableTask extends DefaultTask {}

    def "cacheable tasks are detected"() {
        expect:
        taskClassInfoStore.getTaskClassInfo(MyCacheableTask).cacheable
    }

    private static class MyNonCacheableTask extends MyCacheableTask {}

    def "cacheability is not inherited"() {
        expect:
        !taskClassInfoStore.getTaskClassInfo(MyNonCacheableTask).cacheable
    }

    private static class BaseTask extends DefaultTask {
        @Input String baseValue
        @Input String superclassValue
        @Input String superclassValueWithDuplicateAnnotation
        String nonAnnotatedBaseValue
    }

    private static class OverridingTask extends BaseTask {
        @Override
        String getSuperclassValue() {
            return super.getSuperclassValue()
        }

        @Input @Override
        String getSuperclassValueWithDuplicateAnnotation() {
            return super.getSuperclassValueWithDuplicateAnnotation()
        }

        @Input @Override
        String getNonAnnotatedBaseValue() {
            return super.getNonAnnotatedBaseValue()
        }
    }

    def "overridden properties inherit super-class annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(OverridingTask)

        expect:
        !info.incremental
        propertyNamesOf(info) == ["baseValue", "nonAnnotatedBaseValue", "superclassValue", "superclassValueWithDuplicateAnnotation"]
        info.nonAnnotatedPropertyNames.empty
    }

    private interface TaskSpec {
        @Input
        String getInterfaceValue()
    }

    private static class InterfaceImplementingTask extends DefaultTask implements TaskSpec {
        @Override
        String getInterfaceValue() {
            "value"
        }
    }

    def "implemented properties inherit interface annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(InterfaceImplementingTask)

        expect:
        !info.incremental
        propertyNamesOf(info) == ["interfaceValue"]
        info.nonAnnotatedPropertyNames.empty
    }

    private static class NonAnnotatedTask extends DefaultTask {
        File inputFile

        @SuppressWarnings("GrMethodMayBeStatic")
        String getValue() {
            "test"
        }
    }

    def "detects properties without annotations"() {
        def info = taskClassInfoStore.getTaskClassInfo(NonAnnotatedTask)

        expect:
        !info.incremental
        propertyNamesOf(info).empty
        info.nonAnnotatedPropertyNames.sort() == ["inputFile", "value"]
    }

    def "class infos are cached"() {
        def info = taskClassInfoStore.getTaskClassInfo(SimpleTask)
        expect:
        info == taskClassInfoStore.getTaskClassInfo(SimpleTask)
    }

    @SuppressWarnings("GroovyUnusedDeclaration")
    private static class IsGetterTask extends DefaultTask {
        @Input
        private boolean feature1
        private boolean feature2

        boolean isFeature1() {
            return feature1
        }
        void setFeature1(boolean enabled) {
            this.feature1 = enabled
        }
        boolean isFeature2() {
            return feature2
        }
        void setFeature2(boolean enabled) {
            this.feature2 = enabled
        }
    }

    @Issue("https://issues.gradle.org/browse/GRADLE-2115")
    def "annotation on private filed is recognized for is-getter"() {
        def info = taskClassInfoStore.getTaskClassInfo(IsGetterTask)
        expect:
        propertyNamesOf(info) == ["feature1"]
    }

    @EqualsAndHashCode
    @ToString
    private static class Bean {
        @Input String name
    }

    private static class TaskWithNestedIterable extends DefaultTask {
        @Nested(resolveCollections = true)
        @OrderSensitive
        List<Bean> beans = [new Bean(name: "one"), new Bean(name: "two")]
    }

    def "iterable nested property is resolved"() {
        def info = taskClassInfoStore.getTaskClassInfo(TaskWithNestedIterable)
        expect:
        propertyNamesOf(info) == ["beans", "beans.name"]
        propertyValuesOf(info) { new TaskWithNestedIterable() } == [
            "beans": [new Bean(name: "one"), new Bean(name: "two")],
            "beans\$1": new Bean(name: "one"),
            "beans\$1.name": "one",
            "beans\$2": new Bean(name: "two"),
            "beans\$2.name": "two"
        ]
    }

    private static class TaskWithTwiceNestedIterable extends DefaultTask {
        @Nested(resolveCollections = true)
        @OrderSensitive
        List<List<Bean>> beans = [[new Bean(name: "one")], [new Bean(name: "two-a"), new Bean(name: "two-b")]]
    }

    def "twice nested iterable property is resolved"() {
        def info = taskClassInfoStore.getTaskClassInfo(TaskWithTwiceNestedIterable)
        expect:
        propertyNamesOf(info) == ["beans", "beans.name"]
        propertyValuesOf(info) { new TaskWithTwiceNestedIterable() } == [
            "beans": [[new Bean(name: "one")], [new Bean(name: "two-a"), new Bean(name: "two-b")]],
            "beans\$1": [new Bean(name: "one")],
            "beans\$1\$1": new Bean(name: "one"),
            "beans\$1\$1.name": "one",
            "beans\$2": [new Bean(name: "two-a"), new Bean(name: "two-b")],
            "beans\$2\$1": new Bean(name: "two-a"),
            "beans\$2\$1.name": "two-a",
            "beans\$2\$2": new Bean(name: "two-b"),
            "beans\$2\$2.name": "two-b"
        ]
    }

    private static class TaskWithNestedMap extends DefaultTask {
        @Nested(resolveCollections = true)
        @OrderSensitive
        Map<String, Bean> beans = ["first": new Bean(name: "one"), "second": new Bean(name: "two")]
    }

    def "map nested property is resolved"() {
        def info = taskClassInfoStore.getTaskClassInfo(TaskWithNestedMap)
        expect:
        propertyNamesOf(info) == ["beans", "beans.name"]
        propertyValuesOf(info) { new TaskWithNestedMap() } == [
            "beans": ["first": new Bean(name: "one"), "second": new Bean(name: "two")],
            "beans.first": new Bean(name: "one"),
            "beans.first.name": "one",
            "beans.second": new Bean(name: "two"),
            "beans.second.name": "two"
        ]
    }

    private static class TaskWithNestedMapIterable extends DefaultTask {
        @Nested(resolveCollections = true)
        @OrderSensitive
        Map<String, List<Bean>> beans = ["first": [new Bean(name: "one-a"), new Bean(name: "one-b")], "second": [new Bean(name: "two")]]
    }

    def "map iterable nested property is resolved"() {
        def info = taskClassInfoStore.getTaskClassInfo(TaskWithNestedMapIterable)
        expect:
        propertyNamesOf(info) == ["beans", "beans.name"]
        propertyValuesOf(info) { new TaskWithNestedMapIterable() } == [
            "beans": ["first": [new Bean(name: "one-a"), new Bean(name: "one-b")], "second": [new Bean(name: "two")]],
            "beans.first": [new Bean(name: "one-a"), new Bean(name: "one-b")],
            "beans.first\$1": new Bean(name: "one-a"),
            "beans.first\$1.name": "one-a",
            "beans.first\$2": new Bean(name: "one-b"),
            "beans.first\$2.name": "one-b",
            "beans.second": [new Bean(name: "two")],
            "beans.second\$1": new Bean(name: "two"),
            "beans.second\$1.name": "two",
        ]
    }

    @ToString
    @EqualsAndHashCode
    private static class RecursiveBean {
        @Input
        String name

        @OrderSensitive
        @Nested(resolveCollections = true)
        List<RecursiveBean> children = [];

        RecursiveBean(String name, RecursiveBean... children = []) {
            this.name = name
            this.children = children
        }
    }

    private static class TaskWithRecursiveNestedIterable extends DefaultTask {
        @Nested(resolveCollections = true)
        @OrderSensitive
        RecursiveBean root =
            new RecursiveBean("root",
                new RecursiveBean("child",
                    new RecursiveBean("grandChild")
                )
            )
    }

    def "recursive iterable nested property is resolved"() {
        def info = taskClassInfoStore.getTaskClassInfo(TaskWithRecursiveNestedIterable)
        expect:
        propertyNamesOf(info) == ["root", "root.children", "root.name"]
        propertyValuesOf(info) { new TaskWithRecursiveNestedIterable() } == [
            "root.children\$1.children\$1.children": [],
            "root.children\$1.children\$1.name": "grandChild",
            "root.children\$1.children\$1": new RecursiveBean("grandChild"),
            "root.children\$1.children": [new RecursiveBean("grandChild")],
            "root.children\$1.name": "child",
            "root.children\$1": new RecursiveBean("child", new RecursiveBean("grandChild")),
            "root.children": [new RecursiveBean("child", new RecursiveBean("grandChild"))],
            "root.name": "root",
            "root": new RecursiveBean("root", new RecursiveBean("child", new RecursiveBean("grandChild")))
        ]
    }

    def propertyNamesOf(TaskClassInfo classInfo) {
        def propertyNames = []
        classInfo.visitDeclarations { String propertyName, TaskPropertyInfo property ->
            propertyNames.add propertyName
        }
        return propertyNames
    }

    def propertyValuesOf(TaskClassInfo classInfo, Callable<Task> factory) {
        def propertyValues = [:]
        def task = AbstractTask.injectIntoNewInstance(Mock(ProjectInternal), "test", Task, factory)
        classInfo.visitValues(task) { String propertyName, Object value, TaskPropertyInfo property ->
            propertyValues.put propertyName, value
        }
        return propertyValues
    }
}
