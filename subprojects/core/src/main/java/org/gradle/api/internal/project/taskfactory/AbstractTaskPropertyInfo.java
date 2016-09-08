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
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.Factory;
import org.gradle.internal.reflect.JavaReflectionUtil;
import org.gradle.util.DeprecationLogger;

import java.lang.reflect.Method;
import java.util.Collection;

abstract class AbstractTaskPropertyInfo implements TaskPropertyInfo {
    private final Method method;
    protected final boolean optional;
    protected final boolean skipWhenEmpty;
    protected final boolean orderSensitive;
    protected final PathSensitivity pathSensitivity;

    public AbstractTaskPropertyInfo(TaskPropertyInfoContext context) {
        this.method = context.getMethod();
        this.optional = context.isOptional();
        this.skipWhenEmpty = context.isSkipWhenEmpty();
        this.orderSensitive = context.isOrderSensitive();
        this.pathSensitivity = context.getPathSensitivity();
    }

    @Override
    public void validate(TaskInternal task, String propertyName, Object parentValue, Collection<String> messages) {
        Object value = getPropertyValue(parentValue);
        if (value == null) {
            if (!optional) {
                messages.add(String.format("No value has been specified for property '%s'.", propertyName));
            }
            return;
        }
        validateNonNullValue(task, propertyName, value, messages);
    }

    @Override
    public void process(TaskInternal task, String propertyName, final Object parentValue) {
        processValue(task, propertyName, getPropertyValue(parentValue));
    }

    private Object getPropertyValue(final Object parentValue) {
        return DeprecationLogger.whileDisabled(new Factory<Object>() {
            @Override
            public Object create() {
                return JavaReflectionUtil.method(Object.class, method).invoke(parentValue);
            }
        });
    }

    protected abstract void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages);

    abstract protected void processValue(TaskInternal task, String propertyName, Object value);
}
