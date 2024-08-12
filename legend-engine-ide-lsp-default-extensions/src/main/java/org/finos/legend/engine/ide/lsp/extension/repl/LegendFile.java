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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Objects;

public class LegendFile
{
    private final Path filePath;
    private FileTime lastModifiedTime;
    private String fileContent;

    public LegendFile(Path filePath, FileTime lastModifiedTime)
    {
        this.filePath = validateLegendFile(filePath);
        this.lastModifiedTime = Objects.requireNonNull(lastModifiedTime, "lastModifiedTime is required");
        readFileContent();
    }

    private Path validateLegendFile(Path filePath)
    {
        if (!(filePath != null && Files.isReadable(filePath) && filePath.toString().endsWith(".pure")))
        {
            throw new RuntimeException("Valid filePath is required");
        }
        return filePath;
    }

    public void modifyFileContent(FileTime mostRecentModifiedTime)
    {
        if (lastModifiedTime.compareTo(mostRecentModifiedTime) < 0)
        {
            lastModifiedTime = mostRecentModifiedTime;
            readFileContent();
        }
    }

    private void readFileContent()
    {
        String pathString = filePath.toString();
        try
        {
            fileContent = "\n//Start of models sourced from " +
                    pathString +
                    "\n###Pure\n" +
                    Files.readString(filePath, StandardCharsets.UTF_8) +
                    "\n//End of models sourced from " +
                    pathString +
                    "\n";
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    public String getFileContent()
    {
        return fileContent;
    }
}
