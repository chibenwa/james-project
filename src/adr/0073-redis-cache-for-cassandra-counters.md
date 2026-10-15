# 73. Redis cache for current quotas and mailbox counters

Date: 2026-10-15

## Status

Accepted (lazy consensus).

Implemented.

Complements [ADR-23](0023-cassandra-mailbox-counters-inconsistencies.md) and [ADR-28](0028-Recompute-mailbox-quotas.md).

## Context

The distributed server stores current quotas and mailbox counters (message count, unseen count) in Cassandra counters.

On an unstable Cassandra cluster these counters drift, and recomputation tasks fail to make them converge onto the values
of the source of truth (`imapUidTable` and associated message metadata):

 - Counters are not idempotent: upon error we are stuck over or under counting.
 - Counters offer no `SET` semantic: we can only add or remove. Recomputation thus reads the current value then adds an
 offset. Counters store a set of increments per node: if this set is inconsistent within the cluster, the offset is
 computed from a wrong value and the counter stays wrong.

Not relying on counters at all would expose us to data races. Lightweight transactions are out of the question for
performance reasons.

## Decision

Optionally cache current quotas and mailbox counters in Redis, the source of truth remaining the message metadata stored
in Cassandra:

 - Upon cache miss, values are recomputed from the source of truth and stored in Redis with a TTL (configurable, defaults
 to one day) plus an optional random jitter. Any drift is thus bounded in time.
 - Cached values are maintained upon writes: current quotas via the existing `ListeningCurrentQuotaUpdater`, mailbox
 counters via a dedicated `RedisMailboxCountersListener`. Increments are only applied to cached values.
 - Recomputation tasks atomically replace cached values, which allows warming up the cache.

Redis offers the `SET` semantic Cassandra counters lack, and losing cached values is harmless.

Correctness safeguards:

 - Each entry carries a generation bumped by every write. A recomputed value is only stored if the generation did not
 change during the recomputation, so that concurrent increments are not lost. This is done atomically via Lua scripts.
 - Partial or unreadable entries are treated as cache misses.
 - Increments are never retried, as retrying a counter increment risks double counting. Upon failure the entry is
 invalidated when possible.

In order to provide read-after-write consistency for mailbox counters (IMAP `STATUS` after `APPEND`...), the distributed
event bus can execute some group listeners synchronously, on the node dispatching the event
(`james.eventbus.synchronous.listener.groups` JVM property). This mechanism is ported from TMail.

Rollbacks and Redis outages are expected to be as transparent as possible:

 - Redis commands exceeding a configurable timeout are treated as an outage.
 - Optionally, legacy Cassandra counters are maintained alongside Redis (best effort). They are then read upon Redis
 outages, and rolling back is immediate. Otherwise legacy counters are no longer written, values are recomputed from
 the source of truth upon Redis outages, and rolling back requires running recomputation tasks.
 - Writes never fail because of Redis.

## Consequences

 - Current quotas and mailbox counters converge onto the source of truth at least once per TTL, without operator action.
 - Redis becomes a dependency of the distributed server when the feature is enabled (opt-in, disabled by default).
 - Cache misses imply reading all the message metadata of a mailbox (mailbox counters) or of all the mailboxes of a user
 (current quotas, 4 mailboxes being read concurrently). Concurrent recomputations of the same entry are shared within a
 James node.
 - After a Redis outage during which Redis kept its data, invalidations might have failed and some cached values might be
 stale until they expire. Flushing Redis after such outages, or running it without persistence, avoids this.
 - Without synchronous execution of the mailbox counters listener, counters are eventually consistent.

## Alternatives

 - Lightweight transactions on Cassandra: too expensive.
 - Read before write with Cassandra regular columns: subject to data races.
 - Circuit breaker upon Redis failures: postponed, a timeout is enough for a first version.

## References

 - [ADR-23: Cassandra mailbox counters inconsistencies](0023-cassandra-mailbox-counters-inconsistencies.md)
 - [ADR-28: Recompute mailbox quotas](0028-Recompute-mailbox-quotas.md)
 - [Configuration documentation](../../docs/modules/servers/pages/distributed/configure/redis.adoc)
