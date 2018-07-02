/*
 * Copyright 2016-2018 Red Hat, Inc. and/or its affiliates
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
package org.jboss.galleon.cli;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.stream.XMLStreamException;
import org.aesh.readline.AeshContext;
import org.jboss.galleon.ProvisioningException;
import org.jboss.galleon.ProvisioningManager;
import static org.jboss.galleon.cli.PmSession.getWorkDir;
import org.jboss.galleon.cli.config.Configuration;
import org.jboss.galleon.cli.config.mvn.MavenConfig;
import org.jboss.galleon.cli.config.mvn.MavenConfig.MavenChangeListener;
import org.jboss.galleon.config.ProvisioningConfig;
import org.jboss.galleon.universe.Channel;
import org.jboss.galleon.universe.Producer;
import org.jboss.galleon.universe.UniverseFactoryLoader;
import org.jboss.galleon.universe.UniverseResolver;
import org.jboss.galleon.universe.UniverseSpec;
import org.jboss.galleon.universe.maven.MavenUniverse;
import org.jboss.galleon.universe.maven.MavenUniverseFactory;
import org.jboss.galleon.util.PathsUtils;

/**
 *
 * @author jdenise@redhat.com
 */
public class UniverseManager implements MavenChangeListener {

    public static final String JBOSS_UNIVERSE_GROUP_ID = "org.jboss.universe";
    public static final String JBOSS_UNIVERSE_ARTIFACT_ID = "jboss-universe";

    private final ExecutorService executorService = Executors.newCachedThreadPool(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            Thread thr = new Thread(r, "Galleon CLI universe initializer");
            thr.setDaemon(true);
            return thr;
        }
    });
    private static final Logger LOGGER = Logger.getLogger(UniverseManager.class.getName());
    private MavenUniverse builtinUniverse;
    private final UniverseSpec builtinUniverseSpec;
    private final UniverseResolver universeResolver;
    private AeshContext aeshContext;
    private final PmSession pmSession;
    UniverseManager(PmSession pmSession, Configuration config, MavenArtifactRepositoryManager maven) throws ProvisioningException {
        this.pmSession = pmSession;
        config.getMavenConfig().addListener(this);
        UniverseFactoryLoader.getInstance().addArtifactResolver(maven);
        universeResolver = UniverseResolver.builder().addArtifactResolver(maven).build();
        builtinUniverseSpec = new UniverseSpec(MavenUniverseFactory.ID, JBOSS_UNIVERSE_GROUP_ID + ":" + JBOSS_UNIVERSE_ARTIFACT_ID);
    }

    /**
     * Universe resolution is done in a separate thread to not impact startup
     * time.
     */
    void resolveBuiltinUniverse() {
        executorService.submit(() -> {
            synchronized (this) {
                try {
                    builtinUniverse = (MavenUniverse) universeResolver.getUniverse(builtinUniverseSpec);
                    //speed-up future completion and execution by retrieving producers and channels
                    for (Producer<?> p : builtinUniverse.getProducers()) {
                        for (Channel c : p.getChannels()) {
                        }
                    }
                    LOGGER.log(Level.FINE, "Successfully resolved builtin universe.");
                } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Can't resolve builtin universe", ex);
                }
            }
        });
    }

    void close() {
        executorService.shutdownNow();
    }

    void setAeshContext(AeshContext aeshContext) {
        this.aeshContext = aeshContext;
    }

    public MavenUniverse getBuiltinUniverse() {
        synchronized (this) {
            return builtinUniverse;
        }
    }

    public UniverseSpec getBuiltinUniverseSpec() {
        return builtinUniverseSpec;
    }

    public UniverseResolver getUniverseResolver() {
        synchronized (this) {
            return universeResolver;
        }
    }

    private ProvisioningManager getProvisioningManager() throws ProvisioningException {
        Path workDir = getWorkDir(aeshContext);
        if (!Files.exists(PathsUtils.getProvisioningXml(workDir))) {
            throw new ProvisioningException("Local directory is not an installation directory");
        }
        ProvisioningManager mgr = ProvisioningManager.builder()
                .setInstallationHome(workDir)
                .build();
        return mgr;
    }

    public void addUniverse(String name, String factory, String location) throws ProvisioningException, IOException {
        if (pmSession.getState() != null) {
            pmSession.getState().addUniverse(pmSession, name, factory, location);
            return;
        }
        Path workDir = getWorkDir(aeshContext);
        if (!Files.exists(PathsUtils.getProvisioningXml(workDir))) {
            throw new ProvisioningException("Local directory is not an installation directory");
        }
        ProvisioningManager mgr = getProvisioningManager();
        UniverseSpec u = new UniverseSpec(factory, location);
        if (name != null) {
            mgr.addUniverse(name, u);
        } else {
            mgr.setDefaultUniverse(u);
        }
    }

    public void removeUniverse(String name) throws ProvisioningException, IOException {
        if (pmSession.getState() != null) {
            pmSession.getState().removeUniverse(pmSession, name);
            return;
        }
        Path workDir = getWorkDir(aeshContext);
        if (!Files.exists(PathsUtils.getProvisioningXml(workDir))) {
            throw new ProvisioningException("Local directory is not an installation directory");
        }
        ProvisioningManager mgr = getProvisioningManager();
        // Remove default if name is null
        mgr.removeUniverse(name);
    }

    public Set<String> getUniverseNames() {
        if (pmSession.getState() != null) {
            return pmSession.getState().getConfig().getUniverseNamedSpecs().keySet();
        }
        try {
            ProvisioningManager mgr = getProvisioningManager();
            return mgr.getProvisioningConfig().getUniverseNamedSpecs().keySet();
        } catch (ProvisioningException ex) {
            return Collections.emptySet();
        }
    }

    public UniverseSpec getDefaultUniverseSpec() {
        UniverseSpec defaultUniverse = null;
        if (pmSession.getState() != null) {
            defaultUniverse = pmSession.getState().getConfig().getDefaultUniverse();
        } else {
            Path workDir = getWorkDir(aeshContext);
            if (!Files.exists(PathsUtils.getProvisioningXml(workDir))) {
                return builtinUniverseSpec;
            }
            try {
                ProvisioningManager mgr = getProvisioningManager();
                defaultUniverse = mgr.getProvisioningConfig().getDefaultUniverse();
            } catch (ProvisioningException ex) {
                // OK, not an installation
            }
        }
        return defaultUniverse == null ? builtinUniverseSpec : defaultUniverse;
    }

    public String getUniverseName(UniverseSpec u) {
        ProvisioningConfig config = null;
        if (pmSession.getState() != null) {
            config = pmSession.getState().getConfig();
        } else {
            try {
                config = getProvisioningManager().getProvisioningConfig();
            } catch (ProvisioningException ex) {
                return null;
            }
        }
        for (Map.Entry<String, UniverseSpec> entry : config.getUniverseNamedSpecs().entrySet()) {
            if (entry.getValue().equals(u)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public UniverseSpec getUniverseSpec(String name) {
        ProvisioningConfig config = null;
        if (pmSession.getState() != null) {
            config = pmSession.getState().getConfig();
        } else {
            try {
                config = getProvisioningManager().getProvisioningConfig();
            } catch (ProvisioningException ex) {
                return null;
            }
        }
        return config.getUniverseNamedSpecs().get(name);
    }

    @Override
    public void configurationChanged(MavenConfig config) throws XMLStreamException, IOException {
        resolveBuiltinUniverse();
    }
}