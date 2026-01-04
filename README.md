## JGMLogger

**Ядро**: Paper / Spigot / Folia  
**Версия**: 1.16.5 - 1.21.11  
**Java**: 16+  

### Описание
Плагин отслеживает действия игроков в креативном режиме и логирует их в отдельные файлы для каждого игрока.  
Основная цель — контроль за использованием креатива на серверах с выживанием/донатами.

#### Что логируется:
- Переход в креатив / выход из креатива
- Взятие предметов из креативного меню
- Установка блоков из креатива в мир (с координатами)
- Помещение предметов из креатива в контейнеры  
(сундуки, воронки, печки, эндер-сундуки, вагонетки с сундуком/воронкой и т.д.) — с координатами
- Передача предметов из креатива другому игроку через дроп

### Установка
- Поместите `JGMLogger.jar` в папку `plugins/`.
- Перезапустите сервер.
- При первом запуске создастся папка `plugins/JGMLogger/` с файлом `config.yml`.
- Настройте `config.yml` под свои нужды.
- Выполните `/gmlreload` или перезапустите сервер.

### Команды и права
- `/gmlreload` — Перезагрузка конфига плагина  
Право: `jgmlogger.admin` (по умолчанию у OP)

### Конфигурация (config.yml)
```yaml
# Включение/отключение отдельных типов логирования
log:
  gamemode-change: true     # Логировать смену игрового режима
  creative-take: true       # Логировать взятие предметов из креативного меню
  block-place: true         # Логировать установку блоков из креатива в мир
  container-place: true     # Логировать помещение предметов из креатива в контейнеры
  dropper-sent: true        # Логировать сброс (передачу) предметов из креатива другому игроку (со стороны отправителя)
  receiver-got: true        # Логировать получение предметов из креатива (со стороны получателя)

# Сообщения для команд (поддерживают цветовые коды &)
messages:
  no-permission: "&cНедостаточно &fправ для использования команды"
  reload: "&fПлагин успешно &aперезагружен"

# Шаблоны сообщений, которые записываются в логи игроков
log-messages-templates:
  gamemode-change: "Вошёл в режим: %gamemode%"
  creative-take: "В режиме CREATIVE взял х%amount% %material%"
  block-place: "Установил %material% взятый с CREATIVE на координатах %x% %y% %z%"
  container-place: "Положил х%amount% %material% взятых с CREATIVE в %container% на координатах %coords%"
  dropper-sent: "Сбросил игроку %receiver% х%amount% %material% взятых с CREATIVE"
  receiver-got: "Получил от %sender% х%amount% %material% взятых с CREATIVE"

# Настройки, кто именно будет логироваться
log-settings:
  # Список конкретных игроков
  # Пример: log-players: ['Johny', 'Rey', 'Alex']
  log-players: []
  # Список групп LuckPerms
  # Пример: log-groups: ['admin', 'sponsor', 'vip']
  log-groups: []
  # Логировать всех игроков (если группы и список ников пустые)
  log-all-players: false
```

### Логи
- Логи хранятся в папке `plugins/JGMLogger/logs/`
- Формат файла: `dd.MM.yyyy-ИмяИгрока.log`
#### Пример содержимого:
```text
'12:34':
- 'Вошёл в режим: CREATIVE'
- В режиме CREATIVE взял х64 DIAMOND_BLOCK
- Установил OAK_LOG взятый с CREATIVE на координатах 20 75 4
'12:40':
- Положил х5 DIAMOND_BLOCK взятых с CREATIVE в CHEST на координатах -140 67 256
- Положил х2 DIAMOND в HOPPER_MINECART на координатах 100 64 200
- Получил от jFrostyBoy х64 JUNGLE_WOOD взятых с CREATIVE
```

### Поддерживаемые контейнеры (примеры названий в логе)
- CHEST, TRAPPED_CHEST
- ENDER_CHEST
- HOPPER, DISPENSER, DROPPER
- FURNACE, BLAST_FURNACE, SMOKER
- BREWING_STAND, ENCHANTING_TABLE
- HOPPER_MINECART
- CHEST_MINECART
- И другие блоки/сущности с инвентарём

### Рекомендации
- Используйте для контроля модераторов/админов/донатеров.
- Регулярно проверяйте логи на подозрительные действия.
- При смене групп в LuckPerms — игрок должен перезайти на сервер  
(группа кэшируется при входе).

<img width="1022" height="108" alt="Знімок_20260104_211109" src="https://github.com/user-attachments/assets/17946f18-4e6b-4dce-a4ba-5c2a1baa1a7e" />
<img width="671" height="560" alt="Знімок_20260104_214315" src="https://github.com/user-attachments/assets/2d4c4c7e-df8b-47cd-a825-87e67432a090" />
