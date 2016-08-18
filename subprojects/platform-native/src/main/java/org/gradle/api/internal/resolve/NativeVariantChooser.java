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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Nullable;
import org.gradle.nativeplatform.BuildType;
import org.gradle.nativeplatform.Flavor;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.nativeplatform.SharedLibraryBinarySpec;
import org.gradle.nativeplatform.StaticLibraryBinarySpec;
import org.gradle.nativeplatform.platform.NativePlatform;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.VariantComponentSpec;

import java.util.Collection;
import java.util.Collections;

public class NativeVariantChooser implements VariantChooser {
    @Override
    public Collection<? extends BinarySpec> chooseMatchingVariants(VariantComponentSpec componentSpec, @Nullable String linkage) {
        Class<? extends NativeLibraryBinarySpec> type = getTypeForLinkage(linkage);
        Collection<? extends NativeLibraryBinarySpec> candidateBinaries = componentSpec.getBinaries().withType(type).values();
        return resolve(candidateBinaries, null, null, null);
    }

    private Class<? extends NativeLibraryBinarySpec> getTypeForLinkage(String linkage) {
        if ("static".equals(linkage)) {
            return StaticLibraryBinarySpec.class;
        }
        if ("shared".equals(linkage) || linkage == null) {
            return SharedLibraryBinarySpec.class;
        }
        throw new InvalidUserDataException("Not a valid linkage: " + linkage);
    }

    private Collection<NativeLibraryBinarySpec> resolve(Collection<? extends NativeLibraryBinarySpec> candidates, Flavor flavor, NativePlatform platform, BuildType buildType) {
        for (NativeLibraryBinarySpec candidate : candidates) {
            if (flavor != null && !flavor.getName().equals(candidate.getFlavor().getName())) {
                continue;
            }
            if (platform != null && !platform.getName().equals(candidate.getTargetPlatform().getName())) {
                continue;
            }
            if (buildType != null && !buildType.getName().equals(candidate.getBuildType().getName())) {
                continue;
            }

            return Collections.singleton(candidate);
        }
        return Collections.emptySet();
    }

}
