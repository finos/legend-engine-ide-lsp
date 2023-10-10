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

package org.finos.legend.engine.ide.lsp.tools;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

/**
 * A set of tools for processing text.
 */
public class TextTools
{
    private static final Pattern LINE_BREAK_PATTERN = Pattern.compile("\\R");
    private static final Pattern LINE_START_PATTERN = Pattern.compile("^", Pattern.MULTILINE);
    private static final Pattern NON_WHITESPACE_PATTERN = Pattern.compile("[^\\h\\v]");

    /**
     * Return the index of the first non-whitespace character found in the region of the string, or -1 if no
     * non-whitespace character is found.
     *
     * @param string string
     * @param start  start of region (inclusive)
     * @param end    end of region (exclusive)
     * @return index of first non-whitespace character or -1
     * @see #isBlank
     */
    public static int indexOfNonWhitespace(String string, int start, int end)
    {
        checkRegionBounds(string, start, end);
        Matcher matcher = NON_WHITESPACE_PATTERN.matcher(string).region(start, end);
        return matcher.find() ? matcher.start() : -1;
    }

    /**
     * Return whether the region of the string is blank, meaning it is empty or contains only whitespace.
     *
     * @param string string
     * @param start  start of region (inclusive)
     * @param end    end of region (exclusive)
     * @return whether the string region is blank
     * @see #indexOfNonWhitespace
     */
    public static boolean isBlank(String string, int start, int end)
    {
        checkRegionBounds(string, start, end);
        return !NON_WHITESPACE_PATTERN.matcher(string).region(start, end).find();
    }

    /**
     * Return an index of all the lines in the string. The result is an array of indices, where the Nth value is the
     * index of the start of the Nth line. The result will never be empty, and will always contain {@code 0} as its
     * first value.
     *
     * @param string string
     * @return line index
     * @see #indexLines(String, boolean)
     */
    public static int[] indexLines(String string)
    {
        return indexLines(string, false);
    }

    /**
     * <p>Return an index of all the lines in the string. If {@code ignoreEmptyFinalLine} is true, then the final line
     * of the string will be ignored if it is empty. Otherwise, it will be indexed, regardless of whether it is
     * empty.</p>
     *
     * <p>The result is an array of indices, where the Nth value is the index of the start of the Nth line of the
     * string. Note that if the string is empty and {@code ignoreEmptyFinalLine} is true, the result will be empty.
     * Otherwise, it will be non-empty and contain {@code 0} as its first value.</p>
     *
     * @param string               string
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return line index
     */
    public static int[] indexLines(String string, boolean ignoreEmptyFinalLine)
    {
        return indexLines(string, 0, string.length(), ignoreEmptyFinalLine);
    }

    /**
     * Return an index of all the lines in the region of the string. The result is an array of indices, where the Nth
     * value is the index of the start of the Nth line of the region. The result will never be empty, and will always
     * contain {@code start} as its first value.
     *
     * @param string string
     * @param start  start of region (inclusive)
     * @param end    end of region (exclusive)
     * @return line index
     * @see #indexLines(String, int, int, boolean)
     */
    public static int[] indexLines(String string, int start, int end)
    {
        return indexLines(string, start, end, false);
    }

    /**
     * <p>Return an index of all the lines in the region of the string. If {@code ignoreEmptyFinalLine} is true, then
     * the final line of the region will be ignored if it is empty. Otherwise, it will be indexed, regardless of whether
     * it is empty.</p>
     *
     * <p>The result is an array of indices, where the Nth value is the index of the start of the Nth line of the
     * region. Note that if the region is empty and {@code ignoreEmptyFinalLine} is true, the result will be empty.
     * Otherwise, it will be non-empty and contain {@code start} as its first value.</p>
     *
     * @param string               string
     * @param start                start of region (inclusive)
     * @param end                  end of region (exclusive)
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return line index
     */
    public static int[] indexLines(String string, int start, int end, boolean ignoreEmptyFinalLine)
    {
        checkRegionBounds(string, start, end);
        if (end == start)
        {
            return ignoreEmptyFinalLine ? new int[0] : new int[]{start};
        }

        IntStream.Builder builder = IntStream.builder();
        if (!ignoreEmptyFinalLine)
        {
            builder.add(start);
        }
        Matcher matcher = (ignoreEmptyFinalLine ? LINE_START_PATTERN : LINE_BREAK_PATTERN).matcher(string).region(start, end);
        while (matcher.find())
        {
            builder.add(matcher.end());
        }
        return builder.build().toArray();
    }

    /**
     * Count the number of lines in a string, including empty lines. All strings, including the empty string, will have
     * at least 1 line. The empty string will have exactly 1 line.
     *
     * @param string string
     * @return number of lines in the string
     * @see #countLines(String, boolean)
     */
    public static int countLines(String string)
    {
        return countLines(string, false);
    }

    /**
     * Count the number of lines in a string, including empty lines. If {@code ignoreEmptyFinalLine} is true, then the
     * final line of the string will not be counted if it is empty. Otherwise, the final line will be counted regardless
     * of whether it is empty.
     *
     * @param string               string
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return number of lines in the string
     */
    public static int countLines(String string, boolean ignoreEmptyFinalLine)
    {
        return ignoreEmptyFinalLine ?
                countMatches(LINE_START_PATTERN.matcher(string)) :
                (countMatches(LINE_BREAK_PATTERN.matcher(string)) + 1);
    }

    /**
     * Count the number of lines, including empty lines, in a region of a string. All regions, including empty ones,
     * will have at least 1 line. An empty region will have exactly 1 line.
     *
     * @param string string
     * @param start  start of region (inclusive)
     * @param end    end of region (exclusive)
     * @return number of lines in the string
     * @see #countLines(String, int, int, boolean)
     */
    public static int countLines(String string, int start, int end)
    {
        return countLines(string, start, end, false);
    }

    /**
     * Count the number of lines, including empty lines, in a region of a string. If {@code ignoreEmptyFinalLine} is
     * true, then the final line of the region will not be counted if it is empty. Otherwise, the final line will be
     * counted regardless of whether it is empty.
     *
     * @param string               string
     * @param start                start of region (inclusive)
     * @param end                  end of region (exclusive)
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return number of lines in the string
     */
    public static int countLines(String string, int start, int end, boolean ignoreEmptyFinalLine)
    {
        checkRegionBounds(string, start, end);
        return ignoreEmptyFinalLine ?
                countMatches(LINE_START_PATTERN.matcher(string).region(start, end)) :
                (countMatches(LINE_BREAK_PATTERN.matcher(string).region(start, end)) + 1);
    }

    private static int countMatches(Matcher matcher)
    {
        int count = 0;
        while (matcher.find())
        {
            count++;
        }
        return count;
    }

    private static void checkRegionBounds(String string, int start, int end)
    {
        if ((start < 0) || (start > end) || (end > string.length()))
        {
            throw new StringIndexOutOfBoundsException("start " + start + ", end " + end + ", length " + string.length());
        }
    }
}
