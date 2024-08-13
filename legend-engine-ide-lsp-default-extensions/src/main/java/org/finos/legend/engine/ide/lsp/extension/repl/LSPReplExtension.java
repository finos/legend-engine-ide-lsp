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

import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.repl.client.Client;
import org.finos.legend.engine.repl.core.Command;
import org.finos.legend.engine.repl.core.ReplExtension;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LSPReplExtension implements ReplExtension
{
    private final WatchService watcher;
    private final List<String> workspaceFolders;
    private final ConcurrentHashMap<Path, String> legendFileCache = new ConcurrentHashMap<>();

    public LSPReplExtension(List<String> workspaceFolders)
    {
        try
        {
            this.workspaceFolders = workspaceFolders;
            watcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Executors.newSingleThreadExecutor(new BasicThreadFactory.Builder().daemon(true).build()).submit(this::processEvents);
    }

    @Override
    public void initialize(Client client)
    {
        workspaceFolders.forEach(workspaceFolder -> processAllDirectories(Path.of(workspaceFolder), this::registerDirectory));
    }

    private void processAllDirectories(Path workingDirectory, Consumer<Path> consumer)
    {
        try (Stream<Path> pathStream = Files.walk(workingDirectory, FileVisitOption.FOLLOW_LINKS))
        {
            pathStream.filter(Files::isDirectory).forEach(consumer);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void processAllPureFiles(Path directory, Consumer<Path> consumer)
    {
        try (Stream<Path> pathStream = Files.find(directory, 1,
                (path, attr) -> attr.isRegularFile() && path.getFileName().toString().endsWith(".pure"),
                FileVisitOption.FOLLOW_LINKS))
        {
            pathStream.forEach(consumer);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void registerDirectory(Path directory)
    {
        try
        {
            directory.register(watcher,
                    StandardWatchEventKinds.ENTRY_CREATE,
                    StandardWatchEventKinds.ENTRY_DELETE,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            processAllPureFiles(directory, this::cacheLegendFile);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private void cacheLegendFile(Path filePath)
    {
        legendFileCache.put(filePath.toAbsolutePath(), readFileContent(filePath));
    }

    private void deleteLegendFile(Path filePath)
    {
        legendFileCache.remove(filePath.toAbsolutePath());
    }

    private String readFileContent(Path filePath)
    {
        String pathString = filePath.toString();
        try
        {
            return "\n//Start of models sourced from " +
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

    private void processEvents()
    {
        while (true)
        {
            WatchKey watchKey;
            Path keyPath;
            try
            {
                watchKey = watcher.take();
                keyPath = (Path) watchKey.watchable();
            }
            catch (InterruptedException e)
            {
                throw new RuntimeException(e);
            }

            for (WatchEvent<?> event : watchKey.pollEvents())
            {
                Path resolvedPath = keyPath.resolve((Path) event.context());
                switch (event.kind().name())
                {
                    case "ENTRY_CREATE":
                        if (Files.isDirectory(resolvedPath))
                        {
                            processAllDirectories(resolvedPath, this::registerDirectory);
                        }
                        else if (Files.isReadable(resolvedPath) && resolvedPath.toString().endsWith(".pure"))
                        {
                            cacheLegendFile(resolvedPath);
                        }
                        break;

                    case "ENTRY_DELETE":
                        if (resolvedPath.toString().endsWith(".pure"))
                        {
                            deleteLegendFile(resolvedPath);
                        }
                        else
                        {
                            String absolutePathString = resolvedPath.toAbsolutePath() + File.separator;
                            Set<Path> filesToDelete = legendFileCache.keySet()
                                    .stream()
                                    .filter(k -> k.toString().startsWith(absolutePathString))
                                    .collect(Collectors.toSet());
                            filesToDelete.forEach(this::deleteLegendFile);
                        }
                        break;

                    case "ENTRY_MODIFY":
                        cacheLegendFile(resolvedPath);
                        break;

                    default:
                        throw new UnsupportedOperationException();
                }
            }

            watchKey.reset();
        }
    }

    @Override
    public MutableList<Command> getExtraCommands()
    {
        return Lists.mutable.empty();
    }

    @Override
    public boolean supports(Result result)
    {
        return false;
    }

    @Override
    public String print(Result result)
    {
        return null;
    }

    @Override
    public MutableList<String> generateDynamicContent(String code)
    {
        return Lists.mutable.ofAll(legendFileCache.values());
    }
}
