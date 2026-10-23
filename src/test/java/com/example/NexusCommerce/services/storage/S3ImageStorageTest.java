package com.example.nexusCommerce.services.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.nexusCommerce.exceptions.InvalidRequestException;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class S3ImageStorageTest {

    @Mock
    private S3Client client;

    private S3ImageStorage storage;

    @BeforeEach
    void setUp() {
        storage = new S3ImageStorage(client, "plain-thread", "products", "https://cdn.example.com/");
    }

    @Test
    void store_uploadsUnderThePrefixAndReturnsThePublicUrl() {
        String url = storage.store(new byte[] {1, 2}, ".png", "image/png");

        assertTrue(url.startsWith("https://cdn.example.com/products/"));
        assertTrue(url.endsWith(".png"));

        ArgumentCaptor<PutObjectRequest> request = ArgumentCaptor.forClass(PutObjectRequest.class);
        verify(client).putObject(request.capture(), any(RequestBody.class));
        assertEquals("plain-thread", request.getValue().bucket());
        assertTrue(request.getValue().key().startsWith("products/"));
        assertEquals("image/png", request.getValue().contentType());
        assertEquals("public, max-age=31536000, immutable", request.getValue().cacheControl());
    }

    @Test
    void store_wrapsSdkFailures() {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("no such bucket"));

        assertThrows(InvalidRequestException.class,
                () -> storage.store(new byte[] {1}, ".png", "image/png"));
    }

    @Test
    void delete_removesOnlyObjectsUnderThePublicBaseUrl() {
        storage.delete("https://cdn.example.com/products/abc.png");
        storage.delete("https://picsum.photos/seed/x/600/800");
        storage.delete("https://cdn.example.com/products/../../etc/passwd");
        storage.delete(null);

        ArgumentCaptor<DeleteObjectRequest> request = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(request.capture());
        assertEquals("plain-thread", request.getValue().bucket());
        assertEquals("products/abc.png", request.getValue().key());
    }

    @Test
    void delete_swallowsProviderFailures() {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("network down"));

        storage.delete("https://cdn.example.com/products/abc.png");

        verify(client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void delete_doesNothingForForeignUrls() {
        storage.delete("https://picsum.photos/seed/x/600/800");

        verifyNoInteractions(client);
    }

    @Test
    void constructor_rejectsAMissingBucketOrPublicUrl() {
        assertThrows(IllegalStateException.class,
                () -> new S3ImageStorage(client, "", "products", "https://cdn.example.com"));
        assertThrows(IllegalStateException.class,
                () -> new S3ImageStorage(client, "bucket", "products", ""));
    }
}
