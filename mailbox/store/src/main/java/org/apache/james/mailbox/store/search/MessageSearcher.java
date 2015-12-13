/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.mailbox.store.search;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.stream.EntityState;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.james.mime4j.stream.MimeTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.CharBuffer;
import java.util.List;

/**
 * Searches an email for content. This class should be safe for use by
 * concurrent threads.
 */
public class MessageSearcher {

    private Logger logger;
    private List<CharSequence> searchContents;
    private List<String> contentTypes;
    private boolean isCaseInsensitive = false;
    private boolean includeHeaders = false;

    public MessageSearcher(List<CharSequence> searchContents, boolean isCaseInsensitive, boolean includeHeaders, List<String> contentTypes) {
        this.contentTypes = ImmutableList.copyOf(contentTypes);
        this.searchContents = ImmutableList.copyOf(searchContents);
        this.isCaseInsensitive = isCaseInsensitive;
        this.includeHeaders = includeHeaders;
    }

    public MessageSearcher(List<CharSequence> searchContents, boolean isCaseInsensitive, boolean includeHeaders) {
        this(searchContents, isCaseInsensitive, includeHeaders, Lists.<String>newArrayList());
    }


    /**
     * Is searchContents found in the given input?
     *
     * @param input
     *            <code>InputStream</code> containing an email
     * @return true if the content exists and the stream contains the content,
     *         false otherwise. It takes the mime structure into account.
     * @throws IOException
     * @throws MimeException
     */
    public boolean isFoundIn(final InputStream input) throws IOException, MimeException {
        final boolean includeHeaders;
        final List<CharSequence> searchContents;
        final boolean isCaseInsensitive;
        final List<String> contentTypes;
        synchronized (this) {
            includeHeaders = this.includeHeaders;
            searchContents = this.searchContents;
            isCaseInsensitive = this.isCaseInsensitive;
            contentTypes = this.contentTypes;
        }
        for (CharSequence charSequence : searchContents) {
            if (charSequence != null) {
                final CharBuffer buffer = createBuffer(charSequence, isCaseInsensitive);
                if (! parseOnContentTypes(input, isCaseInsensitive, includeHeaders, buffer, contentTypes)) {
                    System.out.println("Did not found <" + charSequence + ">");
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Is the search contents found in the given input?
     *
     * @param input <code>InputStream</code> containing an email
     * @return true if the content exists and the stream contains the content,
     *         false otherwise
     * @throws IOException
     * @throws MimeException
     */
    public boolean isFoundInIgnoringMime(final InputStream input) throws IOException, MimeException {
        final List<CharSequence> searchContents;
        final boolean isCaseInsensitive;
        synchronized (this) {
            searchContents = this.searchContents;
            isCaseInsensitive = this.isCaseInsensitive;
        }
        for (CharSequence charSequence : searchContents) {
            if (charSequence != null && ! charSequence.equals("")) {
                final CharBuffer buffer = createBuffer(charSequence, isCaseInsensitive);
                if (! isFoundIn(new InputStreamReader(input), buffer, isCaseInsensitive)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean parseOnContentTypes(final InputStream input, final boolean isCaseInsensitive, final boolean includeHeaders,
                                        final CharBuffer buffer, final List<String> contentTypes) throws IOException, MimeException {
        try {
            MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).build();

            MimeTokenStream parser = new MimeTokenStream(config);
            parser.parse(input);
            while (parser.next() != EntityState.T_END_OF_STREAM) {
                final EntityState state = parser.getState();
                switch (state) {
                    case T_BODY:
                        if (contentTypes.isEmpty() || contentTypes.contains(parser.getBodyDescriptor().getMimeType())) {
                            if (checkBody(isCaseInsensitive, buffer, parser)) {
                                return true;
                            }
                        }
                    case T_PREAMBLE:
                    case T_EPILOGUE:
                        if (includeHeaders) {
                            if (checkBody(isCaseInsensitive, buffer, parser)) {
                                return true;
                            }
                        }
                        break;
                    case T_FIELD:
                        if (includeHeaders) {
                            if (checkHeader(isCaseInsensitive, buffer, parser)) {
                                return true;
                            }
                        }
                        break;
                case T_END_BODYPART:
                case T_END_HEADER:
                case T_END_MESSAGE:
                case T_END_MULTIPART:
                case T_END_OF_STREAM:
                case T_RAW_ENTITY:
                case T_START_BODYPART:
                case T_START_HEADER:
                case T_START_MESSAGE:
                case T_START_MULTIPART:
                    break;
                }
            }
        } catch (Exception e) {
            handle(e);
        }
        return false;
    }

    private boolean checkHeader(final boolean isCaseInsensitive, final CharBuffer buffer, MimeTokenStream parser) throws IOException {
        System.out.println("Looking headers");
        final String value = parser.getField().getBody();
        final StringReader reader = new StringReader(value);
        return isFoundIn(reader, buffer, isCaseInsensitive);
    }

    private boolean checkBody(final boolean isCaseInsensitive, final CharBuffer buffer, MimeTokenStream parser) throws IOException {
        System.out.println("Looking body");
        final Reader reader = parser.getReader();
        return isFoundIn(reader, buffer, isCaseInsensitive);
    }

    private CharBuffer createBuffer(final CharSequence searchContent, final boolean isCaseInsensitive) {
        final CharBuffer buffer;
        if (isCaseInsensitive) {
            final int length = searchContent.length();
            buffer = CharBuffer.allocate(length);
            for (int i = 0; i < length; i++) {
                final char next = searchContent.charAt(i);
                final char upperCase = Character.toUpperCase(next);
                buffer.put(upperCase);
            }
            buffer.flip();
        } else {
            buffer = CharBuffer.wrap(searchContent);
        }
        return buffer;
    }

    protected void handle(Exception e) throws IOException, MimeException {
        final Logger logger = getLogger();
        logger.warn("Cannot read MIME body.");
        logger.debug("Failed to read body.", e);
    }

    public boolean isFoundIn(final Reader reader, final CharBuffer buffer, final boolean isCaseInsensitive) throws IOException {
        int read;
        while ((read = reader.read()) != -1) {
            if (matches(buffer, computeNextChar(isCaseInsensitive, (char) read))) {
                return true;
            }
        }
        return false;
    }

    private char computeNextChar(boolean isCaseInsensitive, char read) {
        if (isCaseInsensitive) {
            return Character.toUpperCase(read);
        } else {
            return read;
        }
    }

    private boolean matches(final CharBuffer buffer, final char next) {
        if (buffer.hasRemaining()) {
            final boolean partialMatch = (buffer.position() > 0);
            final char matching = buffer.get();
            if (next != matching) {
                buffer.rewind();
                if (partialMatch) {
                    return matches(buffer, next);
                }
            }
        } else {
            return true;
        }
        return false;
    }

    public final Logger getLogger() {
        if (logger == null) {
            logger = LoggerFactory.getLogger(MessageSearcher.class);
        }
        return logger;
    }

    public final void setLogger(Logger logger) {
        this.logger = logger;
    }
}
