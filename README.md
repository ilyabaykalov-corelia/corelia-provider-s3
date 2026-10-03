# corelia-provider-s3

S3-compatible реализация `BinaryStorage` с id `s3`. Attachment-service
использует её для сохранения, range-чтения, metadata и удаления blob; метаданные
вложения и его связь с документом остаются в сервисах-владельцах, а не в S3.

Provider создаёт непрозрачные ссылки вида `corelia-blob://<uuid>` и хранит
объекты по внутреннему ключу. Потребители не должны строить ключ S3 из ссылки.
Для ссылок, не принадлежащих provider, возможно read-only чтение через
зарегистрированный `LegacyBinaryReader`.

Для выбранной capability обязательны:

- `CORELIA_S3_ENDPOINT`;
- `CORELIA_S3_BUCKET`;
- `CORELIA_S3_ACCESS_KEY` и `CORELIA_S3_SECRET_KEY`;
- опционально `CORELIA_S3_REGION` (по умолчанию `us-east-1`).

Auto-configuration активируется при `corelia.provider.binary-storage=s3`.
В локальном Compose endpoint предоставляет SeaweedFS; это не требование к
production provider, которому достаточно S3-compatible API.

```bash
mvn -pl corelia-provider-s3 -am test
```

См. [SPI](../docs/provider-spi.md) и [операции](../docs/operations.md).
