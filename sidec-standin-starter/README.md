# SIDEC Standin starter ( spring boot )

## [CHANGELOG](../CHANGELOG.md)
## [Информация для формирования PR](../CONTRIBUTING.md)

## Использование стартера
1. `implementation ("ru.sbrf.sidec:sidec-standin-starter:01.000.00")`
2. Выполнить [шаги по настройке бд, кафка и серверов sidec для stand-in](https://confluence.sberbank.ru/display/SIDP/SIDEC.book+05.001.00#SIDEC.book05.001.00-%D0%98%D0%BD%D1%81%D1%82%D1%80%D1%83%D0%BA%D1%86%D0%B8%D1%8F%D0%BF%D0%BE%D0%B7%D0%B0%D0%BF%D1%83%D1%81%D0%BA%D1%83STANDIN%D1%81%D1%85%D0%B5%D0%BC%D1%8B).

### Nexus
Для интеграции через nexus необходимо использовать nexus зависимость.
1. **Dev версии:** https://nexus-ci.delta.sbrf.ru/repository/maven-lib-dev
2. **Release версии:** https://nexus-ci.delta.sbrf.ru/repository/maven-lib-release
#### Пример использования
##### gradle.properties
```
sidec_standin_core_version=01.000.00-SNAPSHOT
maven_lib = https://nexus-ci.delta.sbrf.ru/repository/maven-lib-dev
```
##### build.gradle
```
repositories {
    maven {
        url("$maven_lib")
        credentials {
            username = project.findProperty('NEXUS_USER')
            password = project.findProperty('NEXUS_PASS')
        }
    }
}
dependencies {
    implementation("ru.sbrf.sidec:sidec-standin-core:$sidec_standin_core_version")
}
```

## Принцип работы
Библиотека используется для предоставления функциональности standin на стороне клиента.    
Подробнее о standin:
[Описание STANDIN](https://confluence.sberbank.ru/display/SIDP/SIDEC.book+05.001.00#SIDEC.book05.001.00-STAND-IN)

## Шаги для имплементации
### Отправка сигналов в сигнальный топик:
#### 1. Создать с помощью KafkaSignalBuilder необходимый ProducerRecord.
Пример использования:
```
ProducerRecord<String, SignalRequest> record = KafkaSignalBuilder.builder()
                .withUid(uuid)
                .withMode(SignalMode.main)
                .withAuthor("author")
                .withDescription("description")
                .withSwitchType(SwitchType.consistent)
                .build();
```
### Получение сигналов с сигнального топика:
#### 1. Создать класс листенера, имплементирующий ConsistentKafkaSignalListener или ForceKafkaSignalListener или оба интерфейса сразу, с реализацией необходимых методов.
ConsistentKafkaSignalListener
- prepareSwitch (Подготовка к консистентному переключению)
- doConsistentSwitch (Запуск консистентного переключения)

ForceKafkaSignalListener
- doForceSwitch (Запуск force переключения)
#### 2. Создать KafkaSignalTranslator, который является реализацией Kafka Consumer.
Метод translate аналогичен вызову метода poll Kafka Consumer. Результат вызова отправляется в переданный listener.

### Сохранение сигнала в сигнальную таблицу:
#### 1. Создать TransitionManager для конвертации сигналов из кафки в сущности базы.
#### 2. Для конвертации сигнала из Kafka в сущность базы данных необходимо вызвать метод convertSignalToConnection.
#### 3. Сохранить сущность AppConnection в корректной базе данных в нижнем регистре.

## Дополнительно
### Обработка сигналов с одинаковым ConnectionMode
В случае получения сигнала с ConnectionMode аналогичным текущему состоянию приложения состояние в базу данных необходимо сохранить.
### Метрики
Библиотека sidec-core предоставляет механизм регистрации метрик. Пример реализации представлен в SwitchoverDelegatorMetrics библиотеки sidec-standin-starter.