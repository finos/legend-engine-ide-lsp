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

package org.finos.legend.engine.ide.lsp.extension.text;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestTextLocation
{
    @Test
    public void testValidation()
    {
        NullPointerException e1 = Assertions.assertThrows(NullPointerException.class, () -> TextLocation.newTextSource(null, TextInterval.newInterval(0, 0, 0, 1)));
        Assertions.assertEquals("Source URI is required", e1.getMessage());

        NullPointerException e2 = Assertions.assertThrows(NullPointerException.class, () -> TextLocation.newTextSource("uri.pure", null));
        Assertions.assertEquals("Text interval is required", e2.getMessage());
    }

    @Test
    public void testSubsumes()
    {
        Assertions.assertTrue(TextLocation.newTextSource("uri.pure", TextInterval.newInterval(1, 10, 5, 17)).subsumes(
                TextLocation.newTextSource("uri.pure", 1, 10, 5, 17)));

        Assertions.assertFalse(TextLocation.newTextSource("uri.pure", TextInterval.newInterval(1, 10, 5, 17)).subsumes(
                TextLocation.newTextSource("other.pure", 1, 10, 5, 17)));
    }

    @Test
    void testCompare()
    {
        TextLocation start = TextLocation.newTextSource("hello.pure", TextInterval.newInterval(0, 0, 0, 1));
        TextLocation start2 = TextLocation.newTextSource("hello2.pure", TextInterval.newInterval(0, 0, 0, 1));
        TextLocation firstLine = TextLocation.newTextSource("hello.pure", TextInterval.newInterval(1, 0, 1, 1));
        TextLocation other = TextLocation.newTextSource("hello.pure", TextInterval.newInterval(1, 0, 2, 1));

        Assertions.assertEquals(0, start.compareTo(start));
        Assertions.assertEquals(4, start2.compareTo(start));
        Assertions.assertEquals(-4, start.compareTo(start2));
        Assertions.assertEquals(1, firstLine.compareTo(start));
        Assertions.assertEquals(-1, start.compareTo(firstLine));
        Assertions.assertEquals(1, other.compareTo(firstLine));
        Assertions.assertEquals(-1, firstLine.compareTo(other));
    }

    @Test
    public void testToString()
    {
        Assertions.assertEquals("TextSource{sourceUri='hello.pure', textInterval=0:0-75:0}", TextLocation.newTextSource("hello.pure", 0, 0, 75, 0).toString());
    }
}
