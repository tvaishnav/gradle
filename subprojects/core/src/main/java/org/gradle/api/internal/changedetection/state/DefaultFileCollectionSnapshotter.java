/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.file.FileVisitDetails;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.internal.cache.StringInterner;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.SingletonFileTree;
import org.gradle.api.internal.tasks.TaskFilePropertySpec;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.cache.CacheAccess;
import org.gradle.internal.Factory;
import org.gradle.internal.nativeintegration.filesystem.FileSystem;
import org.gradle.internal.serialize.SerializerRegistry;

import java.io.File;
import java.util.List;
import java.util.Map;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter {
    private static final DefaultFileCollectionSnapshot EMPTY_SNAPSHOT = new DefaultFileCollectionSnapshot(ImmutableMap.<String, NormalizedFileSnapshot>of(), UNORDERED);
    private final FileSnapshotter snapshotter;
    private final StringInterner stringInterner;
    private final CacheAccess cacheAccess;
    private final FileSystem fileSystem;
    private final Factory<PatternSet> patternSetFactory;

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileSystem fileSystem, Factory<PatternSet> patternSetFactory) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.patternSetFactory = patternSetFactory;
    }

    @Override
    public FileCollectionSnapshot emptySnapshot() {
        return EMPTY_SNAPSHOT;
    }

    public void registerSerializers(SerializerRegistry registry) {
        registry.register(DefaultFileCollectionSnapshot.class, new DefaultFileCollectionSnapshot.SerializerImpl(stringInterner));
    }

    @Override
    public FileCollectionSnapshot snapshot(FileCollection input, TaskFilePropertyCompareStrategy compareStrategy, final SnapshotNormalizationStrategy snapshotNormalizationStrategy) {
        FileCollectionInternal fileCollection = (FileCollectionInternal) input;
        final FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl(snapshotNormalizationStrategy, fileSystem, snapshotter, stringInterner, patternSetFactory);
        fileCollection.visitRootElements(visitor);

        if (visitor.snapshots.isEmpty()) {
            return emptySnapshot();
        }

        final Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (SnapshotProducer producer : visitor.snapshots) {
                    producer.addTo(snapshots);
                }
            }
        });
        return new DefaultFileCollectionSnapshot(snapshots, compareStrategy);
    }

    @Override
    public FileCollectionSnapshot snapshot(TaskFilePropertySpec propertySpec) {
        return snapshot(propertySpec.getPropertyFiles(), propertySpec.getCompareStrategy(), propertySpec.getSnapshotNormalizationStrategy());
    }

    private interface SnapshotProducer {
        void addTo(Map<String, NormalizedFileSnapshot> snapshots);
    }

    private static class FileSnapshotProducer implements SnapshotProducer {
        private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
        private final StringInterner stringInterner;
        private final FileSnapshotter snapshotter;
        private final FileTreeElement fileDetails;

        FileSnapshotProducer(FileTreeElement fileDetails, SnapshotNormalizationStrategy snapshotNormalizationStrategy, FileSnapshotter snapshotter, StringInterner stringInterner) {
            this.fileDetails = fileDetails;
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            this.snapshotter = snapshotter;
            this.stringInterner = stringInterner;
        }

        @Override
        public void addTo(Map<String, NormalizedFileSnapshot> snapshots) {
            String absolutePath = stringInterner.intern(fileDetails.getFile().getAbsolutePath());
            if (!snapshots.containsKey(absolutePath)) {
                IncrementalFileSnapshot snapshot;
                if (fileDetails.isDirectory()) {
                    snapshot = DirSnapshot.getInstance();
                } else {
                    snapshot = new FileHashSnapshot(snapshotter.snapshot(fileDetails).getHash(), fileDetails.getLastModified());
                }
                NormalizedFileSnapshot normalizedSnapshot = snapshotNormalizationStrategy.getNormalizedSnapshot(fileDetails, snapshot, stringInterner);
                if (normalizedSnapshot != null) {
                    snapshots.put(absolutePath, normalizedSnapshot);
                }
            }
        }
    }

    private static class MissingFileSnapshotProducer implements SnapshotProducer {
        private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
        private final StringInterner stringInterner;
        private final File file;

        MissingFileSnapshotProducer(File file, SnapshotNormalizationStrategy snapshotNormalizationStrategy, StringInterner stringInterner) {
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            this.stringInterner = stringInterner;
            this.file = file;
        }

        @Override
        public void addTo(Map<String, NormalizedFileSnapshot> snapshots) {
            String absolutePath = stringInterner.intern(file.getAbsolutePath());
            if (!snapshots.containsKey(absolutePath)) {
                snapshots.put(absolutePath, snapshotNormalizationStrategy.getNormalizedSnapshot(new MissingFileVisitDetails(file), MissingFileSnapshot.getInstance(), stringInterner));
            }
        }
    }

    private static class FileCollectionVisitorImpl implements FileCollectionVisitor, FileVisitor {
        private final FileSystem fileSystem;
        private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
        private final StringInterner stringInterner;
        private final Factory<PatternSet> patternSetFactory;
        private final FileSnapshotter snapshotter;
        private final List<SnapshotProducer> snapshots = Lists.newArrayList();

        FileCollectionVisitorImpl(SnapshotNormalizationStrategy snapshotNormalizationStrategy, FileSystem fileSystem, FileSnapshotter snapshotter, StringInterner stringInterner, Factory<PatternSet> patternSetFactory) {
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            this.fileSystem = fileSystem;
            this.snapshotter = snapshotter;
            this.stringInterner = stringInterner;
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                if (file.isFile()) {
                    addFileSnapshot(new SingletonFileTree.SingletonFileVisitDetails(file, fileSystem, false));
                } else if (file.isDirectory()) {
                    // Visit the directory itself, then its contents
                    addFileSnapshot(new SingletonFileTree.SingletonFileVisitDetails(file, fileSystem, true));
                    visitDirectoryTree(new DirectoryFileTree(file, patternSetFactory.create()));
                } else {
                    snapshots.add(new MissingFileSnapshotProducer(file, snapshotNormalizationStrategy, stringInterner));
                }
            }
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            fileTree.visitTreeOrBackingFile(this);
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryFileTree) {
            directoryFileTree.visit(this);
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            addFileSnapshot(dirDetails);
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            addFileSnapshot(fileDetails);
        }

        private void addFileSnapshot(FileVisitDetails fileVisitDetails) {
            snapshots.add(new FileSnapshotProducer(fileVisitDetails, snapshotNormalizationStrategy, snapshotter, stringInterner));
        }
    }
}
