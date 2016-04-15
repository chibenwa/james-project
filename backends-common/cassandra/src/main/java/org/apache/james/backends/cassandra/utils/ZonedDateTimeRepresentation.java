

package org.apache.james.backends.cassandra.utils;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

public class ZonedDateTimeRepresentation {

    private final long msSinceEpoch;
    private final String serializedZoneId;

    public static ZonedDateTimeRepresentation fromZonedDateTime(ZonedDateTime zonedDateTime) {
        return new ZonedDateTimeRepresentation(zonedDateTime.toInstant().toEpochMilli(), zonedDateTime.getZone().getId());
    }

    public ZonedDateTimeRepresentation(long msSinceEpochUTC, String serializedZoneId) {
        this.msSinceEpoch = msSinceEpochUTC;
        this.serializedZoneId = serializedZoneId;
    }

    public long getMsSinceEpoch() {
        return msSinceEpoch;
    }

    public String getSerializedZoneId() {
        return serializedZoneId;
    }

    public ZonedDateTime convertToZonedDateTime() {
        return ZonedDateTime.ofInstant(Instant.ofEpochMilli(msSinceEpoch), ZoneId.of(serializedZoneId));
    }
}
