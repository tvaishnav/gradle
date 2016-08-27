/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.internal.component.local.model;

import com.google.common.base.Objects;
import org.gradle.api.Nullable;
import org.gradle.api.Project;
import org.gradle.api.artifacts.component.BuildIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentIdentifier;
import org.gradle.api.artifacts.component.ProjectComponentSelector;

public class DefaultProjectComponentIdentifier implements ProjectComponentIdentifier {
    private final BuildIdentifier buildIdentifier;
    private final String projectPath;
    private final String displayName;

    private DefaultProjectComponentIdentifier(String projectPath) {
        this(null, projectPath);
    }

    private DefaultProjectComponentIdentifier(BuildIdentifier buildIdentifier, String projectPath) {
        this.buildIdentifier = buildIdentifier;
        assert projectPath != null : "project path cannot be null";
        this.projectPath = projectPath;
        displayName = "project " + fullPath(this);
    }

    public String getDisplayName() {
        return displayName;
    }

    @Nullable
    @Override
    public BuildIdentifier getBuild() {
        return buildIdentifier;
    }

    public String getProjectPath() {
        return projectPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultProjectComponentIdentifier that = (DefaultProjectComponentIdentifier) o;
        return Objects.equal(projectPath, that.projectPath)
            && Objects.equal(buildIdentifier, that.buildIdentifier);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(projectPath, buildIdentifier);
    }

    @Override
    public String toString() {
        return displayName;
    }

    public static String fullPath(ProjectComponentIdentifier projectId) {
        return projectId.getBuild() == null ? projectId.getProjectPath() : projectId.getBuild().getName() + ":" + projectId.getProjectPath();
    }

    public static ProjectComponentIdentifier newProjectId(String projectPath) {
        return new DefaultProjectComponentIdentifier(projectPath);
    }

    public static ProjectComponentIdentifier newProjectId(String build, String projectPath) {
        if (build == null) {
            return newProjectId(projectPath);
        }
        BuildIdentifier buildIdentifier = new DefaultBuildIdentifier(build);
        return new DefaultProjectComponentIdentifier(buildIdentifier, projectPath);
    }

    public static ProjectComponentIdentifier newProjectId(ProjectComponentSelector selector) {
        return new DefaultProjectComponentIdentifier(selector.getBuild(), selector.getProjectPath());
    }

    public static ProjectComponentIdentifier newProjectId(Project project) {
        return new DefaultProjectComponentIdentifier(project.getPath());
    }

    public static ProjectComponentIdentifier rootId(ProjectComponentIdentifier projectComponentIdentifier) {
        return new DefaultProjectComponentIdentifier(projectComponentIdentifier.getBuild(), ":");
    }
}
