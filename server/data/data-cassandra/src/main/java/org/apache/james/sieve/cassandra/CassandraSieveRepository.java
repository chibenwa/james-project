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

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import org.apache.commons.io.IOUtils;
import org.apache.james.sieve.cassandra.tables.*;
import org.apache.james.sieverepository.api.ScriptSummary;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.sieverepository.api.exception.*;

import javax.annotation.Resource;
import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

public class CassandraSieveRepository implements SieveRepository {
    private Session session;

    @Inject
    @Resource
    public void setSession(Session session) {
        this.session = session;
    }

    @Override
    public void haveSpace(String user, String name, long size) throws QuotaExceededException {
        long quota;

        try {
            quota = getQuota(user);
        } catch (QuotaNotFoundException e) {
            try {
                quota = getQuota();
            } catch (QuotaNotFoundException ex) {
                return;
            }
        }

        System.out.println("quota:"+quota+"<"+spaceUsedBy(user)+"+"+size+"-"+sizeOfScript(user, name));
        if (quota < spaceUsedBy(user) + size - sizeOfScript(user, name)) {
            System.out.println("No space !!");
            throw new QuotaExceededException();
        }

    }

    private long spaceUsedBy(String user) {
        Row space = session.execute(
                select(CassandraSieveSpaceTable.SPACE_USED)
                    .from(CassandraSieveSpaceTable.TABLE_NAME)
                    .where(eq(CassandraSieveSpaceTable.USER_NAME, user))
        ).one();

        if (space==null) {
            System.out.println("spaceused null");
            return 0;
        } else {
            System.out.println("spaceused not null : "+space.getLong(CassandraSieveSpaceTable.SPACE_USED));
            return space.getLong(CassandraSieveSpaceTable.SPACE_USED);
        }
    }

    private long sizeOfScript(String user, String name) {
        try {
            return IOUtils.toString(getScript(user, name)).length();
        } catch (IOException | ScriptNotFoundException e) {
            return 0;
        }
    }

    @Override
    public void putScript(String user, String name, String content) throws QuotaExceededException {
        long scriptSize = content.length();

        // throw QuotaExceededException if there is not enough space
        haveSpace(user, name, scriptSize);

        long spaceUsed = scriptSize - sizeOfScript(user, name);
        session.execute(
                update(CassandraSieveTable.TABLE_NAME)
                        .with(set(CassandraSieveTable.SCRIPT_CONTENT, content))
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
        );


        System.out.println("size: "+scriptSize + " & " + sizeOfScript(user, name));
       // if (spaceUsed != 0) {
            session.execute(
                    "UPDATE " +
                    CassandraSieveSpaceTable.TABLE_NAME +
                    " SET "
                    + CassandraSieveSpaceTable.SPACE_USED + " = "
                    + CassandraSieveSpaceTable.SPACE_USED + " + " + spaceUsed
                    + " WHERE " + CassandraSieveSpaceTable.USER_NAME + " = '" + user + "';"
            );
      //  }
    }

    @Override
    public List<ScriptSummary> listScripts(String user) {
        String activeName;
        try {
            activeName = getActiveName(user);
        } catch (ScriptNotFoundException e) {
            activeName = null;
        }
        // lambda require final variables
        final String finalActiveName = activeName;

        return session.execute(
                select(CassandraSieveTable.SCRIPT_NAME, CassandraSieveTable.SCRIPT_CONTENT)
                        .from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
        ).all().stream().map(
                rs -> new ScriptSummary(
                        rs.getString(CassandraSieveTable.SCRIPT_NAME),
                        finalActiveName != null && finalActiveName.equals(rs.getString(CassandraSieveTable.SCRIPT_NAME))
                )
        ).collect(Collectors.toList());
    }

    @Override
    public InputStream getActive(String user) throws ScriptNotFoundException {
        Row script = session.execute(
                select(CassandraSieveActiveTable.SCRIPT_CONTENT)
                        .from(CassandraSieveActiveTable.TABLE_NAME)
                        .where(eq(CassandraSieveActiveTable.USER_NAME, user))
        ).one();

        if (script == null) {
            throw new ScriptNotFoundException();
        }

        return IOUtils.toInputStream(
                script.getString(CassandraSieveActiveTable.SCRIPT_CONTENT)
        );
    }

    @Override
    public void setActive(String user, String name) throws ScriptNotFoundException {
        // name is "" when switching off active script
        if (!name.equals("")) {
            // This throw a ScriptNotFoundException if the script does not exists
            String scriptContent = scriptContentIfExists(user, name);

            session.execute(
                    update(CassandraSieveActiveTable.TABLE_NAME)
                            .with(set(CassandraSieveActiveTable.SCRIPT_NAME, name))
                            .and(set(CassandraSieveActiveTable.SCRIPT_CONTENT, scriptContent))
                            .where(eq(CassandraSieveActiveTable.USER_NAME, user))
            );
        } else {
            session.execute(
                    delete()
                            .from(CassandraSieveActiveTable.TABLE_NAME)
                            .where(eq(CassandraSieveActiveTable.USER_NAME, user))
            );
        }
    }

    @Override
    public InputStream getScript(String user, String name) throws ScriptNotFoundException {
        Row script = session.execute(
                select(CassandraSieveTable.SCRIPT_CONTENT)
                        .from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, name))
        ).one();

        if (script == null) {
            throw new ScriptNotFoundException();
        }

        return IOUtils.toInputStream(
                script.getString(CassandraSieveTable.SCRIPT_CONTENT)
        );
    }

    @Override
    public void deleteScript(String user, String name) throws ScriptNotFoundException, IsActiveException {
        // Check that the script exists
        if (!scriptExists(user, name)) {
            throw new ScriptNotFoundException();
        }

        // Check that the script is not active
        try {
            if (name.equals(this.getActiveName(user))) {
                throw new IsActiveException();
            }
        } catch (ScriptNotFoundException e) {
            // No active script
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
        // make sure that we are not overwriting another script
        if (scriptExists(user, newName)) {
            throw new DuplicateException();
        }

        // this will throw a ScriptNotFoundException if the script does not exist
        String script_content = scriptContentIfExists(user, oldName);

        session.execute(
                delete().from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, oldName))
        );

        session.execute(
                insertInto(CassandraSieveTable.TABLE_NAME)
                        .value(CassandraSieveTable.USER_NAME, user)
                        .value(CassandraSieveTable.SCRIPT_NAME, newName)
                        .value(CassandraSieveTable.SCRIPT_CONTENT, script_content)
        );

        session.execute(
                update(CassandraSieveActiveTable.TABLE_NAME)
                        .with(set(CassandraSieveActiveTable.SCRIPT_NAME,oldName))
                        .where(eq(CassandraSieveActiveTable.USER_NAME, user))
        );
    }

    @Override
    public boolean hasQuota() {
        return session.execute(
                select(CassandraSieveClusterQuotaTable.NAME)
                        .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME)))
                .one() != null;
    }

    @Override
    public long getQuota() throws QuotaNotFoundException {
        Row quota = session.execute(
                select(CassandraSieveClusterQuotaTable.VALUE)
                        .from(CassandraSieveClusterQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME)))
                .one();

        if (quota == null) {
            throw new QuotaNotFoundException();
        }

        return quota.getLong(CassandraSieveClusterQuotaTable.VALUE);
    }

    @Override
    public void setQuota(long quota) {
        session.execute(
                update(CassandraSieveClusterQuotaTable.TABLE_NAME)
                        .with(set(CassandraSieveClusterQuotaTable.VALUE,quota))
                        .where(eq(CassandraSieveClusterQuotaTable.NAME, CassandraSieveClusterQuotaTable.DEFAULT_NAME)));
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
        return this.hasOwnQuota(user) || this.hasQuota();
    }


    @Override
    public long getQuota(String user) throws QuotaNotFoundException {
        Row quota = session.execute(
                select(CassandraSieveQuotaTable.QUOTA)
                        .from(CassandraSieveQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveQuotaTable.USER_NAME, user)))
                .one();

        if (quota != null) {
            return quota.getLong(CassandraSieveQuotaTable.QUOTA);
        } else {
            throw new QuotaNotFoundException();
           // return getQuota();
        }
    }

    @Override
    public void setQuota(String user, long quota) {
        session.execute(
                update(CassandraSieveQuotaTable.TABLE_NAME)
                        .with(set(CassandraSieveQuotaTable.QUOTA,quota))
                        .where(eq(CassandraSieveQuotaTable.USER_NAME, user)));
    }

    @Override
    public void removeQuota(String user) throws QuotaNotFoundException {
        if (!this.hasOwnQuota(user)) {
            throw new QuotaNotFoundException();
        }

        session.execute(
                delete().from(CassandraSieveQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveQuotaTable.USER_NAME, user))
        );
    }

    private boolean hasOwnQuota(String user) {
        Row quota = session.execute(
                select(CassandraSieveQuotaTable.QUOTA)
                        .from(CassandraSieveQuotaTable.TABLE_NAME)
                        .where(eq(CassandraSieveQuotaTable.USER_NAME, user))
        ).one();

        return quota != null && quota.getLong(CassandraSieveQuotaTable.QUOTA) != -1;
    }

    private String getActiveName(String user) throws ScriptNotFoundException {
        Row script = session.execute(
                select(CassandraSieveActiveTable.SCRIPT_NAME)
                        .from(CassandraSieveActiveTable.TABLE_NAME)
                        .where(eq(CassandraSieveActiveTable.USER_NAME, user))
        ).one();

        if (script == null) {
            throw new ScriptNotFoundException();
        }

        return script.getString(CassandraSieveActiveTable.SCRIPT_NAME);
    }

    private String scriptContentIfExists(String user, String name) throws ScriptNotFoundException {
        try {
            return session.execute(
                    select(CassandraSieveTable.SCRIPT_CONTENT)
                            .from(CassandraSieveTable.TABLE_NAME)
                            .where(eq(CassandraSieveTable.USER_NAME, user))
                            .and(eq(CassandraSieveTable.SCRIPT_NAME, name)))
                    .one().getString(CassandraSieveTable.SCRIPT_CONTENT);
        } catch (NullPointerException e) {
            throw new ScriptNotFoundException();
        }
    }

    private boolean scriptExists(String user, String name) {
        return session.execute(
                select(CassandraSieveTable.SCRIPT_CONTENT)
                        .from(CassandraSieveTable.TABLE_NAME)
                        .where(eq(CassandraSieveTable.USER_NAME, user))
                        .and(eq(CassandraSieveTable.SCRIPT_NAME, name)))
                .one() != null;
    }
}