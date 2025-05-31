package ru.sbrf.sidec.config;

public class SidecConfig {
    public static final String SIGNAL_TOPIC_NAME_DEFAULT = "sidec.app_signal";
    public static final String APP_SIGNAL_RECORD_KEY = "sidec.app_signal.status";

    public static final String CONSUMER_GROUP_ID_PREFIX = "switchover-consumer-";
    public static final String CONSUMER_GROUP_ID = "group.id";
    public static final String CONSUMER_CLIENT_ID = "client.id";

    public static final String CLEAN_UP_POLICY_CONFIG_NAME = "cleanup.policy";
    public static final String CLEAN_UP_POLICY_COMPACT = "compact";
}