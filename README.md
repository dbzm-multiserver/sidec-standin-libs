# SIDEC Standin starter ( spring boot )

## [CHANGELOG](../CHANGELOG.md)
## [Информация для формирования PR](../CONTRIBUTING.md)
## [Метрики](./METRICS.md)

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
sidec_starter_version=01.000.00-SNAPSHOT
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
    implementation("ru.sbrf.sidec:sidec-standin-starter:$sidec_starter_version")
}
```

## Принцип работы
Стартер используется для предоставления функциональности standin на стороне клиента.    
Подробнее о standin:
[Описание STANDIN](https://confluence.sberbank.ru/display/SIDP/SIDEC.book+05.001.00#SIDEC.book05.001.00-STAND-IN)   
Стартер встраивается в клиентский код и позволяет отслеживать сигналы о переключениях между main и standin.    
Подробнее о механизме работы стартера:
[Описание sidec-standin-starter](https://confluence.sberbank.ru/display/SIDP/SIDEC.book+05.001.00#SIDEC.book05.001.00-%D0%9E%D0%BF%D0%B8%D1%81%D0%B0%D0%BD%D0%B8%D0%B5sidec-standin-starter)
## Параметры для конфигурации
```yaml
# - опциональные параметры
sidec:
  switchover:
    enabled: true
    #signal-request-timeout-ms: 1000
    #app-name: sidec-switchover
    #signal-topic: sidec.app_signal
    #poll-timeout-ms: 1000
    #watcher:
      #delay-ms: 1000
    #retry:
      #max-attempts: 120
      #back-off-interval-ms: 1000
  kafka:
    bootstrap-servers: ${KAFKA_BOOTSTRAP_SERVERS}
  datasource:
    main:
      url: ${DATABASE_URL_MAIN}
      driverClassName: org.postgresql.Driver
      username: postgres
      password: postgres
    standIn:
      url: ${DATABASE_URL_STANDIN}
      driverClassName: org.postgresql.Driver
      username: postgres
      password: postgres
```
**sidec.switchover.enabled** - Флаг для включения/отключения функционала switchover. Возможные значения: true/false.    
**sidec.switchover.signal-request-timeout-ms** - Таймаут на ожидание отправки запроса в kafka на переключение.    
**sidec.switchover.app-name** - Имя текущего инстанса приложения.    
**sidec.switchover.signal-topic** - Топик сигналов, на который подписывается и в который пишет стартер    
**sidec.switchover.poll-timeout-ms** - Таймаут на проверку статуса внутри стартера.    
**sidec.switchover.watcher.delay-ms** - Периодичность проверки текущих коннектов к базе.     
**sidec.switchover.retry.max-attempts** - Максимальное количество ретраев для внутренних механизмов switchover (Вставка сигнала в базу)
**sidec.switchover.retry.back-off-interval-ms** - Задержка между ретраями для внутренних механизмов switchover (Вставка сигнала в базу)
**sidec.kafka** - Параметры подключения к кафке. Аналогичны парамтерам кафки в spring boot приложении    
**sidec.datasource.main** - Параметры подключения к main базе. Аналогичны парамтерам кафки в spring boot приложении    
**sidec.datasource.standIn** - Параметры подключения к standin базе. Аналогичны парамтерам кафки в spring boot приложении