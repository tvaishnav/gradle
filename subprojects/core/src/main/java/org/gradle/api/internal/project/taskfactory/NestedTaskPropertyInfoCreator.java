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

import com.google.common.base.Function;
import com.google.common.collect.Collections2;
import com.google.common.reflect.TypeToken;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.Nested;
import org.gradle.internal.Cast;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import static org.gradle.internal.Cast.uncheckedCast;

class NestedTaskPropertyInfoCreator extends TaskPropertyInfoCreator<Nested> {
    private static final TypeToken<Iterable> ITERABLE_TYPE = TypeToken.of(Iterable.class);
    private static final TypeToken<Map> MAP_TYPE = TypeToken.of(Map.class);
    @Override
    public Class<Nested> getAnnotationType() {
        return Nested.class;
    }

    @Override
    public void handleAnnotation(TaskPropertyInfoContext context, Nested annotation) {
        super.handleAnnotation(context, annotation);
        context.setResolveCollections(annotation.resolveCollections());
    }

    @Override
    public TaskPropertyInfo createProperty(TaskPropertyInfoContext context) {
        return createNestedProperty(context.getType(), context);
    }

    private TaskPropertyInfo createNestedProperty(Type type, TaskPropertyInfoContext context) {
        boolean optional = context.isOptional();
        if (context.isResolveCollections()) {
            if (ITERABLE_TYPE.isAssignableFrom(type)) {
                if (!context.isOrderSensitive()) {
                    throw new IllegalArgumentException(String.format("Iterable task property '%s' must also be annotated with @OrderSensitive", context.getName()));
                }
                TypeToken<? extends Iterable<?>> typeToken = uncheckedCast(TypeToken.of(type));
                Type iterableType = typeToken.getSupertype(Iterable.class).getType();
                if (!(iterableType instanceof ParameterizedType)) {
                    throw new IllegalArgumentException(String.format("Iterable task property '%s' must be parameterized",
                        context.getName()));
                }
                Type elementType = ((ParameterizedType) iterableType).getActualTypeArguments()[0];
                return new IterableTaskPropertyInfo(createNestedProperty(elementType, context), optional);
            } else if (MAP_TYPE.isAssignableFrom(type)) {
                TypeToken<? extends Map> typeToken = uncheckedCast(TypeToken.of(type));
                Type mapType = typeToken.getSupertype(Map.class).getType();
                if (!(mapType instanceof ParameterizedType)) {
                    throw new IllegalArgumentException(String.format("Map task property '%s' must be parameterized",
                        context.getName()));
                }
                Type keyType = ((ParameterizedType) mapType).getActualTypeArguments()[0];
                if (keyType != String.class) {
                    throw new IllegalArgumentException(String.format("Map task property '%s' key type must be String",
                        context.getName()));
                }
                Type valueType = ((ParameterizedType) mapType).getActualTypeArguments()[1];
                return new MapTaskPropertyInfo(createNestedProperty(valueType, context), optional);
            }
        }
        if (!(type instanceof Class)) {
            throw new IllegalArgumentException(String.format("Nested property '%s' must be an object type",
                context.getName()));
        }
        return new NestedPropertyInfo((Class<?>) type, optional);
    }

    private static class NestedPropertyInfo extends AbstractTaskPropertyInfo {
        private final Class<?> type;

        public NestedPropertyInfo(Class<?> type, boolean optional) {
            super(optional);
            this.type = type;
        }

        @Override
        public void process(TaskInternal task, String propertyName, Object value) {
            task.getInputs().property(propertyName + ".class", value == null ? null : value.getClass().getName());
        }

        @Override
        public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Object value, TaskPropertyValueVisitor visitor) {
            super.acceptVisitor(classInfoStore, propertyName, value, visitor);
            classInfoStore.getClassInfo(type).getProperties().visitValues(classInfoStore, propertyName, value, visitor);
        }

        @Override
        public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Set<Type> visitedTypes, TaskPropertyDeclarationVisitor visitor) {
            super.acceptVisitor(classInfoStore, propertyName, visitedTypes, visitor);
            if (!visitedTypes.add(type)) {
                return;
            }
            classInfoStore.getClassInfo(type).getProperties().visitDeclarations(classInfoStore, propertyName, visitedTypes, visitor);
        }

        @Override
        public Collection<String> getNonAnnotatedSubProperties(final String propertyName, ClassInfoStore classInfoStore) {
            return Collections2.transform(classInfoStore.getClassInfo(type).getNonAnnotatedProperties(), new Function<String, String>() {
                @Override
                public String apply(String name) {
                    return propertyName + "." + name;
                }
            });
        }
    }

    private static class IterableTaskPropertyInfo extends AbstractTaskPropertyInfo {
        private final TaskPropertyInfo childProperty;

        public IterableTaskPropertyInfo(TaskPropertyInfo childProperty, boolean optional) {
            super(optional);
            this.childProperty = childProperty;
        }

        @Override
        public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Object parentValue, TaskPropertyValueVisitor visitor) {
            super.acceptVisitor(classInfoStore, propertyName, parentValue, visitor);
            if (parentValue == null) {
                return;
            }
            Iterator<?> iterator = ((Iterable<?>) parentValue).iterator();
            int index = 1;
            while (iterator.hasNext()) {
                Object childValue = iterator.next();
                childProperty.acceptVisitor(classInfoStore, propertyName + "$" + index, childValue, visitor);
                index++;
            }
        }

        @Override
        public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Set<Type> visitedTypes, TaskPropertyDeclarationVisitor visitor) {
            childProperty.acceptVisitor(classInfoStore, propertyName, visitedTypes, visitor);
        }
    }

    private static class MapTaskPropertyInfo extends AbstractTaskPropertyInfo {
        private final TaskPropertyInfo childProperty;

        public MapTaskPropertyInfo(TaskPropertyInfo childProperty, boolean optional) {
            super(optional);
            this.childProperty = childProperty;
        }

        @Override
        public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Object parentValue, TaskPropertyValueVisitor visitor) {
            super.acceptVisitor(classInfoStore, propertyName, parentValue, visitor);
            if (parentValue == null) {
                return;
            }
            for (Map.Entry<String, ?> entry : Cast.<Map<String, ?>>uncheckedCast(parentValue).entrySet()) {
                Object childValue = entry.getValue();
                String childPropertyName = entry.getKey();
                childProperty.acceptVisitor(classInfoStore, propertyName + "." + childPropertyName, childValue, visitor);
            }
        }

        @Override
        public void acceptVisitor(ClassInfoStore classInfoStore, String propertyName, Set<Type> visitedTypes, TaskPropertyDeclarationVisitor visitor) {
            childProperty.acceptVisitor(classInfoStore, propertyName, visitedTypes, visitor);
        }
    }
}
