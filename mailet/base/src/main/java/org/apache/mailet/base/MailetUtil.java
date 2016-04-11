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



package org.apache.mailet.base;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import javax.mail.MessagingException;

import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.apache.mailet.Mail;
import org.apache.mailet.MailetConfig;


/**
 * Collects utility methods.
 */
public class MailetUtil {
    
    /**
     * <p>This takes the subject string and reduces (normailzes) it.
     * Multiple "Re:" entries are reduced to one, and capitalized.  The
     * prefix is always moved/placed at the beginning of the line, and
     * extra blanks are reduced, so that the output is always of the
     * form:</p>
     * <code>
     * &lt;prefix&gt; + &lt;one-optional-"Re:"*gt; + &lt;remaining subject&gt;
     * </code>
     * <p>I have done extensive testing of this routine with a standalone
     * driver, and am leaving the commented out debug messages so that
     * when someone decides to enhance this method, it can be yanked it
     * from this file, embedded it with a test driver, and the comments
     * enabled.</p>
     */
    public static String normalizeSubject(String subj, String prefix) {
        StringBuilder subject = new StringBuilder(subj);
        int prefixLength = prefix.length();

        // If the "prefix" is not at the beginning the subject line, remove it
        int index = subject.indexOf(prefix);
        if (index != 0) {

            if (index > 0) {
                subject.delete(index, index + prefixLength);
            }
            subject.insert(0, prefix); // insert prefix at the front
        }

        // Replace Re: with RE:
        String match = "Re:";
        index = subject.indexOf(match, prefixLength);

        while(index > -1) {
            subject.replace(index, index + match.length(), "RE:");
            index = subject.indexOf(match, prefixLength);
        }

        // Reduce them to one at the beginning
        match ="RE:";
        int indexRE = subject.indexOf(match, prefixLength) + match.length();
        index = subject.indexOf(match, indexRE);
        while(index > 0) {    
            subject.delete(index, index + match.length());
            index = subject.indexOf(match, indexRE);
        }

        // Reduce blanks
        match = "  ";
        index = subject.indexOf(match, prefixLength);
        while(index > -1) {
            subject.replace(index, index + match.length(), " ");
            index = subject.indexOf(match, prefixLength);
        }
        return subject.toString();
    }

    
    /**
     * <p>Gets a boolean valued init parameter.</p>
     * @param config not null
     * @param name name of the init parameter to be queried
     * @param defaultValue this value will be substituted when the named value
     * cannot be parse or when the init parameter is absent
     * @return true when the init parameter is <code>true</code> (ignoring case);
     * false when the init parameter is <code>false</code> (ignoring case);
     * otherwise the default value
     */
    public static boolean getInitParameter(MailetConfig config, String name, boolean defaultValue) {
        final String value = config.getInitParameter(name);
        final boolean result;
        if ("true".equalsIgnoreCase(value)) {
            result = true;
        } else if ("false".equalsIgnoreCase(value)){
            result = false;
        } else {
            result = defaultValue;
        }
        return result;
    }

    public static boolean canSendAutomaticResponse(Mail mail) throws MessagingException {
        return !isMailingList(mail) &&
            ! isAutoSubmitted(mail) &&
            ! isMdnSentAutomatically(mail);
    }

    public static boolean isMailingList(Mail mail) throws MessagingException {
        return mail.getSender().getLocalPart().startsWith("owner-")
            || mail.getSender().getLocalPart().endsWith("-request")
            || mail.getSender().getLocalPart().equalsIgnoreCase("MAILER-DAEMON")
            || mail.getSender().getLocalPart().equalsIgnoreCase("LISTSERV")
            || mail.getSender().getLocalPart().equalsIgnoreCase("majordomo")
            || mail.getMessage()
            .getMatchingHeaders(new String[]{"List-Help",
                "List-Subscribe",
                "List-Unsubscribe",
                "List-Owner",
                "List-Post",
                "List-Id",
                "List-Archive"})
            .hasMoreElements();
    }

    public static boolean isAutoSubmitted(Mail mail) throws MessagingException {
        String[] headers = mail.getMessage().getHeader("Auto-Submitted");
        if (headers != null && headers.length >= 1) {
            for (String header : headers) {
                if (header.equalsIgnoreCase("auto-replied")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isMdnSentAutomatically(Mail mail) throws MessagingException {
        final ResultCollector resultCollector = new ResultCollector(false);
        MimeConfig config = MimeConfig.custom().setMaxLineLen(-1).setMaxHeaderLen(-1).build();
        final MimeStreamParser parser = new MimeStreamParser(config);
        parser.setContentHandler(createMdnContentHandler(resultCollector));
        try {
            parser.parse(mail.getMessage().getInputStream());
        } catch (MimeException e) {
            throw new MessagingException("Can not parse Mime", e);
        } catch (IOException e) {
            throw new MessagingException("Can not read content", e);
        }
        return resultCollector.getResult();
    }

    private static AbstractContentHandler createMdnContentHandler(final ResultCollector resultCollector) {
        return new AbstractContentHandler() {
            @Override
            public void body(BodyDescriptor bodyDescriptor, InputStream inputStream) throws MimeException, IOException {
                if (bodyDescriptor.getMimeType().equalsIgnoreCase("message/disposition-notification")) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                    String line;
                    while ((line = reader.readLine()) != null ) {
                        if (line.startsWith("Disposition:")) {
                            if (line.contains("MDN-sent-automatically") || line.contains("automatic-action")) {
                                resultCollector.setResult(true);
                            }
                        }
                    }
                }
            }
        };
    }

    private static class ResultCollector {
        private boolean result;

        public ResultCollector(boolean result) {
            this.result = result;
        }

        public boolean getResult() {
            return result;
        }

        public void setResult(boolean result) {
            this.result = result;
        }
    }

}
