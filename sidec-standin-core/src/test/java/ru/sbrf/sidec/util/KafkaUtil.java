package ru.sbrf.sidec.util;

import java.util.ArrayList;
import java.util.List;

import static ru.sbrf.sidec.extension.KafkaExtension.adminClient;
import static ru.sbrf.sidec.extension.KafkaExtension.consumer;

public class KafkaUtil {
    public static void deleteKafkaTopics() {
        List<String> topicsName = new ArrayList<>(consumer().listTopics().keySet());
        adminClient().deleteTopics(topicsName);
    }
}