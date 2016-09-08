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
import org.gradle.api.tasks.Input;

import java.util.Collection;

class InputTaskPropertyInfoCreator extends SingleTaskPropertyInfoCreator<Input> {
    @Override
    public Class<Input> getAnnotationType() {
        return Input.class;
    }

    @Override
    public TaskPropertyInfo createProperty(TaskPropertyInfoContext context) {
        return new InputPropertyInfo(context);
    }

    private static class InputPropertyInfo extends AbstractTaskPropertyInfo {
        public InputPropertyInfo(TaskPropertyInfoContext context) {
            super(context);
        }

        @Override
        protected void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
        }

        @Override
        protected void processValue(TaskInternal task, String propertyName, Object value) {
            task.getInputs().property(propertyName, value);
        }
    }
}
