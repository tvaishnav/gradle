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
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import groovy.lang.GroovyObject;
import org.gradle.api.DefaultTask;
import org.gradle.api.Task;
import org.gradle.api.internal.AbstractTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.api.internal.tasks.options.OptionValues;
import org.gradle.api.tasks.Console;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OrderSensitive;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.SkipWhenEmpty;
import org.gradle.internal.Cast;
import org.gradle.internal.reflect.GroovyMethods;
import org.gradle.internal.reflect.PropertyAccessorType;
import org.gradle.internal.reflect.Types;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

class TaskPropertyParserUtils {
    // Avoid reflecting on classes we know we don't need to look at
    private static final Collection<Class<?>> IGNORED_SUPER_CLASSES = ImmutableSet.<Class<?>>of(
        ConventionTask.class, DefaultTask.class, AbstractTask.class, Task.class, Object.class, GroovyObject.class
    );

    private static final List<TaskPropertyAnnotationHandler<?>> ANNOTATION_HANDLERS = ImmutableList.<TaskPropertyAnnotationHandler<?>>builder()
        .add(new InputTaskPropertyInfoCreator())
        .add(new InputFileTaskPropertyInfoCreator())
        .add(new InputDirectoryTaskPropertyInfoCreator())
        .add(new InputFilesTaskPropertyInfoCreator())
        .add(new OutputFileTaskPropertyInfoCreator())
        .add(new OutputDirectoryTaskPropertyInfoCreator())
        .add(new OutputFilesTaskPropertyInfoCreator())
        .add(new OutputDirectoriesTaskPropertyInfoCreator())
        .add(new NestedTaskPropertyInfoCreator())
        .add(NoOpTaskPropertyInfoCreator.of(Console.class))
        .add(NoOpTaskPropertyInfoCreator.of(Internal.class))
        .add(NoOpTaskPropertyInfoCreator.of(Inject.class))
        .add(NoOpTaskPropertyInfoCreator.of(OptionValues.class))
        .add(new OptionalTaskPropertyAnnotationHandler())
        .add(new SkipWhenEmptyTaskPropertyAnnotationHandler())
        .add(new OrderSensitiveTaskPropertyAnnotationHandler())
        .add(new PathSensitiveTaskPropertyAnnotationHandler())
        .build();

    public static <T> void findProperties(Class<T> type, TaskPropertyInfoCollector properties) {
        final Map<String, TaskPropertyInfoContext> propertiesSeen = Maps.newHashMap();
        Types.walkTypeHierarchy(type, IGNORED_SUPER_CLASSES, new Types.TypeVisitor<T>() {
            @Override
            public void visitType(Class<? super T> type) {
                Map<String, Field> fields = getFields(type);
                for (Method method : type.getDeclaredMethods()) {
                    PropertyAccessorType accessorType = PropertyAccessorType.of(method);
                    if (accessorType == null || accessorType == PropertyAccessorType.SETTER || method.isBridge() || GroovyMethods.isObjectMethod(method)) {
                        continue;
                    }

                    String propertyName = accessorType.propertyNameFor(method);
                    Field field = fields.get(propertyName);

                    TaskPropertyInfoContext propertyContext = propertiesSeen.get(propertyName);
                    if (propertyContext == null) {
                        propertyContext = new TaskPropertyInfoContext(propertyName, method);
                        propertiesSeen.put(propertyName, propertyContext);
                    }

                    handleAnnotations(propertyContext, method.getDeclaredAnnotations());
                    if (field != null) {
                        propertyContext.setField(field);
                        handleAnnotations(propertyContext, field.getDeclaredAnnotations());
                    }
                }
            }
        });

        List<TaskPropertyInfoContext> sortedProperties = Lists.newArrayList(propertiesSeen.values());
        Collections.sort(sortedProperties, new Comparator<TaskPropertyInfoContext>() {
            @Override
            public int compare(TaskPropertyInfoContext o1, TaskPropertyInfoContext o2) {
                return o1.getName().compareTo(o2.getName());
            }
        });
        for (TaskPropertyInfoContext propertyContext : sortedProperties) {
            TaskPropertyInfoCreator<?> creator = propertyContext.getPropertyCreator();
            if (creator != null) {
                creator.createProperties(propertyContext, properties);
            } else {
                properties.recordNonAnnotatedPropertyName(propertyContext.getName());
            }
        }
    }

    private static void handleAnnotations(TaskPropertyInfoContext context, Annotation[] annotations) {
        for (Annotation annotation : annotations) {
            for (TaskPropertyAnnotationHandler<?> handler : ANNOTATION_HANDLERS) {
                if (handler.getAnnotationType().isAssignableFrom(annotation.getClass())) {
                    Cast.<TaskPropertyAnnotationHandler<Annotation>>uncheckedCast(handler).handleAnnotation(context, annotation);
                    break;
                }
            }
        }
    }

    private static Map<String, Field> getFields(Class<?> type) {
        Map<String, Field> fields = Maps.newLinkedHashMap();
        for (Field field : type.getDeclaredFields()) {
            fields.put(field.getName(), field);
        }
        return fields;
    }

    private static class NoOpTaskPropertyInfoCreator<A extends Annotation> extends TaskPropertyInfoCreator<A> {
        private final Class<A> annotationType;

        public NoOpTaskPropertyInfoCreator(Class<A> annotationType) {
            this.annotationType = annotationType;
        }

        @Override
        public Class<A> getAnnotationType() {
            return annotationType;
        }

        @Override
        public void createProperties(TaskPropertyInfoContext context, TaskPropertyInfoCollector properties) {
        }

        public static <A extends Annotation> NoOpTaskPropertyInfoCreator<A> of(Class<A> annotationType) {
            return new NoOpTaskPropertyInfoCreator<A>(annotationType);
        }
    }

    private static class OptionalTaskPropertyAnnotationHandler implements TaskPropertyAnnotationHandler<Optional> {
        @Override
        public Class<Optional> getAnnotationType() {
            return Optional.class;
        }

        @Override
        public void handleAnnotation(TaskPropertyInfoContext context, Optional annotation) {
            context.markOptional();
        }
    }

    private static class SkipWhenEmptyTaskPropertyAnnotationHandler implements TaskPropertyAnnotationHandler<SkipWhenEmpty> {
        @Override
        public Class<SkipWhenEmpty> getAnnotationType() {
            return SkipWhenEmpty.class;
        }

        @Override
        public void handleAnnotation(TaskPropertyInfoContext context, SkipWhenEmpty annotation) {
            context.markSkipWhenEmpty();
        }
    }

    private static class OrderSensitiveTaskPropertyAnnotationHandler implements TaskPropertyAnnotationHandler<OrderSensitive> {
        @Override
        public Class<OrderSensitive> getAnnotationType() {
            return OrderSensitive.class;
        }

        @Override
        public void handleAnnotation(TaskPropertyInfoContext context, OrderSensitive annotation) {
            context.markOrderSensitive();
        }
    }

    private static class PathSensitiveTaskPropertyAnnotationHandler implements TaskPropertyAnnotationHandler<PathSensitive> {
        @Override
        public Class<PathSensitive> getAnnotationType() {
            return PathSensitive.class;
        }

        @Override
        public void handleAnnotation(TaskPropertyInfoContext context, PathSensitive annotation) {
            context.setPathSensitivity(annotation.value());
        }
    }
}
