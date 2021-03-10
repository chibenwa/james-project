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

package org.apache.james.server.core;

import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;

import javax.mail.MessagingException;
import javax.mail.internet.InternetHeaders;

import org.apache.mailet.base.RFC2822Headers;

import com.sun.mail.util.PropUtil;

/**
 * This interface defines a container for mail headers. Each header must use
 * MIME format:
 * 
 * <pre>
 * name: value
 * </pre>
 */
public class MailHeaders extends InternetHeaders implements Serializable, Cloneable {
    private static final boolean ignoreWhitespaceLines =
        PropUtil.getBooleanSystemProperty("mail.mime.ignorewhitespacelines",
            false);

    public static class LineInputStream extends FilterInputStream {
        private boolean allowutf8;
        private byte[] lineBuffer = null; // reusable byte buffer
        private static int MAX_INCR = 1024 * 1024;    // 1MB
        public long longbread;

        public long getLongbread() {
            return longbread;
        }

        /**
         * @param in the InputStream
         * @param allowutf8    allow UTF-8 characters?
         * @since    JavaMail 1.6
         */
        public LineInputStream(InputStream in, boolean allowutf8) {
            super(in);
            this.allowutf8 = allowutf8;
        }

        /**
         * Read a line containing only ASCII characters from the input
         * stream. A line is terminated by a CR or NL or CR-NL sequence.
         * A common error is a CR-CR-NL sequence, which will also terminate
         * a line.
         * The line terminator is not returned as part of the returned
         * String. Returns null if no data is available. <p>
         *
         * This class is similar to the deprecated
         * <code>DataInputStream.readLine()</code>
         *
         * @return        the line
         * @exception    IOException    for I/O errors
         */
        @SuppressWarnings("deprecation")    // for old String constructor
        public String readLine() throws IOException {
            //InputStream in = this.in;
            byte[] buf = lineBuffer;

            if (buf == null) {
                buf = lineBuffer = new byte[128];
            }

            int c1;
            int room = buf.length;
            int offset = 0;

            while ((c1 = in.read()) != -1) {
                longbread++;
                if (c1 == '\n') { // Got NL, outa here.
                    break;
                } else if (c1 == '\r') {
                    // Got CR, is the next char NL ?
                    boolean twoCRs = false;
                    if (in.markSupported()) {
                        in.mark(2);
                    }
                    int c2 = in.read();
                    longbread++;
                    if (c2 == '\r') {        // discard extraneous CR
                        twoCRs = true;
                        c2 = in.read();
                        longbread++;
                    }
                    if (c2 != '\n') {
                        /*
                         * If the stream supports it (which we hope will always
                         * be the case), reset to after the first CR.  Otherwise,
                         * we wrap a PushbackInputStream around the stream so we
                         * can unread the characters we don't need.  The only
                         * problem with that is that the caller might stop
                         * reading from this LineInputStream, throw it away,
                         * and then start reading from the underlying stream.
                         * If that happens, the pushed back characters will be
                         * lost forever.
                         */
                        if (in.markSupported()) {
                            in.reset();
                            longbread -= 2;
                        } else {
                            if (!(in instanceof PushbackInputStream)) {
                                in /*= this.in*/ = new PushbackInputStream(in, 2);
                            }
                            if (c2 != -1) {
                                ((PushbackInputStream) in).unread(c2);
                            }
                            if (twoCRs) {
                                ((PushbackInputStream) in).unread('\r');
                            }
                            longbread -= 2;
                        }
                    }
                    break; // outa here.
                }

                // Not CR, NL or CR-NL ...
                // .. Insert the byte into our byte buffer
                if (--room < 0) { // No room, need to grow.
                    if (buf.length < MAX_INCR) {
                        buf = new byte[buf.length * 2];
                    } else {
                        buf = new byte[buf.length + MAX_INCR];
                    }
                    room = buf.length - offset - 1;
                    System.arraycopy(lineBuffer, 0, buf, 0, offset);
                    lineBuffer = buf;
                }
                buf[offset++] = (byte)c1;
            }

            if ((c1 == -1) && (offset == 0)) {
                return null;
            }

            if (allowutf8) {
                return new String(buf, 0, offset, StandardCharsets.UTF_8);
            } else {
                return new String(buf, 0, 0, offset);
            }
        }
    }


    private static final long serialVersionUID = 238748126601L;
    private boolean modified = false;
    private long size = -1;
    private long originalSize = -1;

    /**
     * No argument constructor
     * 
     * @throws MessagingException
     *             if the super class cannot be properly instantiated
     */
    public MailHeaders() {
        super();
    }

    /**
     * Constructor that takes an InputStream containing the contents of the set
     * of mail headers.
     * 
     * @param in
     *            the InputStream containing the header data
     * 
     * @throws MessagingException
     *             if the super class cannot be properly instantiated based on
     *             the stream
     */
    public MailHeaders(InputStream in) throws MessagingException {
        super();
        load(in);
    }

    /**
     * Read and parse the given RFC822 message stream till the
     * blank line separating the header from the body. Store the
     * header lines inside this InternetHeaders object. The order
     * of header lines is preserved. <p>
     *
     * Note that the header lines are added into this InternetHeaders
     * object, so any existing headers in this object will not be
     * affected.  Headers are added to the end of the existing list
     * of headers, in order.
     *
     * @param    is     RFC822 input stream
     * @param    allowutf8     if UTF-8 encoded headers are allowed
     * @exception    MessagingException for any I/O error reading the stream
     * @since        JavaMail 1.6
     */
    public void load(InputStream is, boolean allowutf8)
        throws MessagingException {
        // Read header lines until a blank line. It is valid
        // to have BodyParts with no header lines.
        String line;
        LineInputStream lis = new LineInputStream(is, allowutf8);
        String prevline = null;    // the previous header line, as a string
        // a buffer to accumulate the header in, when we know it's needed
        StringBuilder lineBuffer = new StringBuilder();
        size = 0;

        try {
            // if the first line being read is a continuation line,
            // we ignore it if it's otherwise empty or we treat it as
            // a non-continuation line if it has non-whitespace content
            boolean first = true;
            do {
                line = lis.readLine();
                if (line != null && (line.startsWith(" ") || line.startsWith("\t"))) {
                    // continuation of header
                    if (prevline != null) {
                        lineBuffer.append(prevline);
                        prevline = null;
                    }
                    if (first) {
                        String lt = line.trim();
                        if (lt.length() > 0) {
                            lineBuffer.append(lt);
                        }
                    } else {
                        if (lineBuffer.length() > 0) {
                            lineBuffer.append("\r\n");
                        }
                        lineBuffer.append(line);
                    }
                } else {
                    // new header
                    if (prevline != null) {
                        addHeaderLine(prevline);
                    } else if (lineBuffer.length() > 0) {
                        // store previous header first
                        addHeaderLine(lineBuffer.toString());
                        lineBuffer.setLength(0);
                    }
                    prevline = line;
                }
                first = false;
            } while (line != null && !isEmpty(line));
        } catch (IOException ioex) {
            throw new MessagingException("Error in input stream", ioex);
        } finally {
            originalSize = lis.longbread;
        }
    }

    /**
     * Is this line an empty (blank) line?
     */
    private static boolean isEmpty(String line) {
        return line.length() == 0 || (ignoreWhitespaceLines && line.trim().length() == 0);
    }


    /**
     * Write the headers to an output stream
     * 
     * @param out
     *            the OutputStream to which to write the headers
     */
    public void writeTo(OutputStream out) throws MessagingException {
        MimeMessageUtil.writeHeadersTo(getAllHeaderLines(), out);
    }

    /**
     * Generate a representation of the headers as a series of bytes.
     * 
     * @return the byte array containing the headers
     */
    public byte[] toByteArray() throws MessagingException {
        ByteArrayOutputStream headersBytes = new ByteArrayOutputStream();
        writeTo(headersBytes);
        return headersBytes.toByteArray();
    }

    /**
     * If the new header is a Return-Path we get sure that we add it to the top
     * Javamail, at least until 1.4.0 does the wrong thing if it loaded a stream
     * with a return-path in the middle.
     */
    @Override
    public synchronized void addHeader(String arg0, String arg1) {
        if (RFC2822Headers.RETURN_PATH.equalsIgnoreCase(arg0)) {
            headers.add(0, new InternetHeader(arg0, arg1));
        } else {
            super.addHeader(arg0, arg1);
        }
        modified();
    }

    /**
     * If the new header is a Return-Path we get sure that we add it to the top
     * Javamail, at least until 1.4.0 does the wrong thing if it loaded a stream
     * with a return-path in the middle.
     */
    @Override
    public synchronized void setHeader(String arg0, String arg1) {
        if (RFC2822Headers.RETURN_PATH.equalsIgnoreCase(arg0)) {
            super.removeHeader(arg0);
        }
        super.setHeader(arg0, arg1);

        modified();
    }

    @Override
    public synchronized void removeHeader(String name) {
        super.removeHeader(name);
        modified();
    }

    @Override
    public synchronized void addHeaderLine(String line) {
        super.addHeaderLine(line);
        modified();
    }

    private void modified() {
        modified = true;
        size = -1;
    }

    /**
     * Return the size of the headers
     *
     * @return size
     */
    public synchronized long getSize() {
        if (size == -1 || modified) {
            long c = 0;
            Enumeration<String> headerLines = getAllHeaderLines();
            while (headerLines.hasMoreElements()) {
                c += headerLines.nextElement().getBytes(StandardCharsets.UTF_8).length;
                // CRLF
                c += 2;
            }
            size = c;
            modified = false;
        }
        return size;

    }

    public long getOriginalSize() {
        return originalSize;
    }
}
