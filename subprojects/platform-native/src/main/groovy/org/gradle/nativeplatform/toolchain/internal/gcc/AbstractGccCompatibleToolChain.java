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
package org.gradle.nativeplatform.toolchain.internal.gcc;

import org.gradle.api.Action;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.internal.Actions;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.internal.reflect.Instantiator;
import org.gradle.nativeplatform.platform.Platform;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.toolchain.CommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.GccCommandLineToolConfiguration;
import org.gradle.nativeplatform.toolchain.PlatformConfigurableToolChain;
import org.gradle.nativeplatform.toolchain.PlatformToolChain;
import org.gradle.nativeplatform.toolchain.internal.*;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.UnavailablePlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.tools.*;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.*;

import static java.util.Arrays.asList;

/**
 * A tool chain that has GCC semantics, where all platform variants are produced by varying the tool args.
 */
public abstract class AbstractGccCompatibleToolChain extends ExtendableToolChain<GccCommandLineToolConfiguration> implements PlatformConfigurableToolChain {
    private final ExecActionFactory execActionFactory;
    private final ToolSearchPath toolSearchPath;
    private final List<TargetPlatformConfiguration> platformConfigs = new ArrayList<TargetPlatformConfiguration>();
    private final Instantiator instantiator;
    private int configInsertLocation;

    public AbstractGccCompatibleToolChain(String name, OperatingSystem operatingSystem, FileResolver fileResolver, ExecActionFactory execActionFactory, ToolSearchPath toolSearchPath,
                                          Instantiator instantiator) {
        super(name, operatingSystem, fileResolver);
        this.execActionFactory = execActionFactory;
        this.toolSearchPath = toolSearchPath;
        this.instantiator = instantiator;

        target(new ToolChainDefaultArchitecture());
        target(new Intel32Architecture());
        target(new Intel64Architecture());
        configInsertLocation = 0;
    }

    protected CommandLineToolSearchResult locate(GccCommandLineToolConfigurationInternal gccTool) {
        return toolSearchPath.locate(gccTool.getToolType(), gccTool.getExecutable());
    }

    public List<File> getPath() {
        return toolSearchPath.getPath();
    }

    public void path(Object... pathEntries) {
        for (Object path : pathEntries) {
            toolSearchPath.path(resolve(path));
        }
    }

    protected void initTools(PlatformToolChain<GccCommandLineToolConfiguration> platformToolChain, ToolChainAvailability availability) {
        Map<String, GccCommandLineToolConfiguration> allTools = platformToolChain.getAsMap();
        boolean found = false;
        for (Object o : allTools.values()) {
            GccCommandLineToolConfigurationInternal tool = (GccCommandLineToolConfigurationInternal) o;
            found |= toolSearchPath.locate(tool.getToolType(), tool.getExecutable()).isAvailable();
        }
        if (!found) {
            GccCommandLineToolConfigurationInternal cCompiler = (GccCommandLineToolConfigurationInternal) allTools.get("cCompiler");
            availability.mustBeAvailable(locate(cCompiler));
        }
    }

    public void target(String platformName) {
        target(platformName, Actions.<PlatformToolChain>doNothing());
    }

    public void target(String platformName, Action<? super PlatformToolChain<GccCommandLineToolConfiguration>> action) {
        target(new DefaultTargetPlatformConfiguration(asList(platformName), action));
    }

    public void target(List<String> platformNames, Action<? super PlatformToolChain<GccCommandLineToolConfiguration>> action) {
        target(new DefaultTargetPlatformConfiguration(platformNames, action));
    }

    void target(TargetPlatformConfiguration targetPlatformConfiguration) {
        platformConfigs.add(configInsertLocation, targetPlatformConfiguration);
        configInsertLocation++;
    }

    public PlatformToolProvider select(Platform targetPlatform) {
        TargetPlatformConfiguration targetPlatformConfigurationConfiguration = getPlatformConfiguration(targetPlatform);
        ToolChainAvailability result = new ToolChainAvailability();
        if (targetPlatformConfigurationConfiguration == null) {
            result.unavailable(String.format("Don't know how to build for platform '%s'.", targetPlatform.getName()));
            return new UnavailablePlatformToolProvider(result);
        }

        DefaultGccPlatformToolChain configurableToolChain = instantiator.newInstance(DefaultGccPlatformToolChain.class, targetPlatform, instantiator, getName(), getDisplayName());
        addDefaultTools(configurableToolChain);
        targetPlatformConfigurationConfiguration.apply(configurableToolChain);
        configureActions.execute(configurableToolChain);

        initTools(configurableToolChain, result);
        if (!result.isAvailable()) {
            return new UnavailablePlatformToolProvider(result);
        }

        ToolRegistry platformTools = new ConfiguredToolRegistry(configurableToolChain);
        String objectFileSuffix = targetPlatform.getOperatingSystem().isWindows() ? ".obj" : ".o";
        return new GccPlatformToolProvider(toolSearchPath, platformTools, execActionFactory, objectFileSuffix, canUseCommandFile());
    }

    protected abstract void addDefaultTools(DefaultGccPlatformToolChain toolChain);

    protected TargetPlatformConfiguration getPlatformConfiguration(Platform targetPlatform) {
        for (TargetPlatformConfiguration platformConfig : platformConfigs) {
            if (platformConfig.supportsPlatform(targetPlatform)) {
                return platformConfig;
            }
        }
        return null;
    }

    protected boolean canUseCommandFile() {
        return true;
    }

    private static class ToolChainDefaultArchitecture implements TargetPlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && targetPlatform.getArchitecture() == ArchitectureInternal.TOOL_CHAIN_DEFAULT;
        }

        public PlatformToolChain apply(PlatformToolChain platformToolChain) {
            return platformToolChain;
        }
    }

    private static class Intel32Architecture implements TargetPlatformConfiguration {

        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && ((ArchitectureInternal) targetPlatform.getArchitecture()).isI386();
        }

        public PlatformToolChain apply(PlatformToolChain platformToolChain) {
            Action<PlatformToolChain> action = new Action<PlatformToolChain>() {
                public void execute(PlatformToolChain configurableToolChain) {
                    Action<List<String>> m32args = new Action<List<String>>() {
                        public void execute(List<String> args) {
                            args.add("-m32");
                        }
                    };
                    configureTool(configurableToolChain, "cppCompiler", m32args);
                    configureTool(configurableToolChain, "cCompiler", m32args);
                    configureTool(configurableToolChain, "objcCompiler", m32args);
                    configureTool(configurableToolChain, "objcppCompiler", m32args);
                    configureTool(configurableToolChain, "linker", m32args);
                    configureTool(configurableToolChain, "assembler", new Action<List<String>>() {
                        public void execute(List<String> args) {
                            if (OperatingSystem.current().isMacOsX()) {
                                args.addAll(asList("-arch", "i386"));
                            } else {
                                args.add("--32");
                            }
                        }
                    });
                }
            };
            action.execute(platformToolChain);
            return platformToolChain;
        }
    }

    private static class Intel64Architecture implements TargetPlatformConfiguration {
        public boolean supportsPlatform(Platform targetPlatform) {
            return targetPlatform.getOperatingSystem().isCurrent()
                    && !OperatingSystem.current().isWindows() // Currently don't support building 64-bit binaries on GCC/Windows
                    && ((ArchitectureInternal) targetPlatform.getArchitecture()).isAmd64();
        }

        public PlatformToolChain apply(PlatformToolChain platformToolChain) {

            Action<PlatformToolChain> action = new Action<PlatformToolChain>() {
                public void execute(PlatformToolChain configurableToolChain) {
                    Action<List<String>> m64args = new Action<List<String>>() {
                        public void execute(List<String> args) {
                            args.add("-m64");
                        }
                    };
                    configureTool(configurableToolChain, "cppCompiler", m64args);
                    configureTool(configurableToolChain, "cCompiler", m64args);
                    configureTool(configurableToolChain, "objcCompiler", m64args);
                    configureTool(configurableToolChain, "objcppCompiler", m64args);
                    configureTool(configurableToolChain, "linker", m64args);
                    configureTool(configurableToolChain, "assembler", new Action<List<String>>() {
                        public void execute(List<String> args) {
                            if (OperatingSystem.current().isMacOsX()) {
                                args.addAll(asList("-arch", "x86_64"));
                            } else {
                                args.add("--64");
                            }
                        }
                    });
                }
            };
            action.execute(platformToolChain);
            return platformToolChain;
        }
    }

    private static void configureTool(PlatformToolChain<GccCommandLineToolConfiguration> toolChain, String tool, Action<List<String>> config) {
        CommandLineToolConfiguration cppCompiler = toolChain.getByName(tool);
        cppCompiler.withArguments(config);
    }

    private static class DefaultTargetPlatformConfiguration implements TargetPlatformConfiguration {

        //TODO this should be a container of platforms
        private final Collection<String> platformNames;
        private Action<? super PlatformToolChain> configurationAction;

        public DefaultTargetPlatformConfiguration(Collection<String> targetPlatformNames, Action<? super PlatformToolChain<GccCommandLineToolConfiguration>> configurationAction) {
            this.platformNames = targetPlatformNames;
            this.configurationAction = (Action)configurationAction;
        }

        public boolean supportsPlatform(Platform targetPlatform) {
            return platformNames.contains(targetPlatform.getName());
        }

        public PlatformToolChain apply(PlatformToolChain platformToolChain) {
            configurationAction.execute(platformToolChain);
            return platformToolChain;
        }
    }
}
