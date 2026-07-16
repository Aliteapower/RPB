package com.rpb.reservation.queuedisplay.application;

import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;

public interface CallScreenMediaStorage {
    void store(String storageKey, InputStream input) throws IOException;

    Resource load(String storageKey);
}
