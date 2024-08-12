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

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.finos.legend.engine.plan.execution.result.Result;
import org.finos.legend.engine.repl.client.Client;
import org.finos.legend.engine.repl.core.Command;
import org.finos.legend.engine.repl.core.ReplExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class LSPReplExtension implements ReplExtension
{
    private final WatchService watcher;
    private final HashMap<Path, LegendFile> legendFileCache = new HashMap<>();

    public LSPReplExtension()
    {
        try
        {
            watcher = FileSystems.getDefault().newWatchService();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        Executors.newSingleThreadExecutor().submit(this::processEvents);
    }

    @Override
    public void initialize(Client client)
    {
        processAllDirectories(Path.of(System.getProperty("user.dir")), this::registerDirectory);
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
        try
        {
            legendFileCache.put(filePath.toAbsolutePath(), new LegendFile(filePath, Files.getLastModifiedTime(filePath, LinkOption.NOFOLLOW_LINKS)));
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
                            legendFileCache.remove(resolvedPath);
                        }
                        else
                        {
                            String absolutePathString = resolvedPath.toAbsolutePath() + File.separator;
                            Set<Path> filesToDelete = legendFileCache.keySet().stream().filter(k -> k.toString().startsWith(absolutePathString)).collect(Collectors.toSet());
                            filesToDelete.forEach(legendFileCache::remove);
                        }
                        break;

                    case "ENTRY_MODIFY":
                        try
                        {
                            legendFileCache.get(resolvedPath.toAbsolutePath()).modifyFileContent(Files.getLastModifiedTime(resolvedPath, LinkOption.NOFOLLOW_LINKS));
                        }
                        catch (IOException e)
                        {
                            throw new RuntimeException(e);
                        }
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
        return Lists.mutable.fromStream(legendFileCache.values().stream().map(LegendFile::getFileContent));
    }
}
