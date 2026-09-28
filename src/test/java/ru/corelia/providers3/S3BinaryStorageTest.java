package ru.corelia.providers3;

import static org.junit.jupiter.api.Assertions.*;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import ru.corelia.provider.model.StorageReference;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;

class S3BinaryStorageTest {
    @Test
    void exposesMetadataRangeAndDeletionUsingLogicalReference() throws Exception {
        List<Object> requests = new CopyOnWriteArrayList<>();
        S3Client client = (S3Client) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {S3Client.class}, (proxy, method, arguments) -> {
                    return switch (method.getName()) {
                        case "getObject" -> {
                            GetObjectRequest.Builder builder = GetObjectRequest.builder();
                            ((Consumer<GetObjectRequest.Builder>) arguments[0]).accept(builder);
                            requests.add(builder.build());
                            yield new ResponseInputStream<>(GetObjectResponse.builder().build(), new ByteArrayInputStream(new byte[] {2, 3}));
                        }
                        case "headObject" -> HeadObjectResponse.builder().contentLength(2L).contentType("text/plain").metadata(java.util.Map.of("sha256", "a".repeat(64))).build();
                        case "deleteObject" -> {
                            DeleteObjectRequest.Builder builder = DeleteObjectRequest.builder();
                            ((Consumer<DeleteObjectRequest.Builder>) arguments[0]).accept(builder);
                            requests.add(builder.build());
                            yield DeleteObjectResponse.builder().build();
                        }
                        case "putObject" -> PutObjectResponse.builder().build();
                        case "serviceName" -> "S3";
                        case "close" -> null;
                        default -> throw new UnsupportedOperationException(method.getName());
                    };
                });
        S3BinaryStorage storage = new S3BinaryStorage(client, "attachments");
        StorageReference reference = new StorageReference("corelia-blob://9f0015f6-a00c-4dbd-a69e-8010d6e1b733");

        assertEquals("a".repeat(64), storage.metadata(reference, null).checksum());
        assertArrayEquals(new byte[] {2, 3}, storage.read(reference, 2, 2, null).readAllBytes());
        storage.delete(reference, null);

        GetObjectRequest range = requests.stream().filter(GetObjectRequest.class::isInstance)
                .map(GetObjectRequest.class::cast).findFirst().orElseThrow();
        assertEquals("bytes=2-3", range.range());
        assertTrue(requests.stream().anyMatch(value -> value.getClass().getSimpleName().equals("DeleteObjectRequest")));
    }
}
