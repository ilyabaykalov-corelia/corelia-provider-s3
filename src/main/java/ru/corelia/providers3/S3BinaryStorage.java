package ru.corelia.providers3;

import java.io.InputStream;
import java.net.URI;
import java.util.UUID;
import ru.corelia.auth.AuthContext;
import ru.corelia.config.CoreliaRuntimeConfig;
import ru.corelia.provider.BinaryStorage;
import ru.corelia.provider.model.BinaryStoreRequest;
import ru.corelia.provider.model.BinaryLocation;
import ru.corelia.provider.model.BinaryMetadata;
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

    S3BinaryStorage(S3Client client, String bucket) {
        this.client = client;
        this.bucket = bucket;
    }

    @Override public BinaryLocation reserve(BinaryStoreRequest request, AuthContext auth) {
        String id = UUID.randomUUID().toString();
        return new BinaryLocation(new StorageReference(REFERENCE_PREFIX + id), "s3", bucket, key(id));
    }

    @Override public StoredFile store(BinaryStoreRequest request, InputStream content, AuthContext auth) {
        if (request.reference() == null) throw new IllegalArgumentException("Для S3 storage reference должен быть зарезервирован");
        String id = id(request.reference());
        client.putObject(builder -> builder.bucket(bucket).key(key(id)).contentType(request.contentType()).metadata(java.util.Map.of("sha256", request.checksum())),
                RequestBody.fromInputStream(content, request.size()));
        return new StoredFile(request.reference(), request.checksum(), request.size(), request.contentType());
    }

    @Override public InputStream read(StorageReference reference, AuthContext auth) {
        return client.getObject(builder -> builder.bucket(bucket).key(key(id(reference))));
    }

    @Override public InputStream read(StorageReference reference, long offset, long length, AuthContext auth) {
        if (offset < 0 || length < 1) throw new IllegalArgumentException("Некорректный диапазон binary content");
        long last = Math.addExact(offset, length - 1);
        return client.getObject(builder -> builder.bucket(bucket).key(key(id(reference)))
                .range("bytes=" + offset + "-" + last));
    }

    @Override public BinaryMetadata metadata(StorageReference reference, AuthContext auth) {
        var response = client.headObject(builder -> builder.bucket(bucket).key(key(id(reference))));
        Long size = response.contentLength();
        if (size == null || size < 0) throw new IllegalStateException("S3 не вернул размер binary content");
        return new BinaryMetadata(size, response.contentType(), response.metadata().getOrDefault("sha256", ""));
    }

    @Override public void delete(StorageReference reference, AuthContext auth) {
        client.deleteObject(builder -> builder.bucket(bucket).key(key(id(reference))));
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
