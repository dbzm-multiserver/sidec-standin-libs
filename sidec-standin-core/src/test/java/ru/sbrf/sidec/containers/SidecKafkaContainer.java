package ru.sbrf.sidec.containers;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.DockerImageName;
import ru.sbrf.sidec.util.SystemUtil;

import java.util.ArrayList;

public class SidecKafkaContainer {
    public static final DockerImageName IMAGE = DockerImageName.parse("confluentinc/cp-kafka:6.2.0")
            .asCompatibleSubstituteFor("confluentinc/cp-kafka");
    public static final String NETWORK_ALIAS = "kafka";
    public static final String KAFKA_BROKER_ID = "1";
    public static final DockerImageName ZOOKEEPER_IMAGE = DockerImageName.parse("confluentinc/cp-zookeeper:7.0.1")
            .asCompatibleSubstituteFor("confluentinc/cp-zookeeper");
    /**
     * Используется для общения внутри докера. Нужен для дополнительных инструментов, типа kafka-ui.
     * Внутри {@link KafkaContainer#KafkaContainer(DockerImageName)} захардкожен этот порт, отдельной константы нет
     */
    public static final int INTERNAL_KAFKA_PORT = 9092;
    public static final String ZOOKEEPER_NETWORK_ALIAS = "zookeeper";
    private static final String KAFKA_UI_IMAGE = "base/docker.io/provectuslabs/kafka-ui:1108c760e5f0b23908f3818500b78fe57d44ce71";

    public static KafkaContainer create() {
        Network network = Network.newNetwork();
        GenericContainer<?> zookeeperContainer = createZookeeperContainer(network);
        return create(zookeeperContainer);
    }

    public static KafkaContainer create(GenericContainer<?> zookeeperContainer) {
        KafkaContainer kafkaContainer = new KafkaContainer(IMAGE)
                .withNetwork(zookeeperContainer.getNetwork())
                .withNetworkAliases(NETWORK_ALIAS)
                .withCreateContainerCmdModifier(x -> x.withHostName(NETWORK_ALIAS))
                .withEnv("KAFKA_BROKER_ID", KAFKA_BROKER_ID)
                .withExternalZookeeper(ZOOKEEPER_NETWORK_ALIAS + ":" + KafkaContainer.ZOOKEEPER_PORT)
                .dependsOn(zookeeperContainer);
        if (TestcontainersGlobalConfig.BIND_PORTS_TO_HOST) {
            kafkaContainer.setPortBindings(
                    new ArrayList<String>() {{
                        add(KafkaContainer.KAFKA_PORT + ":" + KafkaContainer.KAFKA_PORT);
                    }}
            );
            kafkaContainer.withCreateContainerCmdModifier(x -> x.withName("testcontainers-kafka"));
        }
        if (SystemUtil.IS_LINUX) {
            kafkaContainer.waitingFor(new ExternalPortsOpeningWaitStrategy());
        }
        return kafkaContainer;
    }

    public static GenericContainer<?> createZookeeperContainer(Network network) {
        GenericContainer<?> zookeeperContainer = new GenericContainer<>(ZOOKEEPER_IMAGE)
                .withNetwork(network)
                .withNetworkAliases(ZOOKEEPER_NETWORK_ALIAS)
                .withExposedPorts(KafkaContainer.ZOOKEEPER_PORT)
                .withEnv("ZOOKEEPER_CLIENT_PORT", String.valueOf(KafkaContainer.ZOOKEEPER_PORT))
                .withEnv("ZOOKEEPER_TICK_TIME", "2000");
        if (TestcontainersGlobalConfig.BIND_PORTS_TO_HOST) {
            zookeeperContainer.setPortBindings(
                    new ArrayList<String>() {{
                        add(KafkaContainer.ZOOKEEPER_PORT + ":" + KafkaContainer.ZOOKEEPER_PORT);
                    }}
            );
            zookeeperContainer.withCreateContainerCmdModifier(x -> x.withName("testcontainers-zookeeper"));
        }
        if (SystemUtil.IS_LINUX) {
            zookeeperContainer.waitingFor(new ExternalPortsOpeningWaitStrategy());
        }
        zookeeperContainer.start();
        return zookeeperContainer;
    }

    public static GenericContainer<?> createKafkaUiContainer(GenericContainer<?> zookeeperContainer, KafkaContainer kafkaContainer) {
        GenericContainer<?> container = new GenericContainer<>(DockerImageName.parse(KAFKA_UI_IMAGE))
                .withNetwork(kafkaContainer.getNetwork())
                .withExposedPorts(8080)
                .dependsOn(zookeeperContainer)
                .dependsOn(kafkaContainer)
                .withEnv("KAFKA_CLUSTERS_0_NAME", "local")
                .withEnv("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS", SidecKafkaContainer.NETWORK_ALIAS + ":" + SidecKafkaContainer.INTERNAL_KAFKA_PORT)
                .withEnv("KAFKA_CLUSTERS_0_ZOOKEEPER", SidecKafkaContainer.ZOOKEEPER_NETWORK_ALIAS + ":" + KafkaContainer.ZOOKEEPER_PORT)
                .waitingFor(new ExternalPortsOpeningWaitStrategy());
        if (TestcontainersGlobalConfig.BIND_PORTS_TO_HOST) {
            container.setPortBindings(
                    new ArrayList<String>() {{
                        add("9090:8080");
                    }}
            );
            container.withCreateContainerCmdModifier(x -> x.withName("testcontainers-kafka-ui"));
        }
        return container;
    }
}