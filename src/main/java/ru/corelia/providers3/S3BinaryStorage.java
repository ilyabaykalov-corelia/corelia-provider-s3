package ru.corelia.providers3;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import ru.corelia.auth.AuthContext;
import ru.corelia.config.CoreliaRuntimeConfig;
import ru.corelia.provider.BinaryStorage;
import ru.corelia.provider.model.BinaryStoreRequest;
import ru.corelia.provider.model.StorageReference;
import ru.corelia.provider.model.StoredFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/** S3-compatible adapter: physical object key остаётся внутренней деталью provider-а. */
public final class S3BinaryStorage implements BinaryStorage {
    private static final String REFERENCE_PREFIX = "corelia-blob://";
    private final S3Client client;
    private final String bucket;

    public S3BinaryStorage(CoreliaRuntimeConfig config) {
        bucket = required(config, "CORELIA_S3_BUCKET");
        String endpoint = required(config, "CORELIA_S3_ENDPOINT");
        client = S3Client.builder().endpointOverride(URI.create(endpoint)).region(Region.of(config.value("CORELIA_S3_REGION", "us-east-1")))
                .forcePathStyle(true).credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(
                        required(config, "CORELIA_S3_ACCESS_KEY"), required(config, "CORELIA_S3_SECRET_KEY")))).build();
    }

    @Override public StoredFile store(BinaryStoreRequest request, InputStream content, AuthContext auth) {
        String id = UUID.randomUUID().toString();
        client.putObject(builder -> builder.bucket(bucket).key(key(id)).contentType(request.contentType()).metadata(java.util.Map.of("sha256", request.checksum())),
                RequestBody.fromInputStream(content, request.size()));
        return new StoredFile(new StorageReference(REFERENCE_PREFIX + id), request.checksum(), request.size(), request.contentType());
    }

    @Override public InputStream read(StorageReference reference, AuthContext auth) {
        return client.getObject(builder -> builder.bucket(bucket).key(key(id(reference))));
    }

    private static String key(String id) { return "blobs/" + id; }
    private static String id(StorageReference reference) {
        String value = reference.value();
        if (!value.startsWith(REFERENCE_PREFIX)) throw new IllegalArgumentException("Storage reference не принадлежит S3 provider");
        return UUID.fromString(value.substring(REFERENCE_PREFIX.length())).toString();
    }
    private static String required(CoreliaRuntimeConfig config, String name) {
        String value = config.value(name);
        if (value.isBlank()) throw new IllegalStateException("Не задан " + name + " для S3 provider");
        return value;
    }
}
