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

import org.gradle.api.tasks.PathSensitivity;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

class TaskPropertyInfoContext {
    private final String name;
    private final Method method;
    private Field field;
    private boolean optional;
    private boolean skipWhenEmpty;
    private boolean orderSensitive;
    private PathSensitivity pathSensitivity = PathSensitivity.ABSOLUTE;
    private TaskPropertyInfoCreator<?> propertyCreator;

    public TaskPropertyInfoContext(String name, Method method) {
        this.name = name;
        this.method = method;
    }

    public void setField(Field field) {
        if (this.field == null) {
            this.field = field;
        }
    }

    public String getName() {
        return name;
    }

    public Class<?> getType() {
        return field != null ? field.getType() : method.getReturnType();
    }

    public Method getMethod() {
        return method;
    }

    public boolean isOptional() {
        return optional;
    }

    public void markOptional() {
        this.optional = true;
    }

    public boolean isSkipWhenEmpty() {
        return skipWhenEmpty;
    }

    public void markSkipWhenEmpty() {
        this.skipWhenEmpty = true;
    }

    public boolean isOrderSensitive() {
        return orderSensitive;
    }

    public void markOrderSensitive() {
        this.orderSensitive = true;
    }

    public PathSensitivity getPathSensitivity() {
        return pathSensitivity;
    }

    public void setPathSensitivity(PathSensitivity pathSensitivity) {
        this.pathSensitivity = pathSensitivity;
    }

    public TaskPropertyInfoCreator<?> getPropertyCreator() {
        return propertyCreator;
    }

    public void setPropertyCreator(TaskPropertyInfoCreator<?> propertyCreator) {
        if (this.propertyCreator != null && !this.propertyCreator.equals(propertyCreator)) {
            throw new IllegalStateException(String.format("Property %s declared in %s is declared both as @%s and @%s",
                name, method.getDeclaringClass().getName(), this.propertyCreator.getAnnotationType().getSimpleName(), propertyCreator.getAnnotationType().getSimpleName()));
        }
        this.propertyCreator = propertyCreator;
    }
}
