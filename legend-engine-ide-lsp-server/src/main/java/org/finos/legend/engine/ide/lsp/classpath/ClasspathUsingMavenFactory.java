// Copyright 2024 Goldman Sachs
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.finos.legend.engine.ide.lsp.classpath;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Exclusion;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.invoker.PrintStreamLogger;
import org.apache.maven.shared.utils.Os;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
import org.finos.legend.engine.ide.lsp.Constants;
import org.finos.legend.engine.ide.lsp.server.LegendLanguageServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClasspathUsingMavenFactory implements ClasspathFactory
{
    private static final Logger LOGGER = LoggerFactory.getLogger(ClasspathUsingMavenFactory.class);
    private final Invoker invoker;
    private final File defaultPom;
    private final ByteArrayOutputStream outputStream;
    private LegendLanguageServer server;

    public ClasspathUsingMavenFactory(File defaultPom)
    {
        this.defaultPom = defaultPom;
        this.invoker = new DefaultInvoker();
        this.outputStream = new ByteArrayOutputStream();
        this.invoker.setLogger(new PrintStreamLogger(new PrintStream(this.outputStream, true), InvokerLogger.INFO));
    }

    public File getMavenExecLocation(String mavenHome)
    {
        if (mavenHome == null || mavenHome.isEmpty())
        {
            Commandline commandline = new Commandline();

            if (Os.isFamily(Os.FAMILY_WINDOWS))
            {
                commandline.setExecutable("where");
                commandline.addArguments("mvn");
            }
            else if (Os.isFamily(Os.FAMILY_UNIX) || Os.isFamily(Os.FAMILY_MAC))
            {
                commandline.setExecutable("which");
                commandline.addArguments("mvn");
            }
            else
            {
                this.server.logErrorToClient("Cannot find maven executable on unsupported OS");
                return null;
            }

            CommandLineUtils.StringStreamConsumer systemOut = new CommandLineUtils.StringStreamConsumer();
            CommandLineUtils.StringStreamConsumer systemErr = new CommandLineUtils.StringStreamConsumer();

            try
            {
                int result = CommandLineUtils.executeCommandLine(commandline, systemOut, systemErr, 30);

                if (result == 0)
                {
                    String[] split = systemOut.getOutput().split(System.lineSeparator());
                    if (split.length == 0)
                    {
                        return null;
                    }
                    String location = split[0];
                    return new File(location);
                }
                else
                {
                    this.server.logErrorToClient("Error finding mvn executable: " + systemErr.getOutput());
                    return null;
                }
            }
            catch (CommandLineException e)
            {
                this.server.logErrorToClient("Error running command " + commandline + ".  Cannot find location for maven.  Error: " + systemErr.getOutput());
                return null;
            }
        }
        else
        {
            return new File(mavenHome);
        }
    }

    @Override
    public void initialize(LegendLanguageServer server)
    {
        this.server = server;
    }

    @Override
    public CompletableFuture<ClassLoader> create(Iterable<String> folders)
    {
        this.server.logInfoToClient("Discovering classpath using maven");

        ConfigurationItem mavenExecPathConfig = new ConfigurationItem();
        mavenExecPathConfig.setSection(Constants.MAVEN_EXEC_PATH_CONFIG_PATH);

        ConfigurationItem mavenSettingXml = new ConfigurationItem();
        mavenSettingXml.setSection(Constants.MAVEN_SETTINGS_FILE_CONFIG_PATH);

        ConfigurationItem defaultPomConfig = new ConfigurationItem();
        defaultPomConfig.setSection(Constants.LEGEND_EXTENSIONS_DEPENDENCIES_POM_CONFIG_PATH);

        ConfigurationItem extraDependenciesConfig = new ConfigurationItem();
        extraDependenciesConfig.setSection(Constants.LEGEND_EXTENSIONS_OTHER_DEPENDENCIES_CONFIG_PATH);

        ConfigurationParams configurationParams = new ConfigurationParams(Arrays.asList(
                mavenExecPathConfig,
                mavenSettingXml,
                defaultPomConfig,
                extraDependenciesConfig
        ));
        return this.server.getLanguageClient().configuration(configurationParams).thenApply(x ->
        {
            String mavenExecPath = this.server.extractValueAs(x.get(0), String.class);
            String settingXmlPath = this.server.extractValueAs(x.get(1), String.class);
            String overrideDefaultPom = this.server.extractValueAs(x.get(2), String.class);
            List<String> extraDependencies = (List<String>) this.server.extractValueAs(x.get(3), TypeToken.getParameterized(List.class, String.class));

            try
            {
                File maven = this.getMavenExecLocation(mavenExecPath);
                this.server.logInfoToClient("Maven path: " + maven);

                File pom = this.findDependencyPom(folders, overrideDefaultPom);
                File settingsXmlFile = settingXmlPath != null && !settingXmlPath.isEmpty() ? new File(settingXmlPath) : null;

                this.server.logInfoToClient("Dependencies loaded from POM: " + pom);
                LOGGER.info("Dependencies loaded from POM: {}", pom);

                return this.createClassloader(maven, pom, settingsXmlFile, extraDependencies);
            }
            catch (Exception e)
            {
                LOGGER.error("Unable to initialize Legend extensions", e);
                this.server.logErrorToClient("Unable to initialize Legend extensions - " + e.getMessage());
                return null;
            }
            finally
            {
                this.outputStream.reset();
            }
        });
    }

    private File findDependencyPom(Iterable<String> folders, String overrideDefaultPom) throws IOException
    {
        File pom = null;

        if (overrideDefaultPom == null || overrideDefaultPom.isEmpty())
        {
            pom = this.maybeUseStudioProjectEntitiesPom(folders);
        }

        if (pom == null)
        {
            pom = this.defaultPom;
        }

        return pom;
    }

    private File maybeUseStudioProjectEntitiesPom(Iterable<String> folders)
    {
        Set<Path> entitiesPoms = new HashSet<>();

        for (String folder : folders)
        {
            Path folderPath = Path.of(URI.create(folder));
            Path projectJson = folderPath.resolve("project.json");
            if (Files.isReadable(projectJson))
            {
                server.logInfoToClient("Found project json file, analyzing it: " + projectJson);
                try
                {
                    String json = Files.readString(projectJson, StandardCharsets.UTF_8);
                    Gson gson = new Gson();
                    Map<String, Object> projectMap = (Map<String, Object>) gson.fromJson(json, TypeToken.getParameterized(Map.class, String.class, Object.class));
                    String artifactId = (String) projectMap.get("artifactId");

                    Path entitiesPom = folderPath.resolve(artifactId + "-entities").resolve("pom.xml");

                    if (Files.isReadable(entitiesPom))
                    {
                        entitiesPoms.add(entitiesPom);
                    }
                }
                catch (Exception e)
                {
                    server.logErrorToClient("Unable to analyze project json file: " + e.getMessage());
                }
            }
        }

        if (entitiesPoms.size() == 1)
        {
            return entitiesPoms.iterator().next().toFile();
        }
        else if (entitiesPoms.size() != 0)
        {
            server.logErrorToClient("Cannot infer pom as found multiple 'entities' pom. Please define pom explicitly: " + entitiesPoms);
        }

        return null;
    }

    private Dependency getDefaultExtensionsDependency()
    {
        Dependency extensions = new Dependency();
        extensions.setGroupId("org.finos.legend.engine.ide.lsp");
        extensions.setArtifactId("legend-engine-ide-lsp-default-extensions");
        extensions.setVersion(this.server.getProjectVersion());
        Exclusion exclusion = new Exclusion();
        exclusion.setGroupId("*");
        exclusion.setArtifactId("*");
        extensions.addExclusion(exclusion);
        return extensions;
    }

    private List<Dependency> getExtraEngineDependencies(String engineVersion)
    {
        List<Dependency> dependencies = new ArrayList<>();

        Dependency replDependency = new Dependency();
        replDependency.setGroupId("org.finos.legend.engine");
        replDependency.setArtifactId("legend-engine-repl-relational");
        replDependency.setVersion(engineVersion);

        dependencies.add(replDependency);

        if (Os.isFamily(Os.FAMILY_WINDOWS))
        {
            Dependency replWindowsDependency = new Dependency();
            replWindowsDependency.setGroupId("org.finos.legend.engine");
            replWindowsDependency.setArtifactId("legend-engine-repl-client-windows");
            replWindowsDependency.setType("pom");
            replWindowsDependency.setVersion(engineVersion);

            dependencies.add(replWindowsDependency);
        }

        Dependency funcTestableDependency = new Dependency();
        funcTestableDependency.setGroupId("org.finos.legend.engine");
        funcTestableDependency.setArtifactId("legend-engine-configuration-plan-generation-serialization");
        funcTestableDependency.setVersion(engineVersion);

        dependencies.add(funcTestableDependency);

        return dependencies;
    }

    private URLClassLoader createClassloader(File maven, File pom, File settingXml, List<String> extraDependencies) throws Exception
    {
        CompletableFuture<Stream<URL>> coreClasspathFuture = this.server.supplyPossiblyAsync(() -> this.getClasspathURLEntries("core", maven, pom, settingXml));
        CompletableFuture<Stream<URL>> extraClasspathFuture = this.server.supplyPossiblyAsync(() ->
        {
            File extraDependenciesPom = null;
            try
            {
                extraDependenciesPom = this.computeExtraDependenciesPom(maven, pom, settingXml, extraDependencies);
                return this.getClasspathURLEntries("lsp-extra", maven, extraDependenciesPom, settingXml);
            }
            catch (IOException | MavenInvocationException e)
            {
                throw new RuntimeException(e);
            }
            finally
            {
                if (extraDependenciesPom != null)
                {
                    extraDependenciesPom.delete();
                }
            }
        });


        try
        {
            URL[] urls = coreClasspathFuture.thenCombine(extraClasspathFuture, Stream::concat).get().toArray(URL[]::new);
            ClassLoader parentClassloader = ClasspathUsingMavenFactory.class.getClassLoader();
            return new URLClassLoader("legend-lsp", urls, parentClassloader);
        }
        catch (Exception e)
        {
            this.server.logErrorToClient("Unable to construct extensions classpath: " + e.getMessage());
            LOGGER.error("Unable to construct extensions classpath", e);
            return null;
        }
    }

    private File computeExtraDependenciesPom(File maven, File pom, File settingXml, List<String> extraDependencies) throws IOException, MavenInvocationException
    {
        File legendLspExtraElementsPom = File.createTempFile("pom", ".xml");
        legendLspExtraElementsPom.deleteOnExit();

        try (OutputStream pomOs = Files.newOutputStream(legendLspExtraElementsPom.toPath()))
        {
            String engineVersion = this.findEngineVersion(maven, pom, settingXml);

            Model extraDependenciesModel = new Model();
            extraDependenciesModel.setModelVersion("4.0.0");
            extraDependenciesModel.setGroupId("org.finos.legend.ide.lsp");
            extraDependenciesModel.setArtifactId("default-pom");
            extraDependenciesModel.setVersion("0.0.1-SNAPSHOT");

            if (engineVersion != null)
            {
                this.getExtraEngineDependencies(engineVersion).forEach(extraDependenciesModel::addDependency);
            }
            extraDependenciesModel.addDependency(this.getDefaultExtensionsDependency());


            if (extraDependencies != null)
            {
                extraDependencies.stream()
                        .map(String::trim)
                        .map(d ->
                        {
                            String[] gav = d.split(":");
                            if (gav.length != 3)
                            {
                                throw new RuntimeException(
                                        String.format("Dependency '%s' provided thru '%s' not on correct format, group:artifact:version",
                                                d, Constants.LEGEND_EXTENSIONS_OTHER_DEPENDENCIES_CONFIG_PATH)
                                );
                            }

                            Dependency dependency = new Dependency();
                            dependency.setGroupId(gav[0]);
                            dependency.setArtifactId(gav[1]);
                            dependency.setVersion(gav[2]);

                            return dependency;
                        })
                        .forEach(extraDependenciesModel::addDependency);
            }

            new MavenXpp3Writer().write(pomOs, extraDependenciesModel);
        }

        return legendLspExtraElementsPom;
    }

    private String findEngineVersion(File maven, File pom, File settingXml) throws IOException, MavenInvocationException
    {
        File legendEngineArtifacts = File.createTempFile("legend_engine_artifacts", ".txt");
        legendEngineArtifacts.deleteOnExit();

        try
        {
            Properties properties = new Properties();
            properties.setProperty("outputFile", legendEngineArtifacts.getAbsolutePath());
            properties.setProperty("includeGroupIds", "org.finos.legend.engine");
            String goal = "dependency:list";

            InvocationResult result = invokeMaven(maven, pom, settingXml, properties, goal);

            if (result.getExitCode() != 0)
            {
                String output = this.outputStream.toString(StandardCharsets.UTF_8);
                if (this.server != null)
                {
                    this.server.logErrorToClient("Unable to initialize Legend extensions.  Maven output:\n\n" + output);
                }
                LOGGER.error("Unable to initialize Legend extensions.  Maven output:\n\n{}", output);
                return null;
            }

            String artifacts = Files.readString(legendEngineArtifacts.toPath(), StandardCharsets.UTF_8);
            String[] artifactEntries = artifacts.split("\n");

            Set<String> engineVersions = Stream.of(artifactEntries)
                    .map(String::trim)
                    .map(x -> x.split(":"))
                    // groupId:artifactId:type:version:classifier
                    .filter(x -> x.length == 5)
                    .map(x -> x[3])
                    .collect(Collectors.toSet());

            if (engineVersions.size() == 1)
            {
                return engineVersions.iterator().next();
            }
            else
            {
                this.server.logErrorToClient("Multiple engine versions found, and cannot add REPL dependencies: " + engineVersions);
                return null;
            }
        }
        finally
        {
            legendEngineArtifacts.delete();
        }
    }

    private Stream<URL> getClasspathURLEntries(String id, File maven, File pom, File settingXml)
    {
        File legendLspClasspath = null;

        try
        {
            legendLspClasspath = File.createTempFile("legend_lsp_classpath", ".txt");
            legendLspClasspath.deleteOnExit();

            Properties properties = new Properties();
            properties.setProperty("mdep.outputFile", legendLspClasspath.getAbsolutePath());
            properties.setProperty("mdep.pathSeparator", ";");

            InvocationResult invocationResult = this.invokeMaven(maven, pom, settingXml, properties, "dependency:build-classpath");

            if (invocationResult.getExitCode() != 0)
            {
                String output = this.outputStream.toString(StandardCharsets.UTF_8);
                throw new RuntimeException("Unable to initialize Legend extensions.  Maven output:\n\n" + output);
            }

            String classpath = Files.readString(legendLspClasspath.toPath(), StandardCharsets.UTF_8);

            LOGGER.info("{} classpath URLs used:  {}", id, classpath);

            return Stream.of(classpath.split(";")).map(entry ->
            {
                try
                {
                    File file = new File(entry);
                    if (!file.canRead())
                    {
                        throw new RuntimeException("Cannot read classpath file: " + file);
                    }
                    return file.toURI().toURL();
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });
        }
        catch (IOException | MavenInvocationException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            if (legendLspClasspath != null)
            {
                legendLspClasspath.delete();
            }
        }
    }

    private InvocationResult invokeMaven(File maven, File pom, File settingXml, Properties properties, String goal) throws MavenInvocationException
    {
        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pom);
        request.setUserSettingsFile(settingXml);
        request.setOutputHandler(new PrintStreamHandler(new PrintStream(this.outputStream, true), true));
        request.setGoals(List.of(goal));
        request.setProperties(properties);
        request.setTimeoutInSeconds((int) TimeUnit.MINUTES.toSeconds(15));
        request.setJavaHome(Optional.ofNullable(System.getProperty("java.home")).map(File::new).orElse(null));
        request.setMavenHome(maven);
        request.setShowErrors(true);
        request.setShowVersion(true);
        request.setUpdateSnapshots(true);

        return this.invoker.execute(request);
    }
}
