/*
 * Copyright 2015 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentIdentifier;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.artifacts.component.LibraryComponentSelector;
import org.gradle.api.internal.component.ArtifactType;
import org.gradle.internal.component.external.model.MetadataSourcedComponentArtifacts;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.internal.component.model.ComponentArtifactMetadata;
import org.gradle.internal.component.model.ComponentOverrideMetadata;
import org.gradle.internal.component.model.ComponentResolveMetadata;
import org.gradle.internal.component.model.DependencyMetadata;
import org.gradle.internal.component.model.ModuleSource;
import org.gradle.internal.resolve.ArtifactResolveException;
import org.gradle.internal.resolve.ModuleVersionResolveException;
import org.gradle.internal.resolve.resolver.ArtifactResolver;
import org.gradle.internal.resolve.resolver.ComponentMetaDataResolver;
import org.gradle.internal.resolve.resolver.DependencyToComponentIdResolver;
import org.gradle.internal.resolve.result.BuildableArtifactResolveResult;
import org.gradle.internal.resolve.result.BuildableArtifactSetResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentArtifactsResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentIdResolveResult;
import org.gradle.internal.resolve.result.BuildableComponentResolveResult;
import org.gradle.language.base.internal.resolve.LibraryResolveException;
import org.gradle.platform.base.Binary;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.VariantComponent;
import org.gradle.platform.base.VariantComponentSpec;

import java.util.Collection;
import java.util.Collections;

public class LocalLibraryDependencyResolver implements DependencyToComponentIdResolver, ComponentMetaDataResolver, ArtifactResolver {
    private final VariantChooser variantChooser;
    private final LibraryResolutionErrorMessageBuilder errorMessageBuilder;
    private final LocalLibraryMetaDataAdapter libraryMetaDataAdapter;
    private final LocalLibraryResolver libraryResolver;

    public LocalLibraryDependencyResolver(LocalLibraryResolver libraryResolver,
                                          VariantChooser variantChooser,
                                          LocalLibraryMetaDataAdapter libraryMetaDataAdapter,
                                          LibraryResolutionErrorMessageBuilder errorMessageBuilder) {
        this.libraryMetaDataAdapter = libraryMetaDataAdapter;
        this.variantChooser = variantChooser;
        this.errorMessageBuilder = errorMessageBuilder;
        this.libraryResolver = libraryResolver;
    }

    @Override
    public void resolve(DependencyMetadata dependency, final BuildableComponentIdResolveResult result) {
        if (dependency.getSelector() instanceof LibraryComponentSelector) {
            LibraryComponentSelector selector = (LibraryComponentSelector) dependency.getSelector();
            resolveLibraryAndChooseBinary(result, selector);
        }
    }

    private void resolveLibraryAndChooseBinary(BuildableComponentIdResolveResult result, LibraryComponentSelector selector) {
        final String selectorProjectPath = selector.getProjectPath();
        final String libraryName = selector.getLibraryName();
        final String variant = selector.getVariant();
        LibraryResolutionResult resolutionResult = libraryResolver.resolve(selectorProjectPath, libraryName);
        VariantComponentSpec selectedLibrary = resolutionResult.getSelectedLibrary();
        if (selectedLibrary == null) {
            String message = resolutionResult.toResolutionErrorMessage(selector);
            ModuleVersionResolveException failure = new ModuleVersionResolveException(selector, new LibraryResolveException(message));
            result.failed(failure);
            return;
        }

        Collection<? extends Binary> matchingVariants = chooseMatchingVariants(selectedLibrary, variant);
        if (matchingVariants.isEmpty()) {
            // no compatible variant found
            Collection<BinarySpec> values = selectedLibrary.getBinaries().values();
            result.failed(new ModuleVersionResolveException(selector, errorMessageBuilder.noCompatibleVariantErrorMessage(libraryName, values)));
        } else if (matchingVariants.size() > 1) {
            result.failed(new ModuleVersionResolveException(selector, errorMessageBuilder.multipleCompatibleVariantsErrorMessage(libraryName, matchingVariants)));
        } else {
            Binary selectedBinary = matchingVariants.iterator().next();
            // TODO:Cedric This is not quite right. We assume that if we are asking for a specific binary, then we resolve to the assembly instead
            // of the jar, but it should be somehow parametrized
            LocalComponentMetadata metaData;
            if (variant == null) {
                metaData = libraryMetaDataAdapter.createLocalComponentMetaData(selectedBinary, selectorProjectPath, false);
            } else {
                metaData = libraryMetaDataAdapter.createLocalComponentMetaData(selectedBinary, selectorProjectPath, true);
            }
            result.resolved(metaData);
        }
    }

    private Collection<? extends Binary> chooseMatchingVariants(VariantComponent selectedLibrary, String variant) {
        return variantChooser.chooseMatchingVariants(selectedLibrary, variant);
    }


    @Override
    public void resolve(ComponentIdentifier identifier, ComponentOverrideMetadata componentOverrideMetadata, BuildableComponentResolveResult result) {
        if (isLibrary(identifier)) {
            throw new RuntimeException("Not yet implemented");
        }
    }

    private boolean isLibrary(ComponentIdentifier identifier) {
        return identifier instanceof LibraryBinaryIdentifier;
    }

    @Override
    public void resolveArtifacts(ComponentResolveMetadata component, BuildableComponentArtifactsResolveResult result) {
        ComponentIdentifier componentId = component.getComponentId();
        if (isLibrary(componentId)) {
            result.resolved(new MetadataSourcedComponentArtifacts());
        }
    }

    @Override
    public void resolveArtifactsWithType(ComponentResolveMetadata component, ArtifactType artifactType, BuildableArtifactSetResolveResult result) {
        if (isLibrary(component.getComponentId())) {
            result.resolved(Collections.<ComponentArtifactMetadata>emptySet());
        }
    }

    @Override
    public void resolveArtifact(ComponentArtifactMetadata artifact, ModuleSource moduleSource, BuildableArtifactResolveResult result) {
        if (isLibrary(artifact.getComponentId())) {
            if (artifact instanceof PublishArtifactLocalArtifactMetadata) {
                result.resolved(((PublishArtifactLocalArtifactMetadata) artifact).getFile());
            } else {
                result.failed(new ArtifactResolveException("Unsupported artifact metadata type: " + artifact));
            }
        }
    }

}
