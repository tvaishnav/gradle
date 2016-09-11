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
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.Task;
import org.gradle.api.tasks.incremental.IncrementalTaskInputs;
import org.gradle.internal.Factory;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

class TaskClassInfoContext {
    private final ImmutableList.Builder<Factory<Action<Task>>> actionFactoriesBuilder = ImmutableList.builder();
    private final Set<String> processedMethods = new HashSet<String>();
    private final ClassInfoStore classInfoStore;
    private ClassInfo classInfo;
    private boolean incremental;
    private boolean cacheable;

    public TaskClassInfoContext(ClassInfoStore classInfoStore) {
        this.classInfoStore = classInfoStore;
    }

    public boolean hasAlreadyProcessed(Method method) {
        return !processedMethods.add(method.getName());
    }

    public void addActionFactory(Factory<Action<Task>> actionFactory) {
        actionFactoriesBuilder.add(actionFactory);
    }

    public void markAsIncremental() {
        if (incremental) {
            throw new GradleException(String.format("Cannot have multiple @TaskAction methods accepting an %s parameter.", IncrementalTaskInputs.class.getSimpleName()));
        }
        this.incremental = true;
    }

    public void setCacheable(boolean cacheable) {
        this.cacheable = cacheable;
    }

    public TaskClassInfo build() {
        return new TaskClassInfo(classInfoStore, incremental, cacheable, classInfo, actionFactoriesBuilder.build());
    }

    public void setClassInfo(ClassInfo classInfo) {
        this.classInfo = classInfo;
    }
}
