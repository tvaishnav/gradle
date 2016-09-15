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
import org.gradle.api.Nullable;
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

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.gradle.api.internal.changedetection.state.TaskFilePropertyCompareStrategy.UNORDERED;

public class DefaultFileCollectionSnapshotter implements FileCollectionSnapshotter, Closeable {
    private static final DefaultFileCollectionSnapshot EMPTY_SNAPSHOT = new DefaultFileCollectionSnapshot(ImmutableMap.<String, NormalizedFileSnapshot>of(), UNORDERED);
    private final FileSnapshotter snapshotter;
    private final StringInterner stringInterner;
    private final CacheAccess cacheAccess;
    private final FileSystem fileSystem;
    private final Factory<PatternSet> patternSetFactory;
    private final AtomicLong fileSnapshotCount = new AtomicLong();
    private final AtomicLong fileReuseCount = new AtomicLong();
    private final AtomicLong treeSnapshotCount = new AtomicLong();
    private final boolean reuseStates;
    // map from interned absolute path to known details for file
    private final Map<String, FileState> files = new HashMap<String, FileState>();
    // map from interned absolute path to known details for tree
    private final Map<String, TreeState> trees = new HashMap<String, TreeState>();

    public DefaultFileCollectionSnapshotter(FileSnapshotter snapshotter, TaskArtifactStateCacheAccess cacheAccess, StringInterner stringInterner, FileSystem fileSystem, Factory<PatternSet> patternSetFactory) {
        this.snapshotter = snapshotter;
        this.cacheAccess = cacheAccess;
        this.stringInterner = stringInterner;
        this.fileSystem = fileSystem;
        this.patternSetFactory = patternSetFactory;
        reuseStates = System.getProperty("org.gradle.task.reuse-details") != null;
        System.out.println("REUSE FILE DETAILS: " + reuseStates);
    }

    @Override
    public void close() throws IOException {
        System.out.println("-- SNAPSHOTTER STATS --");
        System.out.println("root file snapshots: " + fileSnapshotCount.get());
        System.out.println("root file snapshots reused: " + fileReuseCount.get());
        System.out.println("tree snapshots: " + treeSnapshotCount.get());
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
        final FileCollectionVisitorImpl visitor = new FileCollectionVisitorImpl(files, snapshotNormalizationStrategy, fileSystem, snapshotter, stringInterner, patternSetFactory);
        fileCollection.visitRootElements(visitor);

        if (visitor.snapshots.isEmpty()) {
            return emptySnapshot();
        }

        final Map<String, NormalizedFileSnapshot> snapshots = Maps.newLinkedHashMap();

        cacheAccess.useCache("Create file snapshot", new Runnable() {
            public void run() {
                for (NormalizedSnapshotProducer producer : visitor.snapshots) {
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

    private enum FileType {
        File,
        Directory,
        Missing
    }

    private static class FileState {
        private final String path;
        private final FileType type;
        private final FileTreeElement fileDetails;
        @Nullable
        private final IncrementalFileSnapshot snapshot;

        public FileState(String path, FileType type, FileTreeElement fileDetails, @Nullable IncrementalFileSnapshot snapshot) {
            this.path = path;
            this.type = type;
            this.fileDetails = fileDetails;
            this.snapshot = snapshot;
        }
    }

    private static class TreeState {
        private final Map<String, FileState> elements;

        public TreeState(Map<String, FileState> elements) {
            this.elements = elements;
        }
    }

    private interface NormalizedSnapshotProducer {
        void addTo(Map<String, NormalizedFileSnapshot> snapshots);
    }

    private static class FileSnapshotProducer implements NormalizedSnapshotProducer {
        private final Map<String, FileState> files;
        private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
        private final StringInterner stringInterner;
        private final FileSnapshotter snapshotter;
        private final FileState fileState;

        FileSnapshotProducer(FileState fileState, Map<String, FileState> files, SnapshotNormalizationStrategy snapshotNormalizationStrategy, FileSnapshotter snapshotter, StringInterner stringInterner) {
            this.fileState = fileState;
            this.files = files;
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            this.snapshotter = snapshotter;
            this.stringInterner = stringInterner;
        }

        @Override
        public void addTo(Map<String, NormalizedFileSnapshot> snapshots) {
            String absolutePath = fileState.path;
            if (!snapshots.containsKey(absolutePath)) {
                IncrementalFileSnapshot snapshot;
                if (fileState.snapshot == null) {
                    assert fileState.type == FileType.File;
                    snapshot = new FileHashSnapshot(snapshotter.snapshot(fileState.fileDetails).getHash(), fileState.fileDetails.getLastModified());
                    files.put(absolutePath, new FileState(fileState.path, fileState.type, fileState.fileDetails, snapshot));
                } else {
                    snapshot = fileState.snapshot;
                }
                NormalizedFileSnapshot normalizedSnapshot = snapshotNormalizationStrategy.getNormalizedSnapshot(fileState.fileDetails, snapshot, stringInterner);
                if (normalizedSnapshot != null) {
                    snapshots.put(absolutePath, normalizedSnapshot);
                }
            }
        }
    }

    private static class MissingFileSnapshotProducer implements NormalizedSnapshotProducer {
        private final FileState fileState;
        private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
        private final StringInterner stringInterner;

        MissingFileSnapshotProducer(FileState fileState, SnapshotNormalizationStrategy snapshotNormalizationStrategy, StringInterner stringInterner) {
            this.fileState = fileState;
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            this.stringInterner = stringInterner;
        }

        @Override
        public void addTo(Map<String, NormalizedFileSnapshot> snapshots) {
            String absolutePath = fileState.path;
            if (!snapshots.containsKey(absolutePath)) {
                snapshots.put(absolutePath, snapshotNormalizationStrategy.getNormalizedSnapshot(fileState.fileDetails, fileState.snapshot, stringInterner));
            }
        }
    }

    private class FileCollectionVisitorImpl implements FileCollectionVisitor, FileVisitor {
        private final FileSystem fileSystem;
        private final SnapshotNormalizationStrategy snapshotNormalizationStrategy;
        private final StringInterner stringInterner;
        private final Factory<PatternSet> patternSetFactory;
        private final FileSnapshotter snapshotter;
        private final Map<String, FileState> files;
        private final List<NormalizedSnapshotProducer> snapshots = Lists.newArrayList();

        FileCollectionVisitorImpl(Map<String, FileState> files, SnapshotNormalizationStrategy snapshotNormalizationStrategy, FileSystem fileSystem, FileSnapshotter snapshotter, StringInterner stringInterner, Factory<PatternSet> patternSetFactory) {
            this.files = files;
            this.snapshotNormalizationStrategy = snapshotNormalizationStrategy;
            this.fileSystem = fileSystem;
            this.snapshotter = snapshotter;
            this.stringInterner = stringInterner;
            this.patternSetFactory = patternSetFactory;
        }

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            for (File file : fileCollection) {
                fileSnapshotCount.incrementAndGet();
                FileState state = reuseStates ? files.get(file.getAbsolutePath()) : null;
                if (state == null) {
                    state = calculateFileDetails(file);
                    if (state.snapshot != null) {
                        files.put(state.path, state);
                    }
                } else {
                    fileReuseCount.incrementAndGet();
                }
                switch (state.type) {
                    case File:
                        addFileSnapshot(state);
                        break;
                    case Directory:
                        // Visit the directory itself, then its contents
                        addFileSnapshot(state);
                        visitDirectoryTree(new DirectoryFileTree(file, patternSetFactory.create()));
                        break;
                    case Missing:
                        snapshots.add(new MissingFileSnapshotProducer(state, snapshotNormalizationStrategy, stringInterner));
                        break;
                    default:
                        throw new IllegalArgumentException();
                }
            }
        }

        private FileState calculateFileDetails(File file) {
            String path = stringInterner.intern(file.getAbsolutePath());
            if (file.isFile()) {
                return new FileState(path, FileType.File, new SingletonFileTree.SingletonFileVisitDetails(file, fileSystem, false), null);
            }
            if (file.isDirectory()) {
                return new FileState(path, FileType.Directory, new SingletonFileTree.SingletonFileVisitDetails(file, fileSystem, true), DirSnapshot.getInstance());
            }
            return new FileState(path, FileType.Missing, new MissingFileVisitDetails(file), MissingFileSnapshot.getInstance());
        }

        @Override
        public void visitTree(FileTreeInternal fileTree) {
            treeSnapshotCount.incrementAndGet();
            fileTree.visitTreeOrBackingFile(this);
        }

        @Override
        public void visitDirectoryTree(DirectoryFileTree directoryFileTree) {
            treeSnapshotCount.incrementAndGet();
            directoryFileTree.visit(this);
        }

        @Override
        public void visitDir(FileVisitDetails dirDetails) {
            addFileSnapshot(new FileState(stringInterner.intern(dirDetails.getFile().getAbsolutePath()), FileType.Directory, dirDetails, DirSnapshot.getInstance()));
        }

        @Override
        public void visitFile(FileVisitDetails fileDetails) {
            addFileSnapshot(new FileState(stringInterner.intern(fileDetails.getFile().getAbsolutePath()), FileType.File, fileDetails, null));
        }

        private void addFileSnapshot(FileState fileState) {
            snapshots.add(new FileSnapshotProducer(fileState, files, snapshotNormalizationStrategy, snapshotter, stringInterner));
        }
    }
}
