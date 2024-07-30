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

package org.finos.legend.engine.ide.lsp.extension.repl;

import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.impl.factory.Lists;
import org.eclipse.collections.impl.utility.ListIterate;
import org.finos.legend.engine.repl.client.Client;
import org.finos.legend.engine.repl.client.jline3.JLine3Parser;
import org.finos.legend.engine.repl.core.Command;
import org.jline.builtins.Completers;
import org.jline.reader.Candidate;
import org.jline.reader.LineReader;
import org.jline.reader.ParsedLine;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class SourceModelCommand implements Command
{
    private final Client client;
    private final Completers.FilesCompleter completer = new Completers.FilesCompleter(new File("/"));

    public SourceModelCommand(Client client)
    {
        this.client = client;
    }

    @Override
    public String documentation()
    {
        return "sourceModel <path>";
    }

    @Override
    public String description()
    {
        return "source model(s) from file path";
    }

    @Override
    public boolean process(String line) throws Exception
    {
        if (line.startsWith("sourceModel"))
        {
            String[] tokens = line.split(" ");
            if (tokens.length != 2)
            {
                throw new RuntimeException("Error, sourceModel should be used as 'sourceModel <path>'");
            }

            Path modelPath;
            try
            {
                modelPath = Path.of(tokens[1]);
            }
            catch (Exception e)
            {
                alertInvalidModelPath(e.getMessage());
                return true;
            }

            if (checkFileExists(modelPath))
            {
                try
                {
                    String pathString = modelPath.toString();
                    String modelText = "###Pure\n//Sourced from " +
                            pathString +
                            "\n" +
                            Files.readString(modelPath, StandardCharsets.UTF_8) +
                            "\n//End of models sourced from " +
                            pathString +
                            "\n";
                    this.client.getModelState().addElement(modelText);
                }
                catch (Exception e)
                {
                    alertInvalidModelPath(e.getMessage());
                }
            }

            return true;
        }
        return false;
    }

    private boolean checkFileExists(Path modelPath)
    {
        if (modelPath.toString().endsWith(".pure") && Files.isReadable(modelPath) && !Files.isDirectory(modelPath))
        {
            return true;
        }
        alertInvalidModelPath("");
        return false;
    }

    private void alertInvalidModelPath(String errorMessage)
    {
        this.client.getTerminal().writer().println("Unable to source model(s): Provide a valid file path to the model(s)\n" + errorMessage);
    }

    @Override
    public MutableList<Candidate> complete(String inScope, LineReader lineReader, ParsedLine parsedLine)
    {
        if (inScope.startsWith("sourceModel "))
        {
            MutableList<String> words = Lists.mutable.withAll(parsedLine.words()).drop(2);
            if (!words.contains(" "))
            {
                String compressed = words.makeString("");
                MutableList<Candidate> list = Lists.mutable.empty();
                completer.complete(lineReader, new JLine3Parser.MyParsedLine(new JLine3Parser.ParserResult(parsedLine.line(), Lists.mutable.with("sourceModel", " ", compressed))), list);
                MutableList<Candidate> ca = ListIterate.collect(list, c -> new Candidate(c.value(), c.value(), (String) null, (String) null, (String) null, (String) null, false, 0));
                list.clear();
                list.addAll(ca);
                return list;
            }
        }
        return null;
    }
}
