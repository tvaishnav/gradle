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

import com.google.common.collect.ImmutableMap;

import java.util.Map;

class TaskPropertyInfoContainer {
    private final Map<String, TaskPropertyInfo> properties;

    public TaskPropertyInfoContainer(Map<String, TaskPropertyInfo> properties) {
        this.properties = ImmutableMap.copyOf(properties);
    }

    public void visitProperties(Object value, TaskPropertyInfoVisitor visitor) {
        for (Map.Entry<String, TaskPropertyInfo> entry : properties.entrySet()) {
            TaskPropertyInfo property = entry.getValue();
            visitor.visit(entry.getKey(), property, value);
        }
    }

    public interface TaskPropertyInfoVisitor {
        void visit(String propertyName, TaskPropertyInfo property, Object value);
    }

    Map<String, TaskPropertyInfo> getProperties() {
        return properties;
    }
}
