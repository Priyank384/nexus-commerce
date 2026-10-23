package com.example.nexusCommerce.services.storage;

/**
 * Where uploaded product images live. Implementations return the URL persisted on the
 * product/gallery row, so callers never learn whether the bytes went to disk or a bucket.
 */
public interface ImageStorage {

    String store(byte[] content, String extension, String contentType);

    /** Best-effort cleanup of an object this storage owns; URLs from elsewhere are ignored. */
    void delete(String url);
}
