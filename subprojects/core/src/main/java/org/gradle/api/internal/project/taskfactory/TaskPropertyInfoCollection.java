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

import com.google.common.collect.ImmutableList;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public class TaskPropertyInfoCollection {
    private final List<TaskPropertyAccessor> propertyAccessors;

    public TaskPropertyInfoCollection(Collection<TaskPropertyAccessor> propertyAccessors) {
        this.propertyAccessors = ImmutableList.copyOf(propertyAccessors);
    }

    public void visitDeclarations(ClassInfoStore classInfoStore, String parentProperty, Set<Type> visitedTypes, TaskPropertyDeclarationVisitor visitor) {
        for (TaskPropertyAccessor propertyAccessor : propertyAccessors) {
            String propertyName = parentProperty == null
                ? propertyAccessor.getName()
                : parentProperty + "." + propertyAccessor.getName();
            propertyAccessor.getPropertyInfo().acceptVisitor(classInfoStore, propertyName, visitedTypes, visitor);
        }
    }

    public void visitValues(ClassInfoStore classInfoStore, String parentProperty, Object value, TaskPropertyValueVisitor visitor) {
        if (value == null) {
            return;
        }
        for (TaskPropertyAccessor propertyAccessor : propertyAccessors) {
            String propertyName = parentProperty == null
                ? propertyAccessor.getName()
                : parentProperty + "." + propertyAccessor.getName();
            Object propertyValue = propertyAccessor.getPropertyValue(value);
            propertyAccessor.getPropertyInfo().acceptVisitor(classInfoStore, propertyName, propertyValue, visitor);
        }
    }
}
