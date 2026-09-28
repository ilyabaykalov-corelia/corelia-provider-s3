package ru.corelia.providers3;

import java.util.EnumSet;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import ru.corelia.config.CoreliaRuntimeConfig;
import ru.corelia.provider.BinaryStorage;
import ru.corelia.provider.LegacyBinaryReader;
import ru.corelia.provider.ProviderCapability;
import ru.corelia.provider.ProviderDescriptor;

/** Подключает S3 adapter только для binary-storage capability. */
@AutoConfiguration
@ConditionalOnProperty(name = "corelia.provider.binary-storage", havingValue = "s3")
public class S3ProviderConfiguration {
    @Bean BinaryStorage s3BinaryStorage(
            @org.springframework.beans.factory.annotation.Qualifier("coreliaRuntimeConfig") CoreliaRuntimeConfig config,
            java.util.List<LegacyBinaryReader> legacyReaders) {
        return new S3BinaryStorage(config, legacyReaders);
    }
    @Bean ProviderDescriptor s3ProviderDescriptor() { return new ProviderDescriptor() {
        public String id() { return "s3"; }
        public java.util.Set<ProviderCapability> capabilities() { return EnumSet.of(ProviderCapability.BINARY_STORAGE); }
    }; }
}
