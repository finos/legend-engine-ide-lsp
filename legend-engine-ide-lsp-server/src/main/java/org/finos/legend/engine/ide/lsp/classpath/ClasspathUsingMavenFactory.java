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
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.apache.commons.io.FileUtils;
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
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.eclipse.collections.impl.block.factory.Functions;
import org.eclipse.lsp4j.WorkspaceFolder;

public class ClasspathUsingMavenFactory implements ClasspathFactory
{
    private final Invoker invoker;
    private final File defaultPom;
    private final ByteArrayOutputStream outputStream;

    public ClasspathUsingMavenFactory(File defaultPom)
    {
        this.defaultPom = defaultPom;
        this.invoker = new DefaultInvoker();
        this.outputStream = new ByteArrayOutputStream();
        this.invoker.setLogger(new PrintStreamLogger(new PrintStream(this.outputStream, true), InvokerLogger.INFO));

        configureMvnExecIfPossible(this.invoker);
    }

    private static void configureMvnExecIfPossible(Invoker invoker)
    {
        if (System.getProperty("maven.home") == null)
        {
            Commandline commandline = new Commandline();

            if (Os.isFamily(Os.FAMILY_WINDOWS))
            {
                commandline.setExecutable("where");
                commandline.addArguments("mvn");
            }
            else if (Os.isFamily(Os.FAMILY_UNIX))
            {
                commandline.setExecutable("which");
                commandline.addArguments("mvn");
            }
            else
            {
                throw new UnsupportedOperationException("OS not supported");
            }

            CommandLineUtils.StringStreamConsumer systemOut = new CommandLineUtils.StringStreamConsumer();
            CommandLineUtils.StringStreamConsumer systemErr = new CommandLineUtils.StringStreamConsumer();
            int result;

            try
            {
                result = CommandLineUtils.executeCommandLine(commandline, systemOut, systemErr, 2);
            }
            catch (Exception e)
            {
                result = -1;
            }

            if (result == 0)
            {
                String location = systemOut.getOutput().split(System.lineSeparator())[0];
                invoker.setMavenExecutable(new File(location));
            }
        }
        else
        {
            invoker.setMavenHome(new File(System.getProperty("maven.home")));
        }
    }

    @Override
    public ClassLoader create(Iterable<? extends WorkspaceFolder> folders)
    {
        try
        {
            File pom = this.defaultPom;

            // todo apply properties from /project.json is this exists...
            // todo if project.json exists, use pom from a sub-module
            // todo otherwise, check if pom exists on root
            // todo last, use a default pom...

            File legendLspClasspath = File.createTempFile("legend_lsp_classpath", ".txt");
            legendLspClasspath.deleteOnExit();

            Properties properties = new Properties();
            properties.setProperty("mdep.outputFile", legendLspClasspath.getAbsolutePath());

            InvocationRequest request = new DefaultInvocationRequest();
            request.setPomFile(pom);
            request.setOutputHandler(new PrintStreamHandler(new PrintStream(this.outputStream, true), true));
            request.setGoals(Collections.singletonList("dependency:build-classpath"));
            request.setProperties(properties);
            request.setTimeoutInSeconds((int) TimeUnit.MINUTES.toSeconds(5));
            request.setJavaHome(Optional.ofNullable(System.getProperty("java.home")).map(File::new).orElse(null));

            InvocationResult result = this.invoker.execute(request);
            if (result.getExitCode() != 0)
            {
                String output = this.outputStream.toString(StandardCharsets.UTF_8);
                throw new IllegalStateException("Maven invoker failed\n\n" + output, result.getExecutionException());
            }

            String classpath = FileUtils.readFileToString(legendLspClasspath, StandardCharsets.UTF_8);
            String[] classpathEntries = classpath.split(";");
            URL[] urls = Stream.of(classpathEntries).map(Functions.throwing(x -> new File(x).toURI().toURL())).toArray(URL[]::new);

            ClassLoader parentClassloader = ClasspathUsingMavenFactory.class.getClassLoader();
            return new URLClassLoader("legend-lsp", urls, parentClassloader);
        }
        catch (IOException | MavenInvocationException e)
        {
            throw new RuntimeException(e);
        }
        finally
        {
            this.outputStream.reset();
        }
    }
}
