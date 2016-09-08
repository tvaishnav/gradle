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

import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.OutputFile;

import java.io.File;
import java.util.Collection;

import static org.gradle.api.internal.tasks.TaskOutputsUtil.ensureParentDirectoryExists;
import static org.gradle.api.internal.tasks.TaskOutputsUtil.validateFile;

class OutputFileTaskPropertyInfoCreator extends SingleTaskPropertyInfoCreator<OutputFile> {
    @Override
    public Class<OutputFile> getAnnotationType() {
        return OutputFile.class;
    }

    @Override
    public TaskPropertyInfo createProperty(TaskPropertyInfoContext context) {
        return new OutputFilePropertyInfo(context);
    }

    private static class OutputFilePropertyInfo extends AbstractTaskPropertyInfo {
        public OutputFilePropertyInfo(TaskPropertyInfoContext context) {
            super(context);
        }

        @Override
        protected void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
            validateFile(propertyName, (File) value, messages);
        }

        @Override
        protected void processValue(TaskInternal task, String propertyName, Object value) {
            if (value == null) {
                return;
            }
            final File file = (File) value;
            task.prependParallelSafeAction(new Action<Task>() {
                @Override
                public void execute(Task task) {
                    ensureParentDirectoryExists(file);
                }
            });
            task.getOutputs().file(file)
                .withPropertyName(propertyName);
        }
    }
}
