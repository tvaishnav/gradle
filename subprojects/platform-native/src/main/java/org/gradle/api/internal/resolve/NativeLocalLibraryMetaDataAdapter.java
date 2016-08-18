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

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.component.LibraryBinaryIdentifier;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.component.local.model.DefaultLibraryBinaryIdentifier;
import org.gradle.internal.component.local.model.LocalComponentMetadata;
import org.gradle.internal.component.local.model.PublishArtifactLocalArtifactMetadata;
import org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata;
import org.gradle.nativeplatform.NativeLibraryBinary;
import org.gradle.nativeplatform.NativeLibraryBinarySpec;
import org.gradle.platform.base.BinarySpec;
import org.gradle.platform.base.DependencySpec;
import org.gradle.platform.base.LibraryBinarySpec;

import java.io.File;
import java.util.Collections;
import java.util.Map;

import static org.gradle.language.base.internal.model.DefaultLibraryLocalComponentMetadata.newResolvedLibraryMetadata;

public class NativeLocalLibraryMetaDataAdapter implements LocalLibraryMetaDataAdapter {

    @Override
    @SuppressWarnings("unchecked")
    public LocalComponentMetadata createLocalComponentMetaData(BinarySpec selectedBinary, String projectPath, boolean toAssembly) {

        if (selectedBinary instanceof NativeLibraryBinarySpec) {
            return createForNativeLibrary((NativeLibraryBinarySpec) selectedBinary);
        }
        throw new RuntimeException("Can't create metadata for binary: " + selectedBinary);
    }

    private static LocalComponentMetadata createForNativeLibrary(NativeLibraryBinarySpec sharedLib) {
        LibraryBinaryIdentifier id = createComponentId(sharedLib);
        NativeLibraryBinary libraryBinary = (NativeLibraryBinary) sharedLib;
        DefaultLibraryLocalComponentMetadata metadata = createComponentMetadata(id, libraryBinary);

        for (File headerDir : libraryBinary.getHeaderDirs()) {
            PublishArtifact headerDirArtifact = new LibraryPublishArtifact("header", headerDir);
            metadata.addArtifact("compile", new PublishArtifactLocalArtifactMetadata(id, sharedLib.getDisplayName(), headerDirArtifact));
        }

        for (File linkFile : libraryBinary.getLinkFiles()) {
            PublishArtifact linkFileArtifact = new LibraryPublishArtifact("link-file", linkFile);
            metadata.addArtifact("link", new PublishArtifactLocalArtifactMetadata(id, sharedLib.getDisplayName(), linkFileArtifact));
        }

        for (File runtimeFile : libraryBinary.getRuntimeFiles()) {
            PublishArtifact runtimeFileArtifact = new LibraryPublishArtifact("runtime-file", runtimeFile);
            metadata.addArtifact("run", new PublishArtifactLocalArtifactMetadata(id, sharedLib.getDisplayName(), runtimeFileArtifact));
        }

        return metadata;
    }

    private static LibraryBinaryIdentifier createComponentId(LibraryBinarySpec staticLib) {
        String projectPath = staticLib.getProjectPath();
        return new DefaultLibraryBinaryIdentifier(projectPath, staticLib.getLibrary().getName(), "staticLibrary");
    }

    private static DefaultLibraryLocalComponentMetadata createComponentMetadata(LibraryBinaryIdentifier id, NativeLibraryBinary sharedLib) {
        // TODO:DAZ Should wire task dependencies to artifacts, not configurations.
        Map<String, TaskDependency> configurations = Maps.newLinkedHashMap();
        configurations.put("compile", new DefaultTaskDependency().add(sharedLib.getHeaderDirs()));
        configurations.put("link", new DefaultTaskDependency().add(sharedLib.getLinkFiles()));
        configurations.put("run", new DefaultTaskDependency().add(sharedLib.getRuntimeFiles()));

        // TODO:DAZ For transitive dependency resolution, include dependencies from lib
        Map<String, Iterable<DependencySpec>> dependencies = Collections.emptyMap();

        return newResolvedLibraryMetadata(id, configurations, dependencies, null);
    }
}
