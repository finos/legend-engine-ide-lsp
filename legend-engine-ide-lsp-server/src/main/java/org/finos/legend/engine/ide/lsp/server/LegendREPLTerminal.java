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

import java.util.Optional;
import java.util.ServiceLoader;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;
import org.finos.legend.engine.ide.lsp.extension.features.LegendREPLFeature;

public class LegendREPLTerminal
{
    public static void main(String... args) throws InterruptedException
    {
        try
        {
            Optional<LegendREPLFeature> repl = ServiceLoader.load(LegendLSPFeature.class)
                    .stream()
                    .map(ServiceLoader.Provider::get)
                    .filter(LegendREPLFeature.class::isInstance)
                    .map(LegendREPLFeature.class::cast)
                    .findAny();

            if (repl.isPresent())
            {
                repl.get().startREPL();
            }
            else
            {
                throw new IllegalStateException("No REPL feature found on current configuration.  Verify classpath...");
            }
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
