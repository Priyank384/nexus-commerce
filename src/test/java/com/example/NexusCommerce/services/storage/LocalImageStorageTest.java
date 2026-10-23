package com.example.nexusCommerce.services.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalImageStorageTest {

    @TempDir
    Path directory;

    @Test
    void store_writesTheBytesAndReturnsAServedUrl() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());

        String url = storage.store(new byte[] {1, 2, 3}, ".png", "image/png");

        assertTrue(url.startsWith(LocalImageStorage.URL_PREFIX));
        assertTrue(url.endsWith(".png"));
        Path written = directory.resolve(url.substring(LocalImageStorage.URL_PREFIX.length()));
        assertArrayEquals(new byte[] {1, 2, 3}, Files.readAllBytes(written));
    }

    @Test
    void delete_removesAnObjectItOwns() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        String url = storage.store(new byte[] {1}, ".png", "image/png");

        storage.delete(url);

        Path written = directory.resolve(url.substring(LocalImageStorage.URL_PREFIX.length()));
        assertFalse(Files.exists(written));
    }

    @Test
    void delete_ignoresUrlsFromElsewhereAndTraversalAttempts() throws Exception {
        LocalImageStorage storage = new LocalImageStorage(directory.toString());
        Path outside = directory.getParent().resolve("outside.png");
        Files.write(outside, new byte[] {9});

        storage.delete("https://picsum.photos/seed/x/600/800");
        storage.delete(LocalImageStorage.URL_PREFIX + "../outside.png");
        storage.delete(null);

        assertTrue(Files.exists(outside));
        Files.deleteIfExists(outside);
    }

    @Test
    void store_createsTheDirectoryOnFirstUpload() {
        Path nested = directory.resolve("a/b/c");
        LocalImageStorage storage = new LocalImageStorage(nested.toString());

        storage.store(new byte[] {1}, ".webp", "image/webp");

        assertTrue(Files.isDirectory(nested));
        assertEquals(1, nested.toFile().listFiles().length);
    }
}
