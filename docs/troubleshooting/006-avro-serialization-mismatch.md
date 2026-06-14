# 006 — Avro Serialization Mismatch: RiskApprovedConsumer Fails to Parse Event

## Symptom
`RiskApprovedConsumer` in `trade-processor-service` throws a JSON parse error
when consuming events from `risk.approved.v1`:

```
Failed to process risk.approved event. error=Illegal character ((CTRL-CHAR, code 0)):
only regular white space (\r, \n, \t) is allowed between tokens
at [Source: REDACTED; line: 1, column: 2]
```

## Root Cause
`risk-engine-service` publishes `RiskApprovedEvent` using `KafkaAvroSerializer` —
the message on Kafka is **Avro binary format** with a 5-byte Schema Registry header
prepended. The consumer in `trade-processor-service` was configured with
`StringDeserializer` and tried to parse the binary payload as JSON.
Binary Avro is not JSON — hence the control character error.

```
risk-engine  → KafkaAvroSerializer  → [5-byte header + Avro binary] → Kafka
trade-processor → StringDeserializer → tries JSON.parse() → CTRL-CHAR error
```

## Solution
Add a dedicated Avro consumer factory in `trade-processor-service`'s `KafkaConfig.java`:

```java
@Bean
public ConcurrentKafkaListenerContainerFactory<String, Object> avroKafkaListenerContainerFactory() {
    Map<String, Object> props = new HashMap<>();
    props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
    props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, KafkaAvroDeserializer.class);
    props.put(AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
    props.put(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, true);
    props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
    props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

    ConcurrentKafkaListenerContainerFactory<String, Object> factory =
            new ConcurrentKafkaListenerContainerFactory<>();
    factory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(props));
    factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.BATCH);
    return factory;
}
```

Point Avro consumers to this factory:

```java
@KafkaListener(
        topics = "${rtrs.kafka.topics.risk-approved}",
        groupId = "${spring.kafka.consumer.group-id}",
        containerFactory = "avroKafkaListenerContainerFactory"
)
public void consume(ConsumerRecord<String, RiskApprovedEvent> record) { ... }
```

Also add `schema.registry.url` at the top-level `kafka.properties` in `application.yml`
(not nested under `producer.properties` — Spring resolves them separately):

```yaml
spring:
  kafka:
    properties:
      schema.registry.url: http://localhost:8081
```

## Affected Consumers
- `RiskApprovedConsumer` — `risk.approved.v1` (Avro)
- `AmlClearedConsumer` — `aml.cleared.v1` (Avro)

Both switched to `avroKafkaListenerContainerFactory`.

## Rule
Any topic published with `KafkaAvroSerializer` must be consumed with
`KafkaAvroDeserializer` + `SPECIFIC_AVRO_READER_CONFIG=true`. Never mix
Avro producers with String consumers.