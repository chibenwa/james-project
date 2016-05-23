package org.apache.james.mpt.managesieve.cassandra.host;

import org.apache.james.mpt.host.JamesManageSieveHostSystem;
import org.apache.james.sieve.cassandra.CassandraSieveRepository;
import org.apache.james.sieve.cassandra.CassandraSieveRepositoryModule;
import org.apache.james.sieverepository.api.SieveRepository;
import org.apache.james.user.memory.MemoryUsersRepository;
import org.apache.james.backends.cassandra.CassandraCluster;

public class CassandraHostSystem extends JamesManageSieveHostSystem {
    private static CassandraCluster cassandra = CassandraCluster.create(new CassandraSieveRepositoryModule());

    public CassandraHostSystem() throws Exception {
        super(new MemoryUsersRepository(), createSieveRepository());
    }

    protected static SieveRepository createSieveRepository() throws Exception {
        return new CassandraSieveRepository(cassandra.getConf());
    }

    @Override
    protected void resetData() throws Exception {
        cassandra.clearAllTables();
    }
}
