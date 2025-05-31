package ru.sbrf.sidec.utils;

//import org.apache.avro.generic.GenericRecord;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Optional.ofNullable;
import static ru.sbrf.sidec.extension.KafkaExtension.KAFKA_BOOTSTRAP_SERVERS;
//import static ru.sbrf.sidec.extension.KafkaExtension.TEST_BOOTSTRAP_SERVERS_PROPERTY_NAME;

//TODO сделать параметризированный класс для тестов
public class TestKafkaConsumer {

    private final static String groupId = "test-group";

    //TODO по-хорошему бы объединить два поля ниже для достижения cтруктуры TopicPartition,List<String>
    private final static ConcurrentMap<String, List<String>> kafkaData = new ConcurrentHashMap<>();
    private final static ConcurrentMap<String, List<String>> partitionedKafkaData = new ConcurrentHashMap<>(); //<Topic.partition, String>
    private final static ConcurrentMap<String, List<ConsumerRecord<String, String>>> rawData = new ConcurrentHashMap<>();
    private final static ConcurrentMap<String, List<ConsumerRecord<String, String>>> partitionedRawData = new ConcurrentHashMap<>(); //<Topic.partition, ConsumerRecord>
    //private final static ConcurrentMap<String, List<GenericRecord>> kafkaSinkRecordData = new ConcurrentHashMap<>();
    private final static ConcurrentMap<String, Thread> kafkaPollingMap = new ConcurrentHashMap<>();
    private final static ConcurrentMap<String, String> subscribedTopics = new ConcurrentHashMap<>();

    private final static Lock lock = new ReentrantLock();

    public static void initKafkaConsumer(List<String> topics) {
        var bootstrapServers = System.getProperty(KAFKA_BOOTSTRAP_SERVERS);
        Properties properties = new Properties();
        properties.setProperty(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.setProperty(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.setProperty(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        properties.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(properties);
        var newTopics = topics.stream().filter(x -> !subscribedTopics.containsKey(x)).collect(Collectors.toList());
        if (newTopics.isEmpty()) {
            return;
        }
        consumer.subscribe(newTopics);
//      assignedTopics: consumer.listTopics();
        var kafkaPolling = new Thread(() -> {
            boolean workInProgress = true;
            while (workInProgress) {
                try {
                    ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));
                    doInLock(() -> {
                        for (ConsumerRecord<String, String> record : records) {
                            addKafkaData(record);
                            addPartitionedKafkaData(record);
                            addRawData(record);
                            addPartitionedRawData(record);
                        }
                        return null;
                    });
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    workInProgress = false;
                }
            }
        });
        kafkaPolling.start();
        kafkaPollingMap.put(kafkaPolling.getName(), kafkaPolling);
        newTopics.forEach(x -> subscribedTopics.put(x, kafkaPolling.getName()));
    }

    private static void addKafkaData(ConsumerRecord<String, String> record) {
        ofNullable(kafkaData.get(record.topic()))
                .map(x -> x.add(record.value()))
                .orElseGet(() -> {
                    var list = new ArrayList<String>();
                    list.add(record.value());
                    kafkaData.put(record.topic(), list);
                    return true;
                });
    }

    private static void addPartitionedKafkaData(ConsumerRecord<String, String> record) {
        ofNullable(partitionedKafkaData.get(record.topic() + "." + record.partition()))
                .map(x -> x.add(record.value()))
                .orElseGet(() -> {
                    var list = new ArrayList<String>();
                    list.add(record.value());
                    partitionedKafkaData.put(record.topic() + "." + record.partition(), list);
                    return true;
                });
    }

    private static void addRawData(ConsumerRecord<String, String> record) {
        ofNullable(rawData.get(record.topic()))
                .map(x -> x.add(record))
                .orElseGet(() -> {
                    var list = new ArrayList<ConsumerRecord<String, String>>();
                    list.add(record);
                    rawData.put(record.topic(), list);
                    return true;
                });
    }

    private static void addPartitionedRawData(ConsumerRecord<String, String> record) {
        ofNullable(partitionedRawData.get(record.topic() + "." + record.partition()))
                .map(x -> x.add(record))
                .orElseGet(() -> {
                    var list = new ArrayList<ConsumerRecord<String, String>>();
                    list.add(record);
                    partitionedRawData.put(record.topic() + "." + record.partition(), list);
                    return true;
                });
    }

    public static ConcurrentMap<String, List<String>> getPartitionedKafkaData() {
        return partitionedKafkaData;
    }

    public static List<Map.Entry<String, List<String>>> getPartitionedKafkaData(String topic) {
        return partitionedKafkaData.entrySet().stream().filter(x -> x.getKey().contains(topic)).collect(Collectors.toList());
    }

    public static List<String> getKafkaDataExcludeHeartbeats(String topic) {
        return getKafkaData().getOrDefault(topic, Collections.emptyList())
                .stream()
                .filter(x -> !x.contains("\"table_name\":\"heartbeats\""))
                .collect(Collectors.toList());
    }

    public static ConcurrentMap<String, List<String>> getKafkaData() {
        return kafkaData;
    }

    public static List<String> getKafkaData(String topic) {
        return doInLock(() -> new ArrayList<>(getKafkaData().getOrDefault(topic, Collections.emptyList())));
    }

    public static List<ConsumerRecord<String, String>>  getRawData(String topic) {
        return getRawData().getOrDefault(topic, Collections.emptyList());
    }

    public static ConcurrentMap<String, List<ConsumerRecord<String, String>>> getRawData() {
        return rawData;
    }

    public static ConcurrentMap<String, List<ConsumerRecord<String, String>>> getPartitionedRawData() {
        return partitionedRawData;
    }

    public static List<Map.Entry<String, List<ConsumerRecord<String, String>>>> getPartitionedRawData(String topic) {
        return partitionedRawData.entrySet().stream().filter(x -> x.getKey().contains(topic)).collect(Collectors.toList());
    }

/*    public static ConcurrentMap<String, List<GenericRecord>> getKafkaSinkRecordData() {
        return kafkaSinkRecordData;
    }*/

    public static void clearKafkaData() {
        kafkaData.clear();
        partitionedKafkaData.clear();
        rawData.clear();
        partitionedRawData.clear();
        //   kafkaSinkRecordData.clear();
    }

    public static void stopConsumer() {
        for (var kafkaPolling : kafkaPollingMap.values()) {
            try {
                if (kafkaPolling != null) {
                    kafkaPolling.interrupt();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

   /* public static int getLastSchemaPackageReferenceId(String schemaPackageTopic, String tableName) {
        if (!isSubscribed(schemaPackageTopic)) {
            throw new IllegalStateException("Not subscribed to schema package topic: " + schemaPackageTopic);
        }
        var parts = tableName.split("\\.");
        var schema = parts[0];
        var table = parts[1];
        var data = getKafkaData(schemaPackageTopic);
        for (int i = data.size() - 1; i >= 0; i--) {
            var x = Objects.requireNonNull(ObjectMapperUtil.readValue(data.get(i), SchemaPackageDto.class));
            if (x.getSchemaName().equals(schema) && x.getTableName().equals(table)) {
                return x.getReferenceId();
            }
        }
        return -1;
    }*/

    private static boolean isSubscribed(String topic) {
        return subscribedTopics.containsKey(topic);
    }

  /*  public static Map<String, Set<String>> getSchemaPackageTablesAndColumns(String schemaPackageTopic, int referenceId) {
        return doInLock(() -> getKafkaData(schemaPackageTopic)
                .stream()
                .map(x -> ObjectMapperUtil.readValue(x, SchemaPackageDto.class))
                .filter(Objects::nonNull)
                .filter(x -> x.getReferenceId().equals(referenceId))
                .collect(
                        Collectors.toMap(
                                x -> x.getSchemaName() + "." + x.getTableName(),
                                x -> x.getColumns().stream().map(SchemaPackageColumnDto::columnName).collect(Collectors.toSet())
                        )
                ));
    }*/

    private static <T> T doInLock(Supplier<T> fn) {
        lock.lock();
        try {
            return fn.get();
        } finally {
            lock.unlock();
        }
    }
}