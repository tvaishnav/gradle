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

import com.google.common.annotations.VisibleForTesting;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.internal.Factory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TaskClassInfo {
    private final boolean incremental;
    private final boolean cacheable;
    private final Set<String> nonAnnotatedPropertyNames;
    private final List<Factory<Action<Task>>> taskActions;
    private final TaskPropertyInfoContainer properties;

    public TaskClassInfo(boolean incremental, boolean cacheable, Set<String> nonAnnotatedPropertyNames, Map<String, TaskPropertyInfo> properties, List<Factory<Action<Task>>> taskActions) {
        this.incremental = incremental;
        this.cacheable = cacheable;
        this.nonAnnotatedPropertyNames = nonAnnotatedPropertyNames;
        this.properties = new TaskPropertyInfoContainer(properties);
        this.taskActions = taskActions;
    }

    public boolean isIncremental() {
        return incremental;
    }

    public Set<String> getNonAnnotatedPropertyNames() {
        return nonAnnotatedPropertyNames;
    }

    @VisibleForTesting
    Map<String, TaskPropertyInfo> getProperties() {
        return properties.getProperties();
    }

    public boolean isCacheable() {
        return cacheable;
    }

    public List<Factory<Action<Task>>> getTaskActions() {
        return taskActions;
    }

    public void validateInputsAndOutputs(final TaskInternal task, final Collection<String> messages) {
        properties.visitProperties(task, new TaskPropertyInfoContainer.TaskPropertyInfoVisitor() {
            @Override
            public void visit(String propertyName, TaskPropertyInfo property, Object value) {
                property.validate(task, propertyName, value, messages);
            }
        });
    }

    public void processInputsAndOutputs(final TaskInternal task) {
        properties.visitProperties(task, new TaskPropertyInfoContainer.TaskPropertyInfoVisitor() {
            @Override
            public void visit(String propertyName, TaskPropertyInfo property, Object value) {
                property.process(task, propertyName, value);
            }
        });
    }
}
