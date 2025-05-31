package ru.sbrf.sidec.containers;

public class TestcontainersGlobalConfig {
    /**
     * Если true - тогда в {@link SidecKafkaContainer} и др. контейнерах будет выполняться container.setPortBindings(INTERNAL_PORT:INTERNAL_PORT)
     * Т.е. кафка будет доступна по порту 9093, постгрес по 5432 и т.д.
     * <p>
     * Если false - внешние порты будут случайными
     */
    public static boolean BIND_PORTS_TO_HOST = Boolean.parseBoolean(System.getenv("BIND_PORTS_TO_HOST"));

    /**
     * Если true - то кроме zookeeper и kafka контейнеров будет создан kafkaUi, доступный в браузере.
     * <p>
     * Порт, на котором будет запущен ui, зависит от настройки {@link TestcontainersGlobalConfig#BIND_PORTS_TO_HOST}:
     * если true - порт будет равен 9090.
     * если false - порт будет случайным.
     */
    public static boolean ENABLE_KAFKA_UI = Boolean.parseBoolean(System.getenv("ENABLE_KAFKA_UI"));
}