package ru.sbrf.sidec.utils;

import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static ru.sbrf.sidec.extension.KafkaExtension.adminClient;
import static ru.sbrf.sidec.extension.KafkaExtension.consumer;

public class KafkaUtil {
    public static void deleteKafkaTopics() {
        var topicsName = consumer().listTopics()
                .keySet()
                .stream()
                .collect(Collectors.toList());
        adminClient().deleteTopics(topicsName);
    }

    public static void clearConsumerGroups() {
        try {
            var groups = adminClient().listConsumerGroups().all().get();
            List<String> groupsToDelete = groups.stream().map(ConsumerGroupListing::groupId).collect(Collectors.toList());
            adminClient().deleteConsumerGroups(groupsToDelete).all().get();
        } catch (InterruptedException | ExecutionException e)   {
            System.out.println("Exception during deleting consumer groups");
        }
    }
}