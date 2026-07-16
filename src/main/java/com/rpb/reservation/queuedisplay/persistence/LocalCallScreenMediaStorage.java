package com.rpb.reservation.queuedisplay.persistence;

import com.rpb.reservation.queuedisplay.application.CallScreenMediaStorage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.PathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

@Component
class LocalCallScreenMediaStorage implements CallScreenMediaStorage {
    private final Path root;

    LocalCallScreenMediaStorage(@Value("${rpb.call-screen-media.storage-root:target/call-screen-media}") String storageRoot) {
        this.root = Path.of(storageRoot).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, InputStream input) throws IOException {
        Path target = resolve(storageKey);
        Files.createDirectories(target.getParent());
        Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public Resource load(String storageKey) {
        return new PathResource(resolve(storageKey));
    }

    private Path resolve(String storageKey) {
        Path resolved = root.resolve(storageKey).normalize();
        if (!resolved.startsWith(root)) {
            throw new IllegalArgumentException("invalid_storage_key");
        }
        return resolved;
    }
}
