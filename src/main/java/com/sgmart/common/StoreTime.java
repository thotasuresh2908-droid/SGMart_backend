package com.sgmart.common;

import java.time.ZoneId;

/** The store runs on India time; the database stores UTC. */
public final class StoreTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");

    private StoreTime() {
    }
}
