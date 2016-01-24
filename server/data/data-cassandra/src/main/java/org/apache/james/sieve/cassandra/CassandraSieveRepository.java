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

package org.apache.james.sieve.cassandra;

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.incr;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static com.datastax.driver.core.querybuilder.QueryBuilder.set;
import static com.datastax.driver.core.querybuilder.QueryBuilder.update;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import org.apache.commons.io.IOUtils;

import org.apache.james.sieve.cassandra.tables.CassandraSieveClusterQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveQuotaTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveSpaceTable;
import org.apache.james.sieve.cassandra.tables.CassandraSieveTable;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.DuplicateException;
import org.apache.james.sieverepository.api.exception.IsActiveException;
import org.apache.james.sieverepository.api.exception.QuotaExceededException;
import org.apache.james.sieverepository.api.exception.QuotaNotFoundException;
import org.apache.james.sieverepository.api.exception.ScriptNotFoundException;
import org.apache.james.sieverepository.api.exception.StorageException;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


public class CassandraSieveRepository implements SieveRepository {
    private Session session;

    @Inject
    @Resource
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public void haveSpace(String user, String name, long size) throws QuotaExceededException, StorageException {
        try {
            haveSpace(user, size - sizeOfScript(user, name));
        } catch (ScriptNotFoundException e) {
            haveSpace(user, size);
        }
    }

    private void haveSpace(String user, long size) throws QuotaExceededException {
        try {
            if (currentQuota(user) < spaceUsedBy(user) + size) {
                throw new QuotaExceededException();
            }
        } catch (QuotaNotFoundException e) {
            // No quota
        }
    }

    private long spaceUsedBy(String user) {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveSpaceTable.SPACE_USED)
                                .from(CassandraSieveSpaceTable.TABLE_NAME)
                                .where(eq(CassandraSieveSpaceTable.USER_NAME, user))
                ).one()
        ).map(row -> row.getLong(CassandraSieveSpaceTable.SPACE_USED))
                .orElse(0L);
    }

    private long sizeOfScript(String user, String name) throws StorageException, ScriptNotFoundException {
        try {
            return IOUtils.toString(getScript(user, name)).length();
        } catch (IOException e) {
            throw new StorageException();
        }
    }

    @Override
    public void putScript(String user, String name, String content) throws QuotaExceededException, StorageException {
        long scriptSize = content.length();

        // throw QuotaExceededException if there is not enough space
        haveSpace(user, name, scriptSize);

        long spaceUsed;
        try {
            spaceUsed = scriptSize - sizeOfScript(user, name);
        } catch (ScriptNotFoundException e) {
            spaceUsed = scriptSize;
        }

        session.execute(
                update(CassandraSieveTable.TABLE_NAME)
                        .with(set(CassandraSieveTable.SCRIPT_CONTENT, content))
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
        );

        if (spaceUsed != 0) {
            session.execute(
                    update(CassandraSieveSpaceTable.TABLE_NAME)
                            .with(incr(CassandraSieveSpaceTable.SPACE_USED, spaceUsed))
                            .where(eq(CassandraSieveSpaceTable.USER_NAME, user))
            );
        }
    }

    @Override
    public List<ScriptSummary> listScripts(String user) {
        return session.execute(
                select(CassandraSieveTable.SCRIPT_NAME, CassandraSieveTable.IS_ACTIVE)
                        .from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
        ).all().stream().map(
                rs -> new ScriptSummary(
                        rs.getString(CassandraSieveTable.SCRIPT_NAME),
                        rs.getBool(CassandraSieveTable.IS_ACTIVE)
                )
        ).collect(Collectors.toList());
    }

    @Override
    public InputStream getActive(String user) throws ScriptNotFoundException {
        return IOUtils.toInputStream(
                Optional.ofNullable(
                        session.execute(
                                select(CassandraSieveTable.SCRIPT_CONTENT)
                                        .from(CassandraSieveTable.TABLE_NAME)
                                        .where(eq(CassandraSieveTable.USER_NAME, user))
                                        .and(eq(CassandraSieveTable.IS_ACTIVE, true))
                        ).one()
                ).orElseThrow(ScriptNotFoundException::new)
                        .getString(CassandraSieveTable.SCRIPT_CONTENT)
        );
    }

    @Override
    public void setActive(String user, String name) throws ScriptNotFoundException {
        if (!name.equals("") && !scriptExists(user, name)) {
            throw new ScriptNotFoundException();
        }

        Optional<String> oldActive = getActiveName(user);
        if (oldActive.isPresent()) {
            if (name.equals(oldActive.get())) {
                return;
            }
            session.execute(
                    update(CassandraSieveTable.TABLE_NAME)
                            .with(set(CassandraSieveTable.IS_ACTIVE, false))
                            .where(eq(CassandraSieveTable.USER_NAME, user))
                            .and(eq(CassandraSieveTable.SCRIPT_NAME, oldActive.get()))
            );
        }
        if (!name.equals("")) { // not switching off active script
            session.execute(
                    update(CassandraSieveTable.TABLE_NAME)
                            .with(set(CassandraSieveTable.IS_ACTIVE, true))
                            .where(eq(CassandraSieveTable.USER_NAME, user))
                            .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
            );
        }
    }

    @Override
    public InputStream getScript(String user, String name) throws ScriptNotFoundException {
        return  IOUtils.toInputStream(
                Optional.ofNullable(
                        session.execute(
                                select(CassandraSieveTable.SCRIPT_CONTENT)
                                        .from(CassandraSieveTable.TABLE_NAME)
                                        .where(eq(CassandraSieveTable.USER_NAME, user))
                                        .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
                        ).one()
                ).orElseThrow(ScriptNotFoundException::new)
                        .getString(CassandraSieveTable.SCRIPT_CONTENT)
        );
    }

    @Override
    public void deleteScript(String user, String name) throws ScriptNotFoundException, IsActiveException {
        if (!scriptExists(user, name)) {
            throw new ScriptNotFoundException();
        }

        Optional<String> activeName = getActiveName(user);
        if (activeName.isPresent() && name.equals(activeName.get())) {
            throw new IsActiveException();
        }

        session.execute(
                delete()
                        .from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
        );
    }

    @Override
    public void renameScript(String user, String oldName, String newName) throws ScriptNotFoundException, DuplicateException {
        if (scriptExists(user, newName)) {
            throw new DuplicateException();
        }

         Row oldScript = Optional.ofNullable(
                session.execute(
                        select(CassandraSieveTable.SCRIPT_CONTENT, CassandraSieveTable.IS_ACTIVE)
                                .from(CassandraSieveTable.TABLE_NAME)
                                .where(eq(CassandraSieveTable.USER_NAME, user))
                                .and(eq(CassandraSieveTable.SCRIPT_NAME, oldName))
                ).one()
        ).orElseThrow(ScriptNotFoundException::new);

        session.execute(
                insertInto(CassandraSieveTable.TABLE_NAME)
                        .value(CassandraSieveTable.USER_NAME, user)
                        .value(CassandraSieveTable.SCRIPT_NAME, newName)
                        .value(CassandraSieveTable.SCRIPT_CONTENT, oldScript.getString(CassandraSieveTable.SCRIPT_CONTENT))
                        .value(CassandraSieveTable.IS_ACTIVE, oldScript.getBool(CassandraSieveTable.IS_ACTIVE))
        );

        session.execute(
                delete().from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, oldName))
        );
    }

    @Override
    public boolean hasQuota() {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveClusterQuotaTable.NAME)
                                .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                                .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
                ).one()
        ).isPresent();
    }

    @Override
    public long getQuota() throws QuotaNotFoundException {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveClusterQuotaTable.VALUE)
                                .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                                .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
                ).one()
        ).orElseThrow(QuotaNotFoundException::new)
                .getLong(CassandraSieveClusterQuotaTable.VALUE);
    }

    @Override
    public void setQuota(long quota) {
        session.execute(
                update(CassandraSieveClusterQuotaTable.TABLE_NAME)
                        .with(set(CassandraSieveClusterQuotaTable.VALUE,quota))
                        .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
        );
    }

    @Override
    public void removeQuota() throws QuotaNotFoundException {
        if (!hasQuota()) {
            throw new QuotaNotFoundException();
        }

        session.execute(
                delete()
                        .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME))
        );
    }

    @Override
    public boolean hasQuota(String user) {
        return hasOwnQuota(user) || hasQuota();
    }


    @Override
    public long getQuota(String user) throws QuotaNotFoundException {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveQuotaTable.QUOTA)
                                .from(CassandraSieveQuotaTable.TABLE_NAME)
                                .where(eq(CassandraSieveQuotaTable.USER_NAME, user))
                ).one()
        ).orElseThrow(QuotaNotFoundException::new)
                .getLong(CassandraSieveQuotaTable.QUOTA);
    }

    @Override
    public void setQuota(String user, long quota) {
        session.execute(
                update(CassandraSieveQuotaTable.TABLE_NAME)
                        .with(set(CassandraSieveQuotaTable.QUOTA,quota))
                        .where(eq(CassandraSieveQuotaTable.USER_NAME, user))
        );
    }

    @Override
    public void removeQuota(String user) throws QuotaNotFoundException {
        if (!hasOwnQuota(user)) {
            throw new QuotaNotFoundException();
        }

        session.execute(
                delete().from(CassandraSieveQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveQuotaTable.USER_NAME, user))
        );
    }

    private boolean hasOwnQuota(String user) {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveQuotaTable.QUOTA)
                                .from(CassandraSieveQuotaTable.TABLE_NAME)
                                .where(eq(CassandraSieveQuotaTable.USER_NAME, user))
                ).one()
        ).isPresent();
    }

    private Optional<String> getActiveName(String user) {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveTable.SCRIPT_NAME)
                                .from(CassandraSieveTable.TABLE_NAME)
                                .where(eq(CassandraSieveTable.USER_NAME, user))
                                .and(eq(CassandraSieveTable.IS_ACTIVE, true))
                ).one()
        ).map(row -> row.getString(CassandraSieveTable.SCRIPT_NAME));
    }

    private boolean scriptExists(String user, String name) {
        return Optional.ofNullable(
                session.execute(
                        select(CassandraSieveTable.SCRIPT_CONTENT)
                                .from(CassandraSieveTable.TABLE_NAME)
                                .where(eq(CassandraSieveTable.USER_NAME, user))
                                .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
                ).one()
        ).isPresent();
    }

    private long currentQuota(String user) throws QuotaNotFoundException {
        try {
            return getQuota(user);
        } catch (QuotaNotFoundException e) {
            return getQuota();
        }
    }
}
