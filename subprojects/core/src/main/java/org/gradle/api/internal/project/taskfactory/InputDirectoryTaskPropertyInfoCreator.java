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

import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.InputDirectory;

import java.io.File;
import java.util.Collection;

class InputDirectoryTaskPropertyInfoCreator extends SingleTaskPropertyInfoCreator<InputDirectory> {
    @Override
    public Class<InputDirectory> getAnnotationType() {
        return InputDirectory.class;
    }

    @Override
    public TaskPropertyInfo createProperty(TaskPropertyInfoContext context) {
        return new InputDirectoryPropertyInfo(context);
    }

    private static class InputDirectoryPropertyInfo extends AbstractTaskPropertyInfo {
        public InputDirectoryPropertyInfo(TaskPropertyInfoContext context) {
            super(context);
        }

        @Override
        protected void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
            File fileValue = (value instanceof ConfigurableFileTree) ? ((ConfigurableFileTree) value).getDir() : (File) value;
            if (!fileValue.exists()) {
                messages.add(String.format("Directory '%s' specified for property '%s' does not exist.", fileValue, propertyName));
            } else if (!fileValue.isDirectory()) {
                messages.add(String.format("Directory '%s' specified for property '%s' is not a directory.", fileValue, propertyName));
            }
        }

        @Override
        protected void processValue(TaskInternal task, String propertyName, Object value) {
            if (value == null) {
                return;
            }
            task.getInputs().dir(value)
                .withPropertyName(propertyName)
                .skipWhenEmpty(skipWhenEmpty)
                .orderSensitive(orderSensitive)
                .withPathSensitivity(pathSensitivity);
        }
    }
}
