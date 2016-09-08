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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.Nested;

import java.util.Collection;
import java.util.Map;

import static org.gradle.api.internal.project.taskfactory.TaskPropertyParserUtils.findProperties;

class NestedTaskPropertyInfoCreator extends TaskPropertyInfoCreator<Nested> {
    @Override
    public Class<Nested> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public void createProperties(TaskPropertyInfoContext context, TaskPropertyInfoCollector properties) {
        createNestedProperty(context.getName(), context.getType(), context, properties);
    }

    private void createNestedProperty(String propertyName, Class<?> type, TaskPropertyInfoContext context, TaskPropertyInfoCollector properties) {
        NestedTaskPropertyInfoContext nestedContext = new NestedTaskPropertyInfoContext(context.getName(), properties);
        findProperties(type, nestedContext);
        properties.recordAnnotatedProperty(propertyName, new NestedPropertyInfo(context, nestedContext.getProperties()));
    }

    private static class NestedTaskPropertyInfoContext implements TaskPropertyInfoCollector {
        private final String propertyName;
        private final TaskPropertyInfoCollector parentContext;
        private final ImmutableMap.Builder<String, TaskPropertyInfo> propertiesBuilder = ImmutableMap.builder();

        public NestedTaskPropertyInfoContext(String propertyName, TaskPropertyInfoCollector parentContext) {
            this.propertyName = propertyName;
            this.parentContext = parentContext;
        }

        @Override
        public void recordAnnotatedProperty(String propertyName, TaskPropertyInfo property) {
            propertiesBuilder.put(propertyName, property);
        }

        @Override
        public void recordNonAnnotatedPropertyName(String propertyName) {
            parentContext.recordNonAnnotatedPropertyName(this.propertyName + "." + propertyName);
        }

        public Map<String, TaskPropertyInfo> getProperties() {
            return propertiesBuilder.build();
        }
    }

    private static class NestedPropertyInfo extends AbstractTaskPropertyInfo {
        private final TaskPropertyInfoContainer children;

        public NestedPropertyInfo(TaskPropertyInfoContext context, Map<String, TaskPropertyInfo> childProperties) {
            super(context);
            this.children = new TaskPropertyInfoContainer(childProperties);
        }

        @Override
        protected void validateNonNullValue(final TaskInternal task, final String propertyName, Object value, final Collection<String> messages) {
            if (value == null) {
                return;
            }
            children.visitProperties(value, new TaskPropertyInfoContainer.TaskPropertyInfoVisitor() {
                @Override
                public void visit(String childPropertyName, TaskPropertyInfo childProperty, Object value) {
                    childProperty.validate(task, propertyName + "." + childPropertyName, value, messages);
                }
            });
        }

        @Override
        protected void processValue(final TaskInternal task, final String propertyName, Object value) {
            task.getInputs().property(propertyName + ".class", value == null ? null : value.getClass().getName());
            if (value == null) {
                return;
            }
            children.visitProperties(value, new TaskPropertyInfoContainer.TaskPropertyInfoVisitor() {
                @Override
                public void visit(String childPropertyName, TaskPropertyInfo childProperty, Object value) {
                    childProperty.process(task, propertyName + "." + childPropertyName, value);
                }
            });
        }
    }
}
