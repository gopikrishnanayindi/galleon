/*
 * Copyright 2016-2019 Red Hat, Inc. and/or its affiliates
 * and other contributors as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.galleon.installation.fpversions;

import org.jboss.galleon.universe.galleon1.LegacyGalleon1Universe;
import org.jboss.galleon.universe.FeaturePackLocation.ProducerSpec;
import org.jboss.galleon.universe.FeaturePackLocation.FPID;
import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.spec.PackageDependencySpec;
import org.jboss.galleon.test.PmProvisionConfigTestBase;

/**
 *
 * @author Alexey Loubyansky
 */
public class MissingFpVersionTestCase extends PmProvisionConfigTestBase {

    private static final FPID FP1_100_GAV = LegacyGalleon1Universe.newFPID("org.jboss.pm.test:fp1", "1", "1.0.0.Final");
    private static final ProducerSpec FP1_GA = LegacyGalleon1Universe.newProducer("org.jboss.pm.test:fp1");
    private static final FPID FP2_200_GAV = LegacyGalleon1Universe.newFPID("org.jboss.pm.test:fp2", "2", "2.0.0.Final");

    @Override
    protected void createFeaturePacks(FeaturePackCreator creator) throws ProvisioningException {
        creator
            .newFeaturePack(FP1_100_GAV)
                .newPackage("p1", true)
                    .addDependency(PackageDependencySpec.optional("p2"))
                    .writeContent("fp1/p1.txt", "fp1 1.0.0.Final p1")
                    .getFeaturePack()
                .newPackage("p2")
                    .writeContent("fp1/p2.txt", "fp1 1.0.0.Final p2")
                    .getFeaturePack()
                .newPackage("p3")
                    .writeContent("fp1/p3.txt", "fp1 1.0.0.Final p3")
                    .getFeaturePack()
                .getCreator()
            .newFeaturePack(FP2_200_GAV)
                .newPackage("p1", true)
                    .writeContent("fp2/p1.txt", "fp2 p1")
                    .getFeaturePack()
                .getCreator()
            .install();
    }

    @Override
    protected ProvisioningConfig provisioningConfig()
            throws ProvisioningDescriptionException {
        return ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackConfig.forLocation(FP2_200_GAV.getLocation()))
                .addFeaturePackDep(FeaturePackConfig.forLocation(FP1_GA.getLocation()))
                .build();
    }

    @Override
    protected String[] pmErrors() throws ProvisioningException {
        return new String[] {
            "No version is available for " + FP1_GA
        };
    }
}
