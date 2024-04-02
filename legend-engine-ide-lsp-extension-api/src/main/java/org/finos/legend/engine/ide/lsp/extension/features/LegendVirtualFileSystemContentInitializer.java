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

package org.finos.legend.engine.ide.lsp.extension.features;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import org.finos.legend.engine.ide.lsp.extension.LegendLSPFeature;

/**
 * Legend LSP feature that loads content that will be accessed through the virtual file system.
 * The main usage of this is for project dependencies, where we source the pure elements from the classpath
 */
public interface LegendVirtualFileSystemContentInitializer extends LegendLSPFeature
{
    /**
     * Get the pure grammar from the dependencies
     * @return pure grammar from dependencies, index by element name
     */
    List<LegendVirtualFile> getVirtualFilePureGrammars();

    static LegendVirtualFile newVirtualFile(Path path, String content)
    {
        return new LegendVirtualFile(path, content);
    }

    class LegendVirtualFile
    {
        private final Path path;
        private final String content;

        private LegendVirtualFile(Path path, String content)
        {
            this.path = Objects.requireNonNull(path, "path is required");
            this.content = Objects.requireNonNull(content, "content is required");
        }

        public Path getPath()
        {
            return path;
        }

        public String getContent()
        {
            return content;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o)
            {
                return true;
            }
            if (!(o instanceof LegendVirtualFile))
            {
                return false;
            }
            LegendVirtualFile that = (LegendVirtualFile) o;
            return this.path.equals(that.path) && this.content.equals(that.content);
        }

        @Override
        public int hashCode()
        {
            return Objects.hash(this.path, this.content);
        }

        @Override
        public String toString()
        {
            return "LegendVirtualFile{" +
                    "path=" + path +
                    ", content='" + content + '\'' +
                    '}';
        }
    }
}
