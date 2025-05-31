# SIDEC Standin starter ( spring boot )

## [CHANGELOG](../CHANGELOG.md)
## [Информация для формирования PR](../CONTRIBUTING.md)
## [Метрики](./METRICS.md)


## Принцип работы
Стартер используется для предоставления функциональности standin на стороне клиента.
Стартер встраивается в клиентский код и позволяет отслеживать сигналы о переключениях между main и standin.
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