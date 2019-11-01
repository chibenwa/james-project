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

package org.apache.james.probe;

import java.util.List;
import java.util.Map;

import org.apache.james.core.Domain;
import org.apache.james.rrt.lib.Mappings;

public interface DataProbe {

    class FluentDataProbe {

        private final DataProbe dataProbe;

        private FluentDataProbe(DataProbe dataProbe) {
            this.dataProbe = dataProbe;
        }

        public DataProbe getDataProbe() {
            return dataProbe;
        }

        public FluentDataProbe addUser(String userName, String password) throws Exception {
            dataProbe.addUser(userName, password);
            return this;
        }

        public FluentDataProbe addDomain(String domain) throws Exception {
            dataProbe.addDomain(Domain.of(domain));
            return this;
        }

        public FluentDataProbe addDomain(Domain domain) throws Exception {
            dataProbe.addDomain(domain);
            return this;
        }
    }

    default FluentDataProbe fluent() {
        return new FluentDataProbe(this);
    }

    void addUser(String userName, String password) throws Exception;

    void removeUser(String username) throws Exception;

    void setPassword(String userName, String password) throws Exception;

    String[] listUsers() throws Exception;

    void addDomain(Domain domain) throws Exception;

    boolean containsDomain(Domain domain) throws Exception;

    Domain getDefaultDomain() throws Exception;

    void removeDomain(Domain domain) throws Exception;

    List<Domain> listDomains() throws Exception;

    Map<String, Mappings> listMappings() throws Exception;

    Mappings listUserDomainMappings(String user, String domain) throws Exception;

    void addAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception;

    void removeAddressMapping(String fromUser, String fromDomain, String toAddress) throws Exception;

    void addRegexMapping(String user, String domain, String regex) throws Exception;

    void removeRegexMapping(String user, String domain, String regex) throws Exception;

    void addDomainAliasMapping(String aliasDomain, String deliveryDomain) throws Exception;
}