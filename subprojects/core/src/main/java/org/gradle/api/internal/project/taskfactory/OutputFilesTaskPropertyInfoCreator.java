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

import org.gradle.api.tasks.OutputFiles;

import java.io.File;
import java.util.Collection;

import static org.gradle.api.internal.tasks.TaskOutputsUtil.ensureParentDirectoryExists;
import static org.gradle.api.internal.tasks.TaskOutputsUtil.validateFile;

class OutputFilesTaskPropertyInfoCreator extends SingleTaskPropertyInfoCreator<OutputFiles> {
    @Override
    public Class<OutputFiles> getAnnotationType() {
        return OutputFiles.class;
    }

    @Override
    public TaskPropertyInfo createProperty(TaskPropertyInfoContext context) {
        return new OutputFilesPropertyInfo(context);
    }

    private static class OutputFilesPropertyInfo extends AbstractPluralOutputPropertyInfo {
        public OutputFilesPropertyInfo(TaskPropertyInfoContext context) {
            super(context);
        }

        @Override
        protected void doValidate(String propertyName, File file, Collection<String> messages) {
            validateFile(propertyName, file, messages);
        }

        @Override
        protected void doEnsureExists(File file) {
            ensureParentDirectoryExists(file);
        }
    }
}
