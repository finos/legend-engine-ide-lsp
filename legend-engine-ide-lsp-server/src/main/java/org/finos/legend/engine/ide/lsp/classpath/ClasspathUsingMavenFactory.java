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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
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
import org.finos.legend.engine.ide.lsp.extension.Constants;
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

        ConfigurationItem sdlcServerConfig = new ConfigurationItem();
        sdlcServerConfig.setSection(Constants.LEGEND_SDLC_SERVER_CONFIG_PATH);

        ConfigurationParams configurationParams = new ConfigurationParams(Arrays.asList(
                mavenExecPathConfig,
                mavenSettingXml,
                defaultPomConfig,
                extraDependenciesConfig,
                sdlcServerConfig
        ));
        return this.server.getLanguageClient().configuration(configurationParams).thenApply(x ->
        {
            String mavenExecPath = this.server.extractValueAs(x.get(0), String.class);
            String settingXmlPath = this.server.extractValueAs(x.get(1), String.class);
            String overrideDefaultPom = this.server.extractValueAs(x.get(2), String.class);
            List<String> extraDependencies = (List<String>) this.server.extractValueAs(x.get(3), TypeToken.getParameterized(List.class, String.class));
            String sdlcServer = this.server.extractValueAs(x.get(4), String.class);

            try
            {
                List<SDLCPlatform> platformVersions = this.getPlatformVersions(sdlcServer);

                File maven = this.getMavenExecLocation(mavenExecPath);
                this.server.logInfoToClient("Maven path: " + maven);

                File settingsXmlFile = settingXmlPath != null && !settingXmlPath.isEmpty() ? new File(settingXmlPath) : null;

                File pom = this.findDependencyPom(folders, overrideDefaultPom, platformVersions);

                this.server.logInfoToClient("Dependencies loaded from POM: " + pom);
                LOGGER.info("Dependencies loaded from POM: {}", pom);

                return this.createClassloader(maven, pom, settingsXmlFile, extraDependencies, platformVersions);
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

    private void updatePlatformVersions(File pom, List<SDLCPlatform> platforms) throws IOException
    {
        if (!platforms.isEmpty())
        {
            this.server.logInfoToClient("Updating platform version on pom: " + pom);
            final String pomContent = Files.readString(pom.toPath(), StandardCharsets.UTF_8);
            String updatedPomContent = pomContent;

            for (SDLCPlatform platform : platforms)
            {
                String versionProperty = "platform." + platform.getName() + ".version";
                String toReplaceRegEx = String.format("<%s>[^<]*+</%s>", versionProperty.replace(".", "\\."), versionProperty.replace(".", "\\."));
                String replaceValue = String.format("<%s>%s</%s>", versionProperty, platform.getPlatformVersion(), versionProperty);
                updatedPomContent = updatedPomContent.replaceAll(toReplaceRegEx, replaceValue);
            }

            if (!pomContent.equals(updatedPomContent))
            {
                Files.writeString(pom.toPath(), updatedPomContent, StandardCharsets.UTF_8);
            }
        }
    }

    private List<SDLCPlatform> getPlatformVersions(String sdlcServer)
    {
        if (sdlcServer != null && !sdlcServer.isEmpty())
        {
            try
            {
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request = HttpRequest.newBuilder().GET().uri(URI.create(sdlcServer + "/server/platforms")).build();
                LOGGER.info("Requesting platform versions using: {}", request.toString());
                HttpResponse<String> httpResponse = client.send(request, x -> HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8));
                if (httpResponse.statusCode() / 100 == 2)
                {

                    Gson gson = new Gson();
                    TypeToken<List<SDLCPlatform>> platformsResponseType = new TypeToken<>()
                    {
                    };

                    LOGGER.info("Response from platform versions request: {}", httpResponse.body());
                    return gson.fromJson(httpResponse.body(), platformsResponseType);
                }
                else
                {
                    this.server.logErrorToClient("Unable to gather platform versions: " + request + "; Status code: " + httpResponse.statusCode() + "; response: " + httpResponse.body());
                }
            }
            catch (InterruptedException | IOException e)
            {
                throw new RuntimeException("Unable to source platform versions using URL: " + sdlcServer, e);
            }
        }

        return List.of();
    }

    private File findDependencyPom(Iterable<String> folders, String overrideDefaultPom, List<SDLCPlatform> platformVersions) throws IOException
    {
        File pom = null;

        if (overrideDefaultPom == null || overrideDefaultPom.isEmpty())
        {
            pom = this.maybeUseStudioProjectEntitiesPom(folders, platformVersions);
        }

        if (pom == null)
        {
            if (overrideDefaultPom == null || overrideDefaultPom.isEmpty())
            {
                pom = this.defaultPom;
            }
            else
            {
                pom = new File(overrideDefaultPom.trim());
            }

            this.updatePlatformVersions(pom, platformVersions);
        }

        return pom;
    }

    private File maybeUseStudioProjectEntitiesPom(Iterable<String> folders, List<SDLCPlatform> platformVersions) throws IOException
    {
        Set<Path> entitiesPoms = new HashSet<>();

        for (String folder : folders)
        {
            Path folderPath = Path.of(URI.create(folder));

            try (Stream<Path> pathWalkStream = Files.walk(folderPath))
            {
                pathWalkStream.filter(x -> x.getFileName().toString().equals("project.json"))
                        .forEach(projectJson ->
                        {
                            if (Files.isReadable(projectJson))
                            {
                                this.server.logInfoToClient("Found project json file, analyzing it: " + projectJson);
                                try
                                {
                                    String json = Files.readString(projectJson, StandardCharsets.UTF_8);
                                    Gson gson = new Gson();
                                    Map<String, Object> projectMap = (Map<String, Object>) gson.fromJson(json, TypeToken.getParameterized(Map.class, String.class, Object.class));
                                    String artifactId = (String) projectMap.get("artifactId");

                                    Path entitiesPom = projectJson.getParent().resolve(artifactId + "-entities").resolve("pom.xml");

                                    if (Files.isReadable(entitiesPom))
                                    {
                                        entitiesPoms.add(entitiesPom);
                                    }
                                }
                                catch (Exception e)
                                {
                                    LOGGER.error("Unable to analyze project json file ({})", projectJson, e);
                                    this.server.logErrorToClient("Unable to analyze project json file (" + projectJson + "): " + e.getMessage());
                                }
                            }
                        });
            }
        }

        if (entitiesPoms.size() == 1)
        {
            Path pom = entitiesPoms.iterator().next();
            Path parentPom = pom
                    // directory where pom is located
                    .getParent()
                    // parent directory
                    .getParent()
                    // parent pom
                    .resolve("pom.xml");

            if (Files.exists(parentPom))
            {
                this.updatePlatformVersions(parentPom.toFile(), platformVersions);
            }
            else
            {
                this.server.logErrorToClient("Parent pom does not exists for: " + pom + ".  Unable to update platform versions.");
            }

            this.server.logInfoToClient("Using project entities pom: " + pom);
            return pom.toFile();
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

        Dependency planGenSerializationDependency = new Dependency();
        planGenSerializationDependency.setGroupId("org.finos.legend.engine");
        planGenSerializationDependency.setArtifactId("legend-engine-configuration-plan-generation-serialization");
        planGenSerializationDependency.setVersion(engineVersion);

        dependencies.add(planGenSerializationDependency);

        return dependencies;
    }

    private URLClassLoader createClassloader(File maven, File pom, File settingXml, List<String> extraDependencies, List<SDLCPlatform> platformVersions)
    {
        Path pomPath = pom.toPath();
        Path tempPom = null;

        try
        {
            tempPom = this.addExtraDependenciesToPom(maven, pomPath, settingXml, extraDependencies, platformVersions);
            URL[] urls = this.getClasspathURLEntries("core", maven, tempPom.toFile(), settingXml).toArray(URL[]::new);
            ClassLoader parentClassloader = ClasspathUsingMavenFactory.class.getClassLoader();
            return new URLClassLoader("legend-lsp", urls, parentClassloader);
        }
        catch (Exception e)
        {
            this.server.logErrorToClient("Unable to construct extensions classpath: " + e.getMessage());
            LOGGER.error("Unable to construct extensions classpath", e);
            return null;
        }
        finally
        {
            if (tempPom != null)
            {
                try
                {
                    Files.delete(tempPom);
                }
                catch (IOException e)
                {
                    LOGGER.error("Failed to delete temp pom: {}", tempPom, e);
                    tempPom.toFile().deleteOnExit();
                }
            }
        }
    }

    private Path addExtraDependenciesToPom(File maven, Path pom, File settingXml, List<String> extraDependencies, List<SDLCPlatform> platformVersions) throws IOException, MavenInvocationException
    {
        Model model;
        try (InputStream is = Files.newInputStream(pom))
        {
            model = new MavenXpp3Reader().read(is);
        }
        catch (Exception e)
        {
            throw new IOException("Cannot read pom model", e);
        }

        Path tempPom = Files.createTempFile(pom.getParent(), "legend_extension_pom", ".xml");

        try (OutputStream pomOs = Files.newOutputStream(tempPom))
        {
            String engineVersion = this.findEngineVersion(maven, pom.toFile(), settingXml, platformVersions);

            if (engineVersion != null)
            {
                this.getExtraEngineDependencies(engineVersion).forEach(model::addDependency);
            }
            model.addDependency(this.getDefaultExtensionsDependency());

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
                        .forEach(model::addDependency);
            }

            new MavenXpp3Writer().write(pomOs, model);
        }

        return tempPom;
    }

    private String findEngineVersion(File maven, File pom, File settingXml, List<SDLCPlatform> platforms) throws IOException, MavenInvocationException
    {
        Optional<SDLCPlatform> enginePlatformVersion = platforms.stream().filter(x -> x.getName().equals("legend-engine")).findAny();
        if (enginePlatformVersion.isPresent())
        {
            return enginePlatformVersion.get().getPlatformVersion();
        }

        File legendEngineArtifacts = File.createTempFile("legend_engine_artifacts", ".txt");
        legendEngineArtifacts.deleteOnExit();

        try
        {
            Properties properties = new Properties();
            properties.setProperty("outputFile", legendEngineArtifacts.getAbsolutePath());
            properties.setProperty("includeGroupIds", "org.finos.legend.engine");
            properties.setProperty("includeArtifactIds", "legend-engine-protocol");
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
        try
        {
            String storageDir = System.getProperty("storagePath");
            File legendLspClasspath;
            File legendLspCachedPom;
            File legendLspCachedParentPom;

            String[] classpath = null;

            String currentContent = Files.readString(pom.toPath(), StandardCharsets.UTF_8);

            String currentParentContent;
            Path parentPom = pom
                    .toPath()
                    // directory where pom is located
                    .getParent()
                    // parent directory
                    .getParent()
                    // parent pom
                    .resolve("pom.xml");

            if (Files.exists(parentPom))
            {
                currentParentContent = Files.readString(parentPom, StandardCharsets.UTF_8);
            }
            else
            {
                currentParentContent = "";
            }

            if (storageDir == null)
            {
                legendLspClasspath = File.createTempFile("legend_lsp_classpath_", id + ".txt");
                legendLspClasspath.deleteOnExit();

                legendLspCachedPom = File.createTempFile("pom_", id + ".xml");
                legendLspCachedPom.deleteOnExit();

                legendLspCachedParentPom = File.createTempFile("parent_pom_", id + ".xml");
                legendLspCachedParentPom.deleteOnExit();
            }
            else
            {
                File cacheDir = new File(storageDir);
                legendLspClasspath = new File(cacheDir, "legend_lsp_classpath_" + id + ".txt");
                legendLspCachedPom = new File(cacheDir, "pom_" + id + ".xml");
                legendLspCachedParentPom = new File(cacheDir, "parent_pom_" + id + ".xml");

                if (legendLspClasspath.exists() && legendLspCachedPom.exists() && legendLspCachedParentPom.exists())
                {
                    this.server.logInfoToClient("Found cached " + id + " classpath, checking if still valid...");

                    String cachedContent = Files.readString(legendLspCachedPom.toPath(), StandardCharsets.UTF_8);
                    String cachedParentContent = Files.readString(legendLspCachedParentPom.toPath(), StandardCharsets.UTF_8);

                    if (currentContent.equals(cachedContent) && currentParentContent.equals(cachedParentContent))
                    {
                        classpath = Files.readString(legendLspClasspath.toPath(), StandardCharsets.UTF_8).split(";");

                        // can we still can read old files or do we depend on SNAPSHOTS... maybe jars where deleted
                        for (String entry : classpath)
                        {
                            try
                            {
                                File file = new File(entry);
                                if (!file.canRead() || (entry.contains("-SNAPSHOT") && !entry.contains("legend-engine-ide-lsp-default-extensions")))
                                {
                                    this.server.logInfoToClient("Need to reload " + id + " classpath.  Either an entry does not exist anymore or classpath contains -SNAPSHOT dependencies...");
                                    classpath = null;
                                    break;
                                }
                            }
                            catch (Exception e)
                            {
                                classpath = null;
                                break;
                            }
                        }
                    }
                    else
                    {
                        this.server.logInfoToClient("Cached for " + id + " classpath is stale...");
                    }
                }
            }

            if (classpath == null)
            {
                this.server.logInfoToClient("Resolving " + id + " classpath invoking maven...");

                Properties properties = new Properties();
                properties.setProperty("mdep.outputFile", legendLspClasspath.getAbsolutePath());
                properties.setProperty("mdep.pathSeparator", ";");

                InvocationResult invocationResult = this.invokeMaven(maven, pom, settingXml, properties, "dependency:build-classpath");

                if (invocationResult.getExitCode() != 0)
                {
                    String output = this.outputStream.toString(StandardCharsets.UTF_8);
                    throw new RuntimeException("Unable to initialize Legend extensions.  Maven output:\n\n" + output);
                }

                Files.writeString(legendLspCachedPom.toPath(), currentContent, StandardCharsets.UTF_8);
                Files.writeString(legendLspCachedParentPom.toPath(), currentParentContent, StandardCharsets.UTF_8);
                classpath = Files.readString(legendLspClasspath.toPath(), StandardCharsets.UTF_8).split(";");
            }
            else
            {
                this.server.logInfoToClient("Reusing cached " + id + " classpath rather that invoking maven");
            }

            LOGGER.info("{} classpath URLs used:  {}", id, classpath);

            return Stream.of(classpath).map(entry ->
            {
                try
                {
                    return new File(entry).toURI().toURL();
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
        request.setLocalRepositoryDirectory(Optional.ofNullable(System.getProperty("maven.repo.local")).filter(x -> !x.isEmpty()).map(File::new).orElse(null));
        request.setMavenHome(maven);
        request.setShowErrors(true);
        request.setShowVersion(true);
        request.setUpdateSnapshots(true);

        return this.invoker.execute(request);
    }
}
