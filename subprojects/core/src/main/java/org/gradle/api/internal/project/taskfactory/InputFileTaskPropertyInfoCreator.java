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
import org.gradle.api.tasks.InputFile;

import java.io.File;
import java.util.Collection;

class InputFileTaskPropertyInfoCreator extends TaskPropertyInfoCreator<InputFile> {
    @Override
    public Class<InputFile> getAnnotationType() {
        return InputFile.class;
    }

    @Override
    public TaskPropertyInfo createProperty(TaskPropertyInfoContext context) {
        return new InputFilePropertyInfo(context);
    }

    private static class InputFilePropertyInfo extends TerminalTaskPropertyInfo {
        public InputFilePropertyInfo(TaskPropertyInfoContext context) {
            super(context);
        }

        @Override
        protected void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
            File fileValue = (File) value;
            if (!fileValue.exists()) {
                messages.add(String.format("File '%s' specified for property '%s' does not exist.", fileValue, propertyName));
            } else if (!fileValue.isFile()) {
                messages.add(String.format("File '%s' specified for property '%s' is not a file.", fileValue, propertyName));
            }
        }

        @Override
        public void process(TaskInternal task, String propertyName, Object value) {
            if (value == null) {
                return;
            }
            task.getInputs().file(value)
                .withPropertyName(propertyName)
                .withPathSensitivity(pathSensitivity);
        }
    }
}
