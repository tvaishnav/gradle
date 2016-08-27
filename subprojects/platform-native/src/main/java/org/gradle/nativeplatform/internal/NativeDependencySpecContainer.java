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

package org.gradle.nativeplatform.internal;

import org.gradle.api.Transformer;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.nativeplatform.NativeLibraryRequirement;
import org.gradle.nativeplatform.internal.resolve.NativeDependencyNotationParser;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.DependencySpecContainer;
import org.gradle.platform.base.ModuleDependencySpecBuilder;
import org.gradle.platform.base.ProjectDependencySpecBuilder;
import org.gradle.util.CollectionUtils;

import java.util.Collection;

public class NativeDependencySpecContainer implements DependencySpecContainer {
    private final Collection<?> libs;

    public NativeDependencySpecContainer(Collection<?> libs) {
        this.libs = libs;
    }

    @Override
    public ProjectDependencySpecBuilder project(String path) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ProjectDependencySpecBuilder library(String name) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ModuleDependencySpecBuilder module(String moduleIdOrName) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public ModuleDependencySpecBuilder group(String name) {
        throw new UnsupportedOperationException("not implemented");
    }

    @Override
    public Collection<DependencySpec> getDependencies() {
        return CollectionUtils.collect(libs, new Transformer<DependencySpec, Object>() {
            @Override
            public DependencySpec transform(Object o) {
                return parser.parseNotation(o);
            }
        });
    }

    @Override
    public boolean isEmpty() {
        return libs.isEmpty();
    }

    private final NotationParser<Object, NativeLibraryRequirement> parser = NativeDependencyNotationParser.parser();
}
