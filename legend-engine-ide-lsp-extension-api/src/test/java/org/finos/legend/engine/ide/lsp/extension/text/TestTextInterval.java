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

public class TestTextInterval
{
    @Test
    public void testValidation()
    {
        NullPointerException e1 = Assertions.assertThrows(NullPointerException.class, () -> TextInterval.newInterval(null, TextPosition.newPosition(1, 1)));
        Assertions.assertEquals("start is required", e1.getMessage());

        NullPointerException e2 = Assertions.assertThrows(NullPointerException.class, () -> TextInterval.newInterval(TextPosition.newPosition(1, 2), null));
        Assertions.assertEquals("end is required", e2.getMessage());

        IllegalArgumentException e3 = Assertions.assertThrows(IllegalArgumentException.class, () -> TextInterval.newInterval(TextPosition.newPosition(1, 2), TextPosition.newPosition(1, 1)));
        Assertions.assertEquals("Invalid interval: start {1,2} is after end {1,1}", e3.getMessage());

        IllegalArgumentException e4 = Assertions.assertThrows(IllegalArgumentException.class, () -> TextInterval.newInterval(1, 2, 0, 0));
        Assertions.assertEquals("Invalid interval: start {1,2} is after end {0,0}", e4.getMessage());
    }

    @Test
    public void testGetStartEnd()
    {
        TextPosition pos1 = TextPosition.newPosition(5, 3);
        TextPosition pos2 = TextPosition.newPosition(7, 19);

        TextInterval int1 = TextInterval.newInterval(pos1, pos2);
        Assertions.assertSame(int1.getStart(), pos1);
        Assertions.assertSame(int1.getEnd(), pos2);

        TextInterval int2 = TextInterval.newInterval(5, 3, 7, 19);
        Assertions.assertEquals(int2.getStart(), pos1);
        Assertions.assertEquals(int2.getEnd(), pos2);
    }

    @Test
    public void testToString()
    {
        Assertions.assertEquals("0:0-75:0", TextInterval.newInterval(0, 0, 75, 0).toString(true));
        Assertions.assertEquals("TextInterval{start=0:0 end=75:0}", TextInterval.newInterval(0, 0, 75, 0).toString(false));

        Assertions.assertEquals("5:3-7:19", TextInterval.newInterval(5, 3, 7, 19).toString(true));
        Assertions.assertEquals("TextInterval{start=5:3 end=7:19}", TextInterval.newInterval(5, 3, 7, 19).toString(false));
    }

    @Test
    public void testToCompactString()
    {
        Assertions.assertEquals("0:0-75:0", TextInterval.newInterval(0, 0, 75, 0).toCompactString());
        Assertions.assertEquals("0:0-75:0", TextInterval.newInterval(0, 0, 75, 0).toCompactString(0, 0));
        Assertions.assertEquals("1:0-76:0", TextInterval.newInterval(0, 0, 75, 0).toCompactString(1, 0));
        Assertions.assertEquals("0:1-75:1", TextInterval.newInterval(0, 0, 75, 0).toCompactString(0, 1));
        Assertions.assertEquals("1:1-76:1", TextInterval.newInterval(0, 0, 75, 0).toCompactString(1, 1));

        Assertions.assertEquals("5:3-7:19", TextInterval.newInterval(5, 3, 7, 19).toCompactString());
        Assertions.assertEquals("5:3-7:19", TextInterval.newInterval(5, 3, 7, 19).toCompactString(0, 0));
        Assertions.assertEquals("6:3-8:19", TextInterval.newInterval(5, 3, 7, 19).toCompactString(1, 0));
        Assertions.assertEquals("5:4-7:20", TextInterval.newInterval(5, 3, 7, 19).toCompactString(0, 1));
        Assertions.assertEquals("6:4-8:20", TextInterval.newInterval(5, 3, 7, 19).toCompactString(1, 1));

        Assertions.assertEquals("5:3-19", TextInterval.newInterval(5, 3, 5, 19).toCompactString());
        Assertions.assertEquals("5:3-19", TextInterval.newInterval(5, 3, 5, 19).toCompactString(0, 0));
        Assertions.assertEquals("6:3-19", TextInterval.newInterval(5, 3, 5, 19).toCompactString(1, 0));
        Assertions.assertEquals("5:4-20", TextInterval.newInterval(5, 3, 5, 19).toCompactString(0, 1));
        Assertions.assertEquals("6:4-20", TextInterval.newInterval(5, 3, 5, 19).toCompactString(1, 1));

        Assertions.assertEquals("28:43", TextInterval.newInterval(28, 43, 28, 43).toCompactString());
        Assertions.assertEquals("28:43", TextInterval.newInterval(28, 43, 28, 43).toCompactString(0, 0));
        Assertions.assertEquals("29:43", TextInterval.newInterval(28, 43, 28, 43).toCompactString(1, 0));
        Assertions.assertEquals("28:44", TextInterval.newInterval(28, 43, 28, 43).toCompactString(0, 1));
        Assertions.assertEquals("29:44", TextInterval.newInterval(28, 43, 28, 43).toCompactString(1, 1));
    }
}
