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
package org.jboss.galleon.plugin.options.test;

import java.util.HashMap;
import java.util.Map;

import org.jboss.galleon.ProvisioningDescriptionException;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import org.jboss.galleon.config.FeaturePackConfig;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.creator.FeaturePackCreator;
import org.jboss.galleon.ProvisioningOption;
import org.jboss.galleon.state.ProvisionedFeaturePack;
import org.jboss.galleon.state.ProvisionedState;
import org.jboss.galleon.test.util.fs.state.DirState;
import org.jboss.galleon.universe.FeaturePackLocation;
import org.jboss.galleon.universe.MvnUniverse;

/**
 *
 * @author Alexey Loubyansky
 */
public class InstalIntoConfiglWithPersistentPluginOptionsTestCase extends PluginOptionsTestBase {

    private static final String[] valueSet = new String[] {
            "v1", "v2", "v3", "v4", "prod3"
    };

    public static class Plugin1 extends PluginBase {
        protected Map<String, ProvisioningOption> initOptions() {
            final Map<String, ProvisioningOption> options = new HashMap<>();
            addOption(options, ProvisioningOption.builder("p1o1").addToValueSet(valueSet).build());
            addOption(options, ProvisioningOption.builder("p1o2").addToValueSet(valueSet).setPersistent(false).build());
            addOption(options, ProvisioningOption.builder("p1o3").setDefaultValue("false").build());
            addOption(options, ProvisioningOption.builder("p1o4").setDefaultValue("false").build());
            return options;
        }
    }

    public static class Plugin2 extends PluginBase {
        protected Map<String, ProvisioningOption> initOptions() {
            final Map<String, ProvisioningOption> options = new HashMap<>();
            addOption(options, ProvisioningOption.builder("p2o1").addToValueSet(valueSet).build());
            addOption(options, ProvisioningOption.builder("p2o2").addToValueSet(valueSet).setPersistent(false).build());
            addOption(options, ProvisioningOption.builder("p2o3").setDefaultValue("false").build());
            addOption(options, ProvisioningOption.builder("p2o4").setDefaultValue("false").build());
            return options;
        }
    }

    private FeaturePackLocation prod1;
    private FeaturePackLocation prod2;
    private FeaturePackLocation prod3;

    @Override
    protected void createProducers(MvnUniverse universe) throws ProvisioningException {
        universe.createProducer("prod1");
        universe.createProducer("prod2");
        universe.createProducer("prod3");
    }

    @Override
    protected void createFeaturePacks(FeaturePackCreator creator) throws ProvisioningException {
        prod1 = newFpl("prod1", "1", "1.0.0.Final");
        creator.newFeaturePack(prod1.getFPID())
            .setPluginFileName("plugin1.jar")
            .addPlugin(Plugin1.class);

        prod2 = newFpl("prod2", "1", "1.0.0.Final");
        creator.newFeaturePack(prod2.getFPID())
            .addDependency(prod1)
            .setPluginFileName("plugin2.jar")
            .addPlugin(Plugin2.class);

        prod3 = newFpl("prod3", "1", "1.0.0.Final");
        creator.newFeaturePack(prod3.getFPID());

        creator.install();
    }

    @Override
    protected void testPm(ProvisioningManager pm) throws ProvisioningException {
        final Map<String, String> options = new HashMap<>();
        options.put("p1o1", "v1");
        options.put("p1o2", "v2");
        options.put("p1o3", "true");
        options.put("p1o4", "false");
        options.put("p2o1", "v3");
        options.put("p2o2", "v4");
        options.put("p2o3", "true");
        options.put("p2o4", "false");
        pm.install(prod2, options);

        options.clear();
        options.put("p2o4", "true");
        options.put("p1o2", "prod3");
        pm.install(prod3, options);
    }

    @Override
    protected ProvisioningConfig provisionedConfig() throws ProvisioningDescriptionException {
        return ProvisioningConfig.builder()
                .addFeaturePackDep(FeaturePackConfig.builder(prod2).build())
                .addFeaturePackDep(FeaturePackConfig.builder(prod3).build())
                .addOption("p1o1", "v1")
                .addOption("p1o3", "true")
                .addOption("p1o4", "false")
                .addOption("p2o1", "v3")
                .addOption("p2o3", "true")
                .addOption("p2o4", "true")
                .build();
    }

    @Override
    protected ProvisionedState provisionedState() throws ProvisioningException {
        return ProvisionedState.builder()
                .addFeaturePack(ProvisionedFeaturePack.builder(prod1.getFPID()).build())
                .addFeaturePack(ProvisionedFeaturePack.builder(prod2.getFPID()).build())
                .addFeaturePack(ProvisionedFeaturePack.builder(prod3.getFPID()).build())
                .build();
    }

    @Override
    protected DirState provisionedHomeDir() {
        return newDirBuilder()
                .addFile("p1o1", "v1")
                .addFile("p1o2", "prod3")
                .addFile("p1o3", "true")
                .addFile("p1o4", "false")
                .addFile("p2o1", "v3")
                .addFile("p2o3", "true")
                .addFile("p2o4", "true")
                .build();
    }
}
