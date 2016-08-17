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

import org.gradle.language.base.internal.model.VariantAxisCompatibilityFactory;
import org.gradle.language.base.internal.model.VariantsMetaData;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.platform.base.BinarySpec;

import java.util.Collection;
import java.util.List;

public class JvmVariantChooser implements VariantChooser {
    private final VariantsMatcher variantsMatcher;
    private final VariantsMetaData variantsMetaData;

    public JvmVariantChooser(List<VariantAxisCompatibilityFactory> factories, Class<? extends BinarySpec> binarySpecType, ModelSchemaStore schemaStore, VariantsMetaData variantsMetaData) {
        this.variantsMatcher = new VariantsMatcher(factories, binarySpecType, schemaStore);
        this.variantsMetaData = variantsMetaData;
    }

    @Override
    public Collection<? extends BinarySpec> chooseMatchingVariants(Collection<BinarySpec> allBinaries) {
        return variantsMatcher.filterBinaries(variantsMetaData, allBinaries);
    }
}
