# GEADEZIST / Amaliyot - тесты, результаты и PDF

Backend на Java 17 и Spring Boot 4.1.1. В проекте есть пользователи и роли, общий банк из 400 простых вопросов по геодезическим измерениям, экзамены из 16 случайных вопросов, результаты, сертификаты, профиль, новости, справочники и обращения в поддержку.

## Что изменено

- Добавлены PDF результата и сертификата с русскими/узбекскими символами, переносами строк и проверкой владельца.
- Swagger дополнен примерами запросов, авторизацией Bearer JWT, описанием PDF и понятным сценарием проверки.
- Исправлены ответы 401/403, обработка срока экзамена, время результата, запросы ответов по одному на каждый вопрос, числовые границы и гонки записи.
- Улучшено хранение паролей с совместимостью старых хешей, добавлено ограничение частых запросов авторизации.
- Добавлен отдельный деморежим: два аккаунта, готовый результат 16/16 и сертификат. На свежей базе создаётся общий банк из 400 простых вопросов.
- Обычная локальная база теперь сохраняется в `data/`. Добавлены Docker Compose с PostgreSQL и CI-проверки.

Подробности: [изменения](docs/CHANGES_RU.md), [проверка через Swagger](docs/SWAGGER_GUIDE_RU.md), [контракт API](API.md), [результаты проверки](docs/VERIFICATION.md).

## Запуск готовой сборки без Maven

В поставку включён `dist/Amaliyot.jar`. Если Java 17+ уже установлена, запустите **run-demo.cmd** в Windows или `./run-demo.sh` в Linux/macOS. После появления сообщения о запуске откройте <http://localhost:8080/swagger-ui.html>.

Можно запустить напрямую:

```powershell
java -jar dist\Amaliyot.jar --spring.profiles.active=demo
```

Готовая сборка работает без скачивания Maven-зависимостей. После изменения исходников пересоберите JAR командой ниже: `dist/Amaliyot.jar` сам не обновляется, новый файл появляется в `target/`.

## Быстрый запуск демо из исходников

Нужен **JDK 17 или новее**. Отдельно устанавливать Maven не требуется: wrapper включён. Для первой сборки нужен интернет.

### Windows PowerShell

Откройте терминал **в папке, где находится `pom.xml`**:

```powershell
java -version
.\mvnw.cmd "-Dspring-boot.run.profiles=demo" spring-boot:run
```

### Linux / macOS

```bash
chmod +x mvnw
./mvnw -Dspring-boot.run.profiles=demo spring-boot:run
```

Откройте:

- Swagger: <http://localhost:8080/swagger-ui.html>
- OpenAPI JSON: <http://localhost:8080/v3/api-docs>
- Проверка состояния: <http://localhost:8080/actuator/health>

Деморежим не требует `.env`. Он использует отдельную базу **в памяти**, слушает `127.0.0.1`, создаёт только синтетические данные. После остановки данные демо исчезают. Деморежим не предназначен для сервера с реальными пользователями.

| Учётная запись | Email | Пароль | Роль |
| --- | --- | --- | --- |
| Ученик | `student.demo@gmail.com` | `DemoUser123!` | `USER` |
| Администратор | `admin.demo@example.test` | `DemoAdmin123!` | `SUPER_ADMIN` |

В профиле ученика уже выбрано направление. У него есть завершённый результат **38 из 40 = 95%** и действующий сертификат. ID берите из API, не подставляйте предполагаемые значения.

## Самая быстрая проверка PDF

1. В Swagger откройте `POST /api/auth/login`, нажмите **Try it out** и выберите пример `demoUser`.
2. Выполните запрос, скопируйте **только** значение `accessToken`.
3. Нажмите **Authorize**, вставьте токен **без `Bearer`**, затем Authorize и Close.
4. Выполните `GET /api/tests/results`. Скопируйте `content[0].id`.
5. Выполните `GET /api/tests/results/{attemptId}/pdf` с этим ID. Нажмите **Download file** и откройте скачанный PDF.
6. Выполните `GET /api/certificates`, скопируйте `certificateNumber` и проверьте `GET /api/certificates/{certificateNumber}/pdf`.

Весь сценарий нового экзамена, сохранения ответов, прав доступа и ошибок описан в [руководстве Swagger](docs/SWAGGER_GUIDE_RU.md).

### PDF API

```http
GET /api/tests/results/{attemptId}/pdf
GET /api/certificates/{certificateNumber}/pdf
Authorization: Bearer <accessToken>
```

Без параметра используется `Content-Disposition: inline`; `?download=true` предлагает скачивание. Успешный ответ: `application/pdf`, `Cache-Control: no-store, private`. Ошибки приходят в JSON: **401** без входа, **404** для чужого документа, **409** для активной незавершённой попытки. Время в PDF показано в `Asia/Tashkent`; сменить пояс можно параметром `app.reports.zone-id`.

Простое вставление URL в адресную строку браузера не передаёт Bearer JWT. Поэтому используйте Swagger, HTTP-клиент или запрос из интерфейса приложения с заголовком `Authorization`. Токен в URL не нужен.

## Запуск кнопкой Run в IntelliJ IDEA

Откройте корневой `pom.xml` через **Open as Project**. В **Project Structure**
выберите **Project SDK: JDK 17** и **Modules -> Amaliyot -> Dependencies -> Module SDK: Project SDK**.
После изменения POM выполните **Maven -> Reload All Maven Projects**.

В **Run -> Edit Configurations -> AmaliyotApplication** задайте **Active profiles: demo**.
Если поле скрыто, включите его через **Modify options**. Альтернативно укажите в
**Program arguments** значение `--spring.profiles.active=demo`. Запустите приложение.
В консоли должны появиться активный профиль `demo` и сообщение о запуске Tomcat на порту 8080.

POM использует `spring-boot-starter-webmvc` и явно объявляет `spring-boot-starter-tomcat`.
Если появляется ошибка `no ServletWebServerFactory bean`, проверьте успешную
Maven-синхронизацию: в библиотеках запуска должны присутствовать `spring-boot-tomcat`
и `tomcat-embed-core`. Одна библиотека `tomcat-embed-el` HTTP-сервер не содержит.

## Если IntelliJ IDEA пишет `cannot access jakarta.servlet.Filter`

В `pom.xml` Servlet API объявлен прямой зависимостью `jakarta.servlet:jakarta.servlet-api`
со scope `provided`. Версию задаёт Spring Boot. API нужен компилятору и IDE;
при запуске его предоставляет встроенный Tomcat, поэтому отдельный API JAR исключён
из упаковки Spring Boot и не дублирует классы контейнера.

После замены `pom.xml` откройте справа панель Maven и выполните **Reload All Maven Projects**.
Дождитесь загрузки `jakarta.servlet-api:6.1.0`. Из терминала в папке проекта:

```powershell
.\mvnw.cmd -U clean compile
.\mvnw.cmd "-Dspring-boot.run.profiles=demo" spring-boot:run
```

Если Maven возвращает `BUILD SUCCESS`, а встроенная сборка IDEA всё ещё показывает
ошибку, проверьте, что открыт этот `pom.xml`, выполните Maven Reload и затем
**Build -> Rebuild Project**. Если Maven не может скачать зависимость, первая
строка `[ERROR]` в его выводе укажет причину загрузки.

## Обычный локальный запуск с сохранением данных

Скопируйте `.env.example` в `.env`. Заполните `SUPER_ADMIN_EMAIL`, `SUPER_ADMIN_PASSWORD`, `SUPER_ADMIN_PHONE`. Для стабильности сессий задайте случайный `JWT_SECRET_BASE64`. Затем:

```powershell
.\mvnw.cmd spring-boot:run
```

По умолчанию используется файловая H2 в `./data/amaliyot`. Запускайте из одной и той же папки проекта: относительные пути базы и загрузок зависят от рабочей директории. В этом режиме стартовый пользователь создаётся только при первом запуске. Если super admin уже существует, перезапуск не меняет его пароль.

Создание случайного ключа JWT в Windows PowerShell:

```powershell
$bytes = New-Object byte[] 32
$rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
$rng.GetBytes($bytes)
$rng.Dispose()
[Convert]::ToBase64String($bytes)
```

Скопируйте вывод в `JWT_SECRET_BASE64` в `.env`. На Linux/macOS можно использовать `openssl rand -base64 32`.

Исходные `.env` и IDE-настройки в обновлённый архив не включены. Если они нужны для вашей существующей базы, перенесите их локально и проверьте `DB_URL`. Приложение читает `.env` только из рабочего каталога через Spring; поиск секретов в родительских папках убран. Старые миграции V1-V13 не изменены.

## PostgreSQL и Docker

Установите Docker с Compose. Скопируйте `.env.example` в `.env`, задайте непустые `DB_PASSWORD`, `JWT_SECRET_BASE64` и данные первого администратора. `DB_USERNAME` можно оставить `amaliyot`.

```bash
docker compose up --build -d
docker compose logs -f app
```

Compose запускает PostgreSQL 16, ждёт готовности БД, затем запускает приложение с профилем `prod`. PostgreSQL и загрузки находятся в именованных томах. API доступен локально на `127.0.0.1:8080`. Swagger в `prod` выключен; для локальной проверки через Compose задайте `SWAGGER_ENABLED=true` в `.env` и пересоздайте контейнер `app`:

```bash
docker compose up -d --force-recreate app
```

Остановка с сохранением данных:

```bash
docker compose down
```

`docker compose down -v` удаляет тома с данными; не используйте его для обычной остановки. Для публикации на сервере настройте свой домен/HTTPS и reverse proxy. Внешняя публикация в рамках этого обновления не выполнялась.

Для уже существующего PostgreSQL задайте `DB_URL=jdbc:postgresql://HOST:5432/DATABASE`, `DB_USERNAME`, `DB_PASSWORD` и запустите с `-Dspring-boot.run.profiles=prod`. Профиль `prod` требует заданный секрет JWT и не создаёт случайный временный ключ.

## Проверки и сборка JAR

```powershell
.\mvnw.cmd clean verify
java -jar target\Amaliyot-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

Linux/macOS:

```bash
./mvnw clean verify
java -jar target/Amaliyot-0.0.1-SNAPSHOT.jar --spring.profiles.active=demo
```

Тесты используют изолированную H2, не импортируют `.env` и не обращаются к вашей рабочей БД. В `target/surefire-reports` находятся JUnit-отчёты, в `target/sample-result.pdf` и `target/sample-certificate.pdf` - PDF из тестовых данных.

CI `.github/workflows/ci.yml` выполняет проверки на Java 17 и отдельный набор интеграционных тестов с PostgreSQL. Для него нужно разместить проект в своём GitHub-репозитории. Локальная проверка не означает, что workflow уже запускался на GitHub.

## Правила и ограничения

- Экзамен: 40 случайных активных вопросов выбранного направления; сертификат при 35+ правильных ответах. Просроченная/отменённая попытка не даёт действующий сертификат.
- Начало экзамена требует заполненный профиль: имя, фамилия, дата рождения, регион, район, адрес, профессия и принятая оферта.
- `save` сохраняет отметку для продолжения, **не останавливает таймер**. Ответы сохраняются при каждом PUT. Повторный finish не пересчитывает результат заново.
- Обычная регистрация по-прежнему требует Gmail и уникальный телефон. Это существующее бизнес-правило сохранено.
- Новый формат паролей: PBKDF2-HMAC-SHA256, 600 000 итераций, 256 бит, соль. Старые короткие PBKDF2 и BCrypt обновляются после успешного входа.
- Ограничение auth: 20 запросов/60 секунд на IP, таблица до 10 000 адресов. Настройки: `security.auth-rate-limit.max-requests`, `.window-seconds`, `.max-clients`. Ответ 429 содержит `Retry-After`. Существует также отдельная блокировка частых ошибок входа по логину.
- IP-лимит хранится в одном процессе. При нескольких экземплярах нужен общий лимит в reverse proxy/API gateway или Redis; не доверяйте произвольному `X-Forwarded-For`.
- Email/SMS-провайдер восстановления пароля не настроен; соответствующие маршруты честно возвращают 503. Восстановление не имитируется без доставки кода.
- Это backend API. Отдельный пользовательский веб-интерфейс не добавлялся; Swagger служит для работы с API.

## O‘zbekcha qisqa qo‘llanma

Java 17 bilan `./mvnw -Dspring-boot.run.profiles=demo spring-boot:run` ni ishga tushiring. Swagger: <http://localhost:8080/swagger-ui.html>. Demo foydalanuvchi: `student.demo@gmail.com`, parol: `DemoUser123!`. Login javobidagi `accessToken` ni **Authorize** oynasiga `Bearer` so‘zisiz kiriting. `GET /api/tests/results` dan ID oling, so‘ng `/api/tests/results/{attemptId}/pdf` orqali PDF yuklang. Demo bazasi vaqtinchalik; odatiy rejimda ma’lumotlar `data/` papkasida saqlanadi. Eski parollar keyingi muvaffaqiyatli kirishda yangi formatga o‘tadi.
