/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.DomainObjectSet;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.DependencySet;
import org.gradle.api.artifacts.SelfResolvingDependency;
import org.gradle.api.internal.DelegatingDomainObjectSet;
import org.gradle.api.internal.artifacts.dependencies.ProjectDependencyInternal;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.tasks.TaskDependency;

public class DefaultDependencySet extends DelegatingDomainObjectSet<Dependency> implements DependencySet {
    private final TaskDependency builtBy = new DependencySetTaskDependency();
    private final String displayName;
    private final Configuration clientConfiguration;

    public DefaultDependencySet(String displayName, Configuration clientConfiguration, DomainObjectSet<Dependency> backingSet) {
        super(backingSet);
        this.displayName = displayName;
        this.clientConfiguration = clientConfiguration;
    }

    @Override
    public String toString() {
        return displayName;
    }

    public TaskDependency getBuildDependencies() {
        return builtBy;
    }

    private class DependencySetTaskDependency extends AbstractTaskDependency {
        @Override
        public String toString() {
            return "build dependencies " + DefaultDependencySet.this;
        }

        @Override
        public void visitDependencies(TaskDependencyResolveContext context) {
            for (SelfResolvingDependency dependency : DefaultDependencySet.this.withType(SelfResolvingDependency.class)) {
                if (dependency instanceof ProjectDependencyInternal) {
                    context.add(((ProjectDependencyInternal)dependency).getTaskDependency(clientConfiguration.getAttributes()));
                } else {
                    context.add(dependency);
                }
            }
        }
    }
}
