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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.InvokerLogger;
import org.apache.maven.shared.invoker.PrintStreamHandler;
import org.apache.maven.shared.invoker.PrintStreamLogger;
import org.apache.maven.shared.utils.Os;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.eclipse.lsp4j.ConfigurationItem;
import org.eclipse.lsp4j.ConfigurationParams;
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

    private File getMavenExecLocation(String mavenHome)
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
        mavenExecPathConfig.setSection("maven.executable.path");

        ConfigurationItem defaultPomConfig = new ConfigurationItem();
        defaultPomConfig.setSection("legend.extensions.dependencies.pom");

        ConfigurationParams configurationParams = new ConfigurationParams(Arrays.asList(mavenExecPathConfig, defaultPomConfig));
        return this.server.getLanguageClient().configuration(configurationParams).thenApply(x ->
        {
            String mavenExecPath = this.server.extractValueAs(x.get(0), String.class);
            String overrideDefaultPom = this.server.extractValueAs(x.get(1), String.class);

            try
            {
                File maven = this.getMavenExecLocation(mavenExecPath);
                this.server.logInfoToClient("Maven path: " + maven);

                File pom = (overrideDefaultPom == null || overrideDefaultPom.isEmpty()) ? this.defaultPom : new File(overrideDefaultPom);

                // todo apply properties from /project.json is this exists...
                // todo if project.json exists, use pom from a sub-module
                // todo otherwise, check if pom exists on root
                // todo last, use a default pom...

                this.server.logInfoToClient("Dependencies loaded from POM: " + pom);
                LOGGER.info("Dependencies loaded from POM: {}", pom);

                File legendLspClasspath = File.createTempFile("legend_lsp_classpath", ".txt");
                legendLspClasspath.deleteOnExit();

                Properties properties = new Properties();
                properties.setProperty("mdep.outputFile", legendLspClasspath.getAbsolutePath());

                InvocationRequest request = new DefaultInvocationRequest();
                request.setPomFile(pom);
                request.setOutputHandler(new PrintStreamHandler(new PrintStream(this.outputStream, true), true));
                request.setGoals(Collections.singletonList("dependency:build-classpath"));
                request.setProperties(properties);
                request.setTimeoutInSeconds((int) TimeUnit.MINUTES.toSeconds(15));
                request.setJavaHome(Optional.ofNullable(System.getProperty("java.home")).map(File::new).orElse(null));
                request.setMavenHome(maven);
                request.setShowErrors(true);
                request.setShowVersion(true);
                request.setUpdateSnapshots(true);

                InvocationResult result = this.invoker.execute(request);
                if (result.getExitCode() != 0)
                {
                    String output = this.outputStream.toString(StandardCharsets.UTF_8);
                    this.server.logErrorToClient("Unable to initialize Legend extensions.  Maven output:\n\n" + output);
                    LOGGER.error("Unable to initialize Legend extensions.  Maven output:\n\n{}", output);
                    return null;
                }

                String classpath = Files.readString(legendLspClasspath.toPath(), StandardCharsets.UTF_8);

                LOGGER.info("Classpath used: " + classpath);

                String[] classpathEntries = classpath.split(";");
                URL[] urls = new URL[classpathEntries.length];

                for (int i = 0; i < urls.length; i++)
                {
                    urls[i] = new File(classpathEntries[i]).toURI().toURL();
                }

                ClassLoader parentClassloader = ClasspathUsingMavenFactory.class.getClassLoader();
                return new URLClassLoader("legend-lsp", urls, parentClassloader);
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
}
