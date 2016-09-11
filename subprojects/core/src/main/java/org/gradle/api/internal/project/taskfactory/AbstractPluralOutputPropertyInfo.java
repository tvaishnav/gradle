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

import com.google.common.reflect.TypeToken;
import org.gradle.api.Action;
import org.gradle.api.Task;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.TaskOutputFilePropertyBuilder;

import java.io.File;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedCast;

abstract class AbstractPluralOutputPropertyInfo extends TerminalTaskPropertyInfo {
    private static final TypeToken<Map> MAP_TYPE = TypeToken.of(Map.class);
    private final Type type;

    public AbstractPluralOutputPropertyInfo(TaskPropertyInfoContext context) {
        super(context);
        this.type = context.getType();
    }

    @Override
    protected final void validateNonNullValue(TaskInternal task, String propertyName, Object value, Collection<String> messages) {
        for (File file : toFiles(value)) {
            doValidate(propertyName, file, messages);
        }
    }

    protected abstract void doValidate(String propertyName, File file, Collection<String> messages);

    @Override
    public final void process(TaskInternal task, String propertyName, final Object value) {
        if (value == null) {
            return;
        }
        TaskOutputFilePropertyBuilder propertyBuilder;
        task.prependParallelSafeAction(new Action<Task>() {
            @Override
            public void execute(Task task) {
                for (File file : toFiles(value)) {
                    doEnsureExists(file);
                }
            }
        });
        if (MAP_TYPE.isAssignableFrom(type)) {
            propertyBuilder = task.getOutputs().namedFiles(Map.class.cast(value));
        } else {
            propertyBuilder = task.getOutputs().files(value);
        }
        propertyBuilder.withPropertyName(propertyName);
        propertyBuilder.withPathSensitivity(pathSensitivity);
    }

    protected abstract void doEnsureExists(File file);

    private static Iterable<File> toFiles(Object value) {
        if (value instanceof Map) {
            return uncheckedCast(((Map) value).values());
        } else {
            return uncheckedCast(value);
        }
    }
}
