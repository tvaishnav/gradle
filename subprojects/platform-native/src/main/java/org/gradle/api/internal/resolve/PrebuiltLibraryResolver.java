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

package org.gradle.api.internal.resolve;

import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.gradle.api.UnknownProjectException;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.PrebuiltLibraries;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.platform.base.VariantComponent;

import java.util.List;

public class PrebuiltLibraryResolver implements LocalLibraryResolver {
    private static final ModelType<ModelMap<PrebuiltLibraries>> PREBUILT_LIBRARIES_TYPE = ModelTypes.modelMap(PrebuiltLibraries.class);
    private final ProjectModelResolver projectModelResolver;
    private final Predicate<VariantComponent> binarySpecPredicate;

    public PrebuiltLibraryResolver(ProjectModelResolver projectModelResolver) {
        this.projectModelResolver = projectModelResolver;
        this.binarySpecPredicate = new Predicate<VariantComponent>() {
            @Override
            public boolean apply(VariantComponent input) {
                return Iterables.size(input.getVariants()) != 0;
            }
        };
    }

    @Override
    public LibraryResolutionResult resolve(String projectPath, String libraryName) {
        try {
            ModelRegistry projectModel = projectModelResolver.resolveProjectModel(projectPath);
            LibraryResolutionResult libraries = findLocalComponent(libraryName, projectModel);
            if (libraries != null) {
                return libraries;
            }

            return LibraryResolutionResult.emptyResolutionResult(NativeLibraryBinary.class);
        } catch (UnknownProjectException e) {
            return LibraryResolutionResult.projectNotFound(NativeLibraryBinary.class);
        }
    }

    private LibraryResolutionResult findLocalComponent(String componentName, ModelRegistry projectModel) {
        List<PrebuiltLibrary> librarySpecs = Lists.newArrayList();
        collectLocalComponents(projectModel, componentName, librarySpecs);
        if (librarySpecs.isEmpty()) {
            return null;
        }
        return LibraryResolutionResult.of(NativeLibraryBinary.class, librarySpecs, componentName, binarySpecPredicate);
    }

    private void collectLocalComponents(ModelRegistry projectModel, String componentName, List<PrebuiltLibrary> librarySpecs) {
        ModelMap<PrebuiltLibraries> prebuiltLibraries = projectModel.find("repositories", PREBUILT_LIBRARIES_TYPE);
        if (prebuiltLibraries != null) {
            for (PrebuiltLibraries repository : prebuiltLibraries) {
                PrebuiltLibrary prebuiltLibrary = repository.resolveLibrary(componentName);
                if (prebuiltLibrary!=null) {
                    librarySpecs.add(prebuiltLibrary);
                }
            }
        }
    }
}
