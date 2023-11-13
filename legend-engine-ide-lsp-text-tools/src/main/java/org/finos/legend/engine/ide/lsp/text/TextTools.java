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

package org.finos.legend.engine.ide.lsp.text;

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
    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\h\\v]");

    /**
     * Return the index of the first non-whitespace character found in a region of text, or -1 if no non-whitespace
     * character is found.
     *
     * @param text  text
     * @param start start of region (inclusive)
     * @param end   end of region (exclusive)
     * @return index of first non-whitespace character or -1
     * @see #indexOfWhitespace 
     * @see #isBlank
     */
    public static int indexOfNonWhitespace(CharSequence text, int start, int end)
    {
        checkRegionBounds(text, start, end);
        Matcher matcher = NON_WHITESPACE_PATTERN.matcher(text).region(start, end);
        return matcher.find() ? matcher.start() : -1;
    }

    /**
     * Return the index of the first whitespace character found in a region of text, or -1 if no whitespace character is
     * found.
     *
     * @param text  text
     * @param start start of region (inclusive)
     * @param end   end of region (exclusive)
     * @return index of first whitespace character or -1
     * @see #indexOfNonWhitespace
     * @see #isBlank 
     */
    public static int indexOfWhitespace(CharSequence text, int start, int end)
    {
        checkRegionBounds(text, start, end);
        Matcher matcher = WHITESPACE_PATTERN.matcher(text).region(start, end);
        return matcher.find() ? matcher.start() : -1;
    }

    /**
     * Return whether a region of text blank, meaning it is empty or contains only whitespace.
     *
     * @param text  text
     * @param start start of region (inclusive)
     * @param end   end of region (exclusive)
     * @return whether the region of text is blank
     * @see #indexOfNonWhitespace
     */
    public static boolean isBlank(CharSequence text, int start, int end)
    {
        checkRegionBounds(text, start, end);
        return !NON_WHITESPACE_PATTERN.matcher(text).region(start, end).find();
    }

    /**
     * Return an index of all the lines in the text. The result is an array of indices, where the Nth value is the
     * index of the start of the Nth line. The result will never be empty, and will always contain {@code 0} as its
     * first value.
     *
     * @param text text
     * @return line index
     * @see #indexLines(CharSequence, boolean)
     */
    public static int[] indexLines(CharSequence text)
    {
        return indexLines(text, false);
    }

    /**
     * <p>Return an index of all the lines in the text. If {@code ignoreEmptyFinalLine} is true, then the final line
     * will be ignored if it is empty. Otherwise, it will be indexed, regardless of whether it is empty.</p>
     * <br>
     * <p>The result is an array of indices, where the Nth value is the index of the start of the Nth line of the
     * string. Note that if the text is empty and {@code ignoreEmptyFinalLine} is true, the result will be empty.
     * Otherwise, it will be non-empty and contain {@code 0} as its first value.</p>
     *
     * @param text                 text
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return line index
     */
    public static int[] indexLines(CharSequence text, boolean ignoreEmptyFinalLine)
    {
        return indexLines(text, 0, text.length(), ignoreEmptyFinalLine);
    }

    /**
     * Return an index of all the lines in a region of text. The result is an array of indices, where the Nth value is
     * the index of the start of the Nth line of the region. The result will never be empty, and will always contain
     * {@code start} as its first value.
     *
     * @param text  text
     * @param start start of region (inclusive)
     * @param end   end of region (exclusive)
     * @return line index
     * @see #indexLines(CharSequence, int, int, boolean)
     */
    public static int[] indexLines(CharSequence text, int start, int end)
    {
        return indexLines(text, start, end, false);
    }

    /**
     * <p>Return an index of all the lines in a region of text. If {@code ignoreEmptyFinalLine} is true, then the final
     * line of the region will be ignored if it is empty. Otherwise, it will be indexed, regardless of whether it is
     * empty.</p>
     * <br>
     * <p>The result is an array of indices, where the Nth value is the index of the start of the Nth line of the
     * region. Note that if the region is empty and {@code ignoreEmptyFinalLine} is true, the result will be empty.
     * Otherwise, it will be non-empty and contain {@code start} as its first value.</p>
     *
     * @param text                 text
     * @param start                start of region (inclusive)
     * @param end                  end of region (exclusive)
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return line index
     */
    public static int[] indexLines(CharSequence text, int start, int end, boolean ignoreEmptyFinalLine)
    {
        checkRegionBounds(text, start, end);
        if (end == start)
        {
            return ignoreEmptyFinalLine ? new int[0] : new int[]{start};
        }

        IntStream.Builder builder = IntStream.builder();
        if (!ignoreEmptyFinalLine)
        {
            builder.add(start);
        }
        Matcher matcher = (ignoreEmptyFinalLine ? LINE_START_PATTERN : LINE_BREAK_PATTERN).matcher(text).region(start, end);
        while (matcher.find())
        {
            builder.add(matcher.end());
        }
        return builder.build().toArray();
    }

    /**
     * Count the number of lines in text, including empty lines. All text, including empty text, will have at least 1
     * line.
     *
     * @param text text
     * @return number of lines in the text
     * @see #countLines(CharSequence, boolean)
     */
    public static int countLines(CharSequence text)
    {
        return countLines(text, false);
    }

    /**
     * Count the number of lines in text, including empty lines. If {@code ignoreEmptyFinalLine} is true, then the final
     * line will not be counted if it is empty. Otherwise, the final line will be counted regardless of whether it is
     * empty.
     *
     * @param text                 text
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return number of lines in the text
     */
    public static int countLines(CharSequence text, boolean ignoreEmptyFinalLine)
    {
        return ignoreEmptyFinalLine ?
                countMatches(LINE_START_PATTERN.matcher(text)) :
                (countMatches(LINE_BREAK_PATTERN.matcher(text)) + 1);
    }

    /**
     * Count the number of lines, including empty lines, in a region of text. All regions, including empty ones, will
     * have at least 1 line. An empty region will have exactly 1 line.
     *
     * @param text  string
     * @param start start of region (inclusive)
     * @param end   end of region (exclusive)
     * @return number of lines in the text
     * @see #countLines(CharSequence, int, int, boolean)
     */
    public static int countLines(CharSequence text, int start, int end)
    {
        return countLines(text, start, end, false);
    }

    /**
     * Count the number of lines, including empty lines, in a region of text. If {@code ignoreEmptyFinalLine} is true,
     * then the final line of the region will not be counted if it is empty. Otherwise, the final line will be counted
     * regardless of whether it is empty.
     *
     * @param text                 text
     * @param start                start of region (inclusive)
     * @param end                  end of region (exclusive)
     * @param ignoreEmptyFinalLine whether to ignore the final line if it is empty
     * @return number of lines in the text
     */
    public static int countLines(CharSequence text, int start, int end, boolean ignoreEmptyFinalLine)
    {
        checkRegionBounds(text, start, end);
        return ignoreEmptyFinalLine ?
                countMatches(LINE_START_PATTERN.matcher(text).region(start, end)) :
                (countMatches(LINE_BREAK_PATTERN.matcher(text).region(start, end)) + 1);
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

    private static void checkRegionBounds(CharSequence text, int start, int end)
    {
        if ((start < 0) || (start > end) || (end > text.length()))
        {
            throw new StringIndexOutOfBoundsException("start " + start + ", end " + end + ", length " + text.length());
        }
    }
}
