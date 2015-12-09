/*
 *   Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package org.apache.james.managesieve.core;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.james.managesieve.api.AuthenticationException;
import org.apache.james.managesieve.api.AuthenticationProcessor;
import org.apache.james.managesieve.api.AuthenticationRequiredException;
import org.apache.james.managesieve.api.ManageSieveException;
import org.apache.james.managesieve.api.Session;
import org.apache.james.managesieve.api.SessionTerminatedException;
import org.apache.james.managesieve.api.SieveParser;
import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.api.UnknownSaslMechanism;
import org.apache.james.managesieve.api.commands.CoreCommands;
import org.apache.james.managesieve.util.ParserUtils;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.SieveRepositoryException;
import org.apache.james.sieverepository.api.exception.StorageException;
import org.apache.james.sieverepository.api.exception.UserNotFoundException;
import org.apache.james.user.api.UsersRepository;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CoreProcessor implements CoreCommands {

    interface CommandWrapper {
        String execute() throws ManageSieveException, SieveRepositoryException;
    }

    public static final String IMPLEMENTATION_DESCRIPTION = "Apache ManageSieve v1.0";
    public static final String MANAGE_SIEVE_VERSION = "1.0";

    private final SieveRepository sieveRepository;
    private final SieveParser parser;
    private final Map<Capabilities, String> capabilitiesBase;
    private final Map<SupportedMechanism, AuthenticationProcessor> authenticationProcessorMap;

    @Inject
    public CoreProcessor(SieveRepository repository, UsersRepository usersRepository, SieveParser parser) {
        this.sieveRepository = repository;
        this.parser = parser;
        this.capabilitiesBase = precomputedCapabilitiesBase(parser);
        this.authenticationProcessorMap = new HashMap<SupportedMechanism, AuthenticationProcessor>();
        this.authenticationProcessorMap.put(SupportedMechanism.PLAIN, new PlainAuthenticationProcessor(usersRepository));
    }

    @Override
    public String getAdvertisedCapabilities() {
        return convertCapabilityMapToString(capabilitiesBase) + "\r\n";
    }

    @Override
    public String capability(Session session) {
        return convertCapabilityMapToString(computeCapabilityMap(session)) + "\r\nOK";
    }

    private String convertCapabilityMapToString(Map<Capabilities, String> capabilitiesStringMap) {
        return Joiner.on("\r\n").join(
            Iterables.transform(capabilitiesStringMap.entrySet(), new Function<Map.Entry<Capabilities,String>, String>() {
                public String apply(Map.Entry<Capabilities, String> capabilitiesStringEntry) {
                    return computeCapabilityEntryString(capabilitiesStringEntry);
                }
            }));
    }

    private Map<Capabilities, String> computeCapabilityMap(Session session) {
        Map<Capabilities, String> capabilities = Maps.newHashMap(capabilitiesBase);
        if (session.isAuthenticated()) {
            capabilities.put(Capabilities.OWNER, session.getUser());
        }
        return capabilities;
    }

    private String computeCapabilityEntryString(Map.Entry<Capabilities, String> entry) {
        return "\"" + entry.getKey().toString() + "\"" +
            ( entry.getValue() == null ? "" : " \"" + entry.getValue() + "\"" );
    }

    @Override
    public String checkScript(final Session session, final String content) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                return manageWarnings(parser.parse(content));
            }
        }, session);
    }

    private String manageWarnings(List<String> warnings) {
        if (!warnings.isEmpty()) {
            return "OK (WARNINGS) " + Joiner.on(' ').join(Iterables.transform(warnings, new Function<String, String>() {
                public String apply(String s) {
                    return '\"' + s + '"';
                }
            }));
        } else {
            return "OK";
        }
    }

    @Override
    public String deleteScript(final Session session, final String name) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                sieveRepository.deleteScript(session.getUser(), name);
                return "OK";
            }
        }, session);
    }

    @Override
    public String getScript(final Session session, final String name) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                String scriptContent = sieveRepository.getScript(session.getUser(), name);
                return "{" + scriptContent.length() + "}" + "\r\n" + scriptContent + "\r\nOK";
            }
        }, session);
    }

    @Override
    public String haveSpace(final Session session, final String name, final long size) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                sieveRepository.haveSpace(session.getUser(), name, size);
                return "OK";
            }
        }, session);
    }

    @Override
    public String listScripts(final Session session) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                return listScriptsInternals(session);
            }
        }, session);
    }

    private String listScriptsInternals(Session session) throws AuthenticationRequiredException, UserNotFoundException, StorageException {
        authenticationCheck(session);
        String list = Joiner.on("\r\n").join(
            Iterables.transform(sieveRepository.listScripts(session.getUser()), new Function<ScriptSummary, String>() {
                public String apply(ScriptSummary scriptSummary) {
                    return '"' + scriptSummary.getName() + '"' + (scriptSummary.isActive() ? " ACTIVE" : "");
                }
            }));
        if (Strings.isNullOrEmpty(list)) {
            return "OK";
        } else {
            return list + "\r\nOK";
        }
    }

    @Override
    public String putScript(final Session session, final String name, final String content) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                sieveRepository.putScript(session.getUser(), name, content);
                return manageWarnings(parser.parse(content));
            }
        }, session);
    }

    @Override
    public String renameScript(final Session session, final String oldName, final String newName) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                sieveRepository.renameScript(session.getUser(), oldName, newName);
                return "OK";
            }
        }, session);
    }

    @Override
    public String setActive(final Session session, final String name) {
        return handleCommandExecution(new CommandWrapper() {
            public String execute() throws ManageSieveException, SieveRepositoryException {
                authenticationCheck(session);
                sieveRepository.setActive(session.getUser(), name);
                return "OK";
            }
        }, session);
    }

    @Override
    public String noop(String tag) {
        if(Strings.isNullOrEmpty(tag)) {
            return "OK \"NOOP completed\"";
        }
        return "OK " + taggify(tag) + " \"DONE\"";
    }

    @Override
    public String chooseMechanism(Session session, String mechanism) {
        try {
            if (Strings.isNullOrEmpty(mechanism)) {
                return "NO ManageSieve syntax is incorrect : You must specify a SASL mechanism as an argument of AUTHENTICATE command";
            }
            String unquotedMechanism = ParserUtils.unquote(mechanism);
            SupportedMechanism supportedMechanism = SupportedMechanism.retrieveMechanism(unquotedMechanism);

            session.setChoosedAuthenticationMechanism(supportedMechanism);
            session.setState(Session.State.AUTHENTICATION_IN_PROGRESS);
            AuthenticationProcessor authenticationProcessor = authenticationProcessorMap.get(supportedMechanism);
            return authenticationProcessor.initialServerResponse(session);
        } catch (UnknownSaslMechanism unknownSaslMechanism) {
            return "NO " + unknownSaslMechanism.getMessage();
        }
    }

    @Override
    public String authenticate(Session session, String suppliedData) {
        try {
            SupportedMechanism currentAuthenticationMechanism = session.getChoosedAuthenticationMechanism();
            AuthenticationProcessor authenticationProcessor = authenticationProcessorMap.get(currentAuthenticationMechanism);
            String authenticatedUsername = authenticationProcessor.isAuthenticationSuccesfull(session, suppliedData);
            if (authenticatedUsername != null) {
                session.setUser(authenticatedUsername);
                session.setState(Session.State.AUTHENTICATED);
                return "OK authentication successfull";
            } else {
                session.setState(Session.State.UNAUTHENTICATED);
                session.setUser(null);
                return "NO authentication failed";
            }
        } catch (AuthenticationException e) {
            return "NO Authentication failed with " + e.getCause().getClass() + " : " + e.getMessage();
        } catch (SyntaxException e) {
            return "NO ManageSieve syntax is incorrect : " + e.getMessage();
        }
    }

    @Override
    public String unauthenticate(Session session) {
        if (session.isAuthenticated()) {
            session.setState(Session.State.UNAUTHENTICATED);
            session.setUser(null);
            return "OK";
        } else {
            return "NO UNAUTHENTICATE command must be issued in authenticated state";
        }
    }

    @Override
    public void logout() throws SessionTerminatedException {
        throw new SessionTerminatedException();
    }

    @Override
    public String startTLS(Session session) {
        if (session.getState() == Session.State.UNAUTHENTICATED) {
            if (session.isSslEnabled()) {
                return "NO You can't enable two time SSL encryption";
            }
            session.setState(Session.State.SSL_NEGOCIATION);
            return "OK";
        } else {
            return "NO command STARTTLS is issued in the wrong state. It must be issued as you are unauthenticated";
        }
    }

    private String handleCommandExecution(CommandWrapper commandWrapper, Session session) {
        try {
            return commandWrapper.execute();
        } catch (AuthenticationException e) {
            return "NO Authentication failed with " + e.getCause().getClass() + " : " + e.getMessage();
        } catch (QuotaExceededException ex) {
            return "NO (QUOTA/MAXSIZE) \"Quota exceeded\"";
        } catch (AuthenticationRequiredException ex) {
            return "NO";
        } catch (DuplicateException ex) {
            return "NO (ALREADYEXISTS) \"A script with that name already exists\"";
        } catch (ScriptNotFoundException ex) {
            return "NO (NONEXISTENT) \"There is no script by that name\"";
        } catch (IsActiveException ex) {
            return "NO (ACTIVE) \"You may not delete an active script\"";
        }  catch (UserNotFoundException e) {
            return "NO : Invalid user " + session.getUser();
        } catch (StorageException e) {
            return "NO : Storage Exception : " + e.getMessage();
        } catch (SyntaxException e) {
            return sanitizeString("NO \"Syntax Error: " + e.getMessage() + "\"");
        } catch (ManageSieveException e) {
            return sanitizeString("NO \"ManageSieveException: " + e.getMessage() + "\"");
        } catch (SieveRepositoryException e) {
            return sanitizeString("NO \"SieveRepositoryException: " + e.getMessage() + "\"");
        }
    }

    protected void authenticationCheck(Session session) throws AuthenticationRequiredException {
        if (!session.isAuthenticated()) {
            throw new AuthenticationRequiredException();
        }
    }

    private String buildExtensions(SieveParser parser) {
        return Joiner.on(' ').join(parser.getExtensions()).trim();
    }

    private String taggify(String tag) {
        String sanitizedTag = ParserUtils.unquote(tag.trim());
        return "(TAG {" + sanitizedTag.length() + "}\r\n" + sanitizedTag + ")";
    }

    private Map<Capabilities, String> precomputedCapabilitiesBase(SieveParser parser) {
        String extensions = buildExtensions(parser);
        Map<Capabilities, String> capabilitiesBase = new HashMap<Capabilities, String>();
        capabilitiesBase.put(Capabilities.IMPLEMENTATION, IMPLEMENTATION_DESCRIPTION);
        capabilitiesBase.put(Capabilities.VERSION, MANAGE_SIEVE_VERSION);
        capabilitiesBase.put(Capabilities.SASL, constructSaslSupportedAuthenticationMechanisms());
        capabilitiesBase.put(Capabilities.STARTTLS, null);
        if (!extensions.isEmpty()) {
            capabilitiesBase.put(Capabilities.SIEVE, extensions);
        }
        return capabilitiesBase;
    }

    private String constructSaslSupportedAuthenticationMechanisms() {
        return Joiner.on(' ')
            .join(Lists.transform(
                Arrays.asList(SupportedMechanism.values()),
                new Function<SupportedMechanism, String>() {
                    public String apply(SupportedMechanism supportedMechanism) {
                        return supportedMechanism.toString();
                    }
                }));
    }

    private String sanitizeString(String message) {
        return Joiner.on("\r\n").join(Splitter.on('\n').split(message));
    }
}