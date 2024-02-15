/*
 * Copyright 2024 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp.server;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URLClassLoader;
import java.util.Objects;
import org.finos.legend.engine.ide.lsp.classpath.ClasspathUsingMavenFactory;

public class LegendREPLTerminal
{
    public static void main(String... args) throws InterruptedException
    {
        String mavenPath = args[0];
        String pomPath = args[1];

//        todo - once we add mechanism to find pom from project, use the workspace folders
//        ArrayList<String> workspaceFolders = new ArrayList<>(Arrays.asList(args).subList(2, args.length));

        System.out.println("Initializing REPL for engine version on pom: " + pomPath);
        System.out.println("This can take a few minutes...");

        try
        {
            ClasspathUsingMavenFactory factory = new ClasspathUsingMavenFactory(new File(pomPath));
            URLClassLoader classloader = Objects.requireNonNull(
                    factory.createClassloader(mavenPath.isBlank() ? null : new File(mavenPath), new File(pomPath)),
                    "Failed to load classpath from pom");

            Thread.currentThread().setContextClassLoader(classloader);

            Class<?> h2 = classloader.loadClass("org.finos.legend.engine.plan.execution.stores.relational.AlloyH2Server");
            Method startServer = h2.getMethod("startServer", int.class);
            startServer.invoke(null, 1975);

            Class<?> replClient = classloader.loadClass("org.finos.legend.engine.repl.client.Client");
            Method main = replClient.getMethod("main", String[].class);
            main.invoke(null, new Object[] {new String[0]});
        }
        catch (Exception e)
        {
            System.out.println("An error has occurred and cannot start the REPL terminal:");
            e.printStackTrace(System.out);
            System.out.println("The terminal will close itself in 60 seconds");
            Thread.sleep(60_000);
            System.exit(1);
        }

        System.exit(0);
    }
}
