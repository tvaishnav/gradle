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

package org.gradle.api.internal.project.taskfactory;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.internal.Factory;

import java.lang.reflect.Type;
import java.util.List;
import java.util.Set;

public class TaskClassInfo {
    private final ClassInfoStore classInfoStore;
    private final boolean incremental;
    private final boolean cacheable;
    private final ClassInfo classInfo;
    private final List<Factory<Action<Task>>> taskActions;

    public TaskClassInfo(ClassInfoStore classInfoStore, boolean incremental, boolean cacheable, ClassInfo classInfo, List<Factory<Action<Task>>> taskActions) {
        this.classInfoStore = classInfoStore;
        this.incremental = incremental;
        this.cacheable = cacheable;
        this.classInfo = classInfo;
        this.taskActions = taskActions;
    }

    boolean isIncremental() {
        return incremental;
    }

    Set<String> getNonAnnotatedPropertyNames() {
        final ImmutableSet.Builder<String> builder = ImmutableSet.builder();
        builder.addAll(classInfo.getNonAnnotatedProperties());
        Set<Type> visitedTypes = Sets.newHashSet();
        classInfo.getProperties().visitDeclarations(classInfoStore, null, visitedTypes, new TaskPropertyDeclarationVisitor() {
            @Override
            public void visitDeclaration(String propertyName, TaskPropertyInfo property) {
                builder.addAll(property.getNonAnnotatedSubProperties(propertyName, classInfoStore));
            }
        });
        return builder.build();
    }

    public TaskPropertyInfoCollection getProperties() {
        return classInfo.getProperties();
    }

    boolean isCacheable() {
        return cacheable;
    }

    public List<Factory<Action<Task>>> getTaskActions() {
        return taskActions;
    }

    public void visitValues(Task task, TaskPropertyValueVisitor visitor) {
        classInfo.getProperties().visitValues(classInfoStore, null, task, visitor);
    }

    public void visitDeclarations(TaskPropertyDeclarationVisitor visitor) {
        Set<Type> visitedTypes = Sets.newHashSet();
        classInfo.getProperties().visitDeclarations(classInfoStore, null, visitedTypes, visitor);
    }
}
