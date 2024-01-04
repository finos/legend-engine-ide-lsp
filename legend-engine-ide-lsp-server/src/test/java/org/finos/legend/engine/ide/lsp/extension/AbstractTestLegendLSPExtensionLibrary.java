// Copyright 2023 Goldman Sachs
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

package org.finos.legend.engine.ide.lsp.extension;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;

@SuppressWarnings("unchecked")
abstract class AbstractTestLegendLSPExtensionLibrary<E extends LegendLSPExtension, L extends LegendLSPExtensionLibrary<E>>
{
    protected L library;

    @Test
    public void testEmpty()
    {
        this.library = newLibrary();
        Assertions.assertEquals(Set.of(), this.library.getExtensionNames());
        Assertions.assertEquals(Set.of(), Set.copyOf(this.library.getExtensions()));
    }

    @Test
    public void testNonEmpty()
    {
        E ext1 = newExtension("ext1");
        E ext2 = newExtension("ext2");
        E ext3 = newExtension("ext3");
        this.library = newLibrary(ext1, ext2, ext3);
        Assertions.assertEquals(Set.of("ext1", "ext2", "ext3"), this.library.getExtensionNames());
        Assertions.assertSame(ext1, this.library.getExtension("ext1"));
        Assertions.assertSame(ext2, this.library.getExtension("ext2"));
        Assertions.assertSame(ext3, this.library.getExtension("ext3"));
        Assertions.assertEquals(Set.of(ext1, ext2, ext3), Set.copyOf(this.library.getExtensions()));
    }

    @Test
    public void testLibraryCollectionsAreUnmodifiable()
    {
        E ext1 = newExtension("ext1");
        E ext2 = newExtension("ext2");
        E ext3 = newExtension("ext3");
        this.library = newLibrary(ext1, ext2, ext3);

        Set<String> names = this.library.getExtensionNames();
        Assertions.assertEquals(Set.of("ext1", "ext2", "ext3"), names);
        assertUnmodifiable(names, "ext1", Arrays.asList("ext1", "ext2", "ext3"), "ext4", Arrays.asList("ext4", "ext5", "ext6"));

        Collection<E> extensions = this.library.getExtensions();
        Assertions.assertEquals(Set.of(ext1, ext2, ext3), Set.copyOf(this.library.getExtensions()));
        assertUnmodifiable(extensions, ext1, Arrays.asList(ext1, ext2, ext3), newExtension("ext4"), Arrays.asList(newExtension("ext4"), newExtension("ext5")));
    }

    protected <T> void assertUnmodifiable(Collection<T> collection, T toRemove, Collection<? extends T> toRemoveAll, T toAdd, Collection<? extends T> toAddAll)
    {
        Assertions.assertThrows(RuntimeException.class, () -> collection.remove(toRemove));
        Assertions.assertThrows(RuntimeException.class, () -> collection.removeAll(toRemoveAll));
        Assertions.assertThrows(RuntimeException.class, () -> collection.add(toAdd));
        Assertions.assertThrows(RuntimeException.class, () -> collection.addAll(toAddAll));
    }

    @Test
    public void testNameConflict()
    {
        String name = "ext1";

        IllegalArgumentException e = Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> newLibrary(newExtension(name), newExtension("otherName"), newExtension(name), newExtension("otherOtherName")));
        Assertions.assertEquals("Multiple extensions named: \"" + name + "\"", e.getMessage());
    }

    protected L newLibrary(E... extensions)
    {
        LegendLSPExtensionLibrary.AbstractBuilder<E, L> builder = libraryBuilder();
        builder.addExtensions(extensions);
        return builder.build();
    }

    protected abstract LegendLSPExtensionLibrary.AbstractBuilder<E, L> libraryBuilder();

    protected abstract E newExtension(String name);
}
