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

import org.gradle.api.internal.TaskInternal;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;

abstract class AbstractTaskPropertyInfo implements TaskPropertyInfo {
    private final boolean optional;

    public AbstractTaskPropertyInfo(boolean optional) {
        this.optional = optional;
    }

    @Override
    public void validate(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
        if (value == null) {
            if (!optional) {
                messages.add(String.format("No value has been specified for property '%s'.", propertyName));
            }
            return;
        }
        validateNonNullValue(task, propertyName, value, messages);
    }

    protected void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
    }

    @Override
    public void process(TaskInternal task, String propertyName, Object value) {
    }

    @Override
    public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Set<Type> visitedTypes, TaskPropertyDeclarationVisitor visitor) {
        visitor.visitDeclaration(propertyName, this);
    }

    @Override
    public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Object value, TaskPropertyValueVisitor visitor) {
        visitor.visitValue(propertyName, value, this);
    }

    @Override
    public Collection<String> getNonAnnotatedSubProperties(String propertyName, ClassInfoStore classInfoStore) {
        return Collections.emptySet();
    }
}
