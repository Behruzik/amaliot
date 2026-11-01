package com.example.amaliyot.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;

/** OpenAPI metadata, practical examples and shared JSON error contracts. */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI geadezistOpenApi() {
        return new OpenAPI()
                .info(new Info().title("GEADEZIST: тесты, результаты и сертификаты")
                        .version("1.1.0")
                        .description("""
                                ## Быстрая проверка через Swagger

                                1. Выполните **POST /api/auth/login**. Для локального профиля `demo` выберите пример `demoUser`.
                                2. Скопируйте `accessToken` из ответа. Нажмите **Authorize**, вставьте **только токен, без слова Bearer**, затем Authorize и Close.
                                3. Выполните **GET /api/profile**: здесь находятся ваш `id` и выбранный `professionId`.
                                4. Выполните **GET /api/tests/results**, возьмите `content[].id` и подставьте в **GET /api/tests/results/{attemptId}/pdf**.
                                5. Нажмите **Execute → Download file** и откройте полученный PDF. `download=true` включает скачивание как вложения.
                                6. Проверьте **GET /api/certificates** и PDF сертификата по возвращённому `certificateNumber`.

                                ## Пройти тест самостоятельно

                                Профиль должен содержать имя, фамилию, дату рождения, регион, район, адрес, согласие с офертой и `professionId` из **GET /api/professions**.
                                Названия региона и района берите из **GET /api/locations/regions** и **GET /api/locations/regions/{regionId}/districts**.
                                Запустите **POST /api/tests/categories/{categoryId}/attempts**, где `categoryId` равен вашему `professionId`.
                                Сохраняйте ответы через **PUT /api/tests/attempts/{attemptId}/questions/{questionId}/answer**.
                                `questionId` и `selectedOptionIds` берите из **этой попытки** (`questions[].id` и `questions[].options[].id`).
                                Для NUMERIC отправляйте `numericValue`; для SINGLE/MULTIPLE - `selectedOptionIds`.
                                Затем **POST /api/tests/attempts/{attemptId}/finish**. Повторный finish не меняет итог.
                                Каждый экзамен содержит 16 вопросов, случайно выбранных из общего банка 400 простых вопросов. 14 правильных ответов дают сертификат, если попытка не просрочена и не отменена.
                                Сохранение попытки не останавливает таймер.

                                ## Локальный демонстрационный режим

                                Запуск в PowerShell: `.\\mvnw.cmd "-Dspring-boot.run.profiles=demo" spring-boot:run`.
                                Пользователь: `student.demo@gmail.com` / `DemoUser123!`.
                                Администратор: `admin.demo@example.test` / `DemoAdmin123!`.
                                В demo уже есть готовый профиль, результат 16/16 и сертификат. Эти учётные данные работают только в профиле `demo`.
                                Для проверки прав попробуйте **GET /api/admin/dashboard** под USER (403), затем войдите под ADMIN и замените токен в Authorize (200).

                                ## Как понимать ответы

                                `400` - ошибка входных данных; `401` - нужен действующий JWT; `403` - недостаточно прав;
                                `404` - объект не найден или принадлежит другому пользователю; `409` - конфликт состояния (например, незавершённый тест);
                                `429` - слишком много попыток входа; `503` у восстановления пароля означает, что SMS/email-провайдер пока не настроен.
                                Ошибки возвращаются в JSON даже для PDF. Обычная адресная строка браузера JWT не отправляет: скачивайте через Swagger или клиент с заголовком Authorization.
                                ID примеров не фиксированы: всегда используйте значения, возвращённые вашей базой данных.
                                """))
                .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")
                        .description("Вставьте accessToken из ответа login без слова Bearer. Swagger добавит префикс автоматически.")))
                .addTagsItem(new Tag().name("Autentifikatsiya").description("Регистрация, вход, выход и смена пароля"))
                .addTagsItem(new Tag().name("Profil").description("Мой профиль, профессия и статистика"))
                .addTagsItem(new Tag().name("Testlar va natijalar").description("Начать тест, сохранить ответы, завершить и посмотреть результат"))
                .addTagsItem(new Tag().name("Sertifikatlar").description("Мои сертификаты и публичная проверка номера"))
                .addTagsItem(new Tag().name("PDF: результаты и сертификаты").description("Открыть или скачать PDF своего результата и сертификата"));
    }

    @Bean
    OpenApiCustomizer practicalApiDocumentation() {
        return api -> {
            if (api.getPaths() == null) return;
            addErrorSchema(api);
            api.getPaths().forEach((path, item) -> item.readOperations().forEach(operation -> {
                if (!path.startsWith("/api/")) return;
                addError(operation, "400", "Некорректные параметры или тело запроса");
                if (operation.getSecurity() != null && !operation.getSecurity().isEmpty()) {
                    addError(operation, "401", "Нужен действующий JWT");
                }
                if (path.startsWith("/api/admin/")) addError(operation, "403", "Недостаточно прав для административной операции");
                if (path.contains("{")) addError(operation, "404", "Объект отсутствует или недоступен текущему пользователю");
                if (path.startsWith("/api/tests/") && path.contains("{attemptId}")) {
                    addError(operation, "409", "Состояние попытки не позволяет выполнить действие");
                }
                // Error bodies use JSON even when the successful response is PDF.
                operation.getResponses().forEach((code, response) -> {
                    if (code.startsWith("4") || code.startsWith("5")) response.setContent(errorContent());
                });
                if (operation.getParameters() != null) operation.getParameters().forEach(parameter -> {
                    if ("attemptId".equals(parameter.getName())) {
                        parameter.setDescription("ID из ответа запуска теста либо content[].id из GET /api/tests/results");
                    } else if ("questionId".equals(parameter.getName()) && path.startsWith("/api/tests/")) {
                        parameter.setDescription("questions[].id из текущей попытки; это ID снимка вопроса");
                    } else if ("categoryId".equals(parameter.getName()) && path.startsWith("/api/tests/")) {
                        parameter.setDescription("Выбранный professionId из GET /api/profile");
                    }
                });
            }));

            describe(api, "/api/auth/login", PathItem.HttpMethod.POST, "Войти и получить JWT",
                    "Выберите demoUser или demoAdmin только при запуске профиля demo. "
                            + "В обычном режиме укажите данные своей учётной записи. accessToken вставьте в Authorize без Bearer.");
            example(api, "/api/auth/login", PathItem.HttpMethod.POST, "demoUser", "Локальный demo: обычный пользователь",
                    Map.of("email", "student.demo@gmail.com", "password", "DemoUser123!"));
            example(api, "/api/auth/login", PathItem.HttpMethod.POST, "demoAdmin", "Локальный demo: администратор",
                    Map.of("email", "admin.demo@example.test", "password", "DemoAdmin123!"));
            Operation login = operation(api, "/api/auth/login", PathItem.HttpMethod.POST);
            addError(login, "401", "Неверные данные входа или неактивный аккаунт");
            addError(login, "429", "Превышен лимит входа; повторите позже");

            describe(api, "/api/auth/register", PathItem.HttpMethod.POST, "Зарегистрировать пользователя",
                    "Создаёт USER. Замените email и телефон на свои уникальные значения; разрешён адрес @gmail.com. "
                            + "Перед началом экзамена дополните профиль и выберите professionId.");
            example(api, "/api/auth/register", PathItem.HttpMethod.POST, "newUser", "Замените email и телефон на уникальные",
                    Map.of("email", "new.student@gmail.com", "phone", "+998900001234", "password", "NewUserPass123!",
                            "confirmPassword", "NewUserPass123!", "firstName", "Алишер", "lastName", "Каримов",
                            "nickname", "new-student", "offerAccepted", true));
            addError(operation(api, "/api/auth/register", PathItem.HttpMethod.POST), "409", "Email или телефон уже зарегистрирован");

            describe(api, "/api/profile", PathItem.HttpMethod.GET, "Мой профиль и выбранная профессия",
                    "Возвращает данные владельца JWT. professionId используется как categoryId при запуске теста.");
            describe(api, "/api/profile", PathItem.HttpMethod.PATCH, "Обновить профиль и выбрать профессию",
                    "Отправляйте только изменяемые поля. Для профессии добавьте professionId из GET /api/professions; "
                            + "для региона/района используйте названия из /api/locations. В demo профиль уже заполнен.");
            example(api, "/api/profile", PathItem.HttpMethod.PATCH, "personalDetails", "Пример изменения личных данных",
                    Map.of("firstName", "Алишер", "lastName", "Каримов", "birthDate", "2000-05-15",
                            "address", "Ташкент, учебный адрес", "offerAccepted", true));

            String answerPath = "/api/tests/attempts/{attemptId}/questions/{questionId}/answer";
            describe(api, answerPath, PathItem.HttpMethod.PUT, "Сохранить или заменить ответ",
                    "SINGLE: один ID, MULTIPLE: несколько ID из questions[].options[].id текущей попытки. "
                            + "NUMERIC: число в numericValue. Не смешивайте число и варианты. Пустой selectedOptionIds очищает выбранные варианты.");
            example(api, answerPath, PathItem.HttpMethod.PUT, "numeric", "Числовой ответ: только для NUMERIC",
                    Map.of("numericValue", new java.math.BigDecimal("12.5")));
            example(api, answerPath, PathItem.HttpMethod.PUT, "clearChoice", "Очистить ответ SINGLE/MULTIPLE",
                    Map.of("selectedOptionIds", List.of()));

            describe(api, "/api/tests/results", PathItem.HttpMethod.GET, "Мои завершённые результаты",
                    "Возьмите content[].id для просмотра результата и скачивания PDF. page начинается с 0, size ограничен 100.");
            describe(api, "/api/tests/results/{attemptId}", PathItem.HttpMethod.GET, "Посмотреть итог одной попытки",
                    "Только собственный завершённый тест. Для PDF используйте этот же attemptId в соседнем PDF endpoint.");
            describe(api, "/api/certificates", PathItem.HttpMethod.GET, "Мои действующие сертификаты",
                    "Возьмите certificateNumber для просмотра, публичной проверки или PDF сертификата.");
            describe(api, "/api/certificates/verify/{certificateNumber}", PathItem.HttpMethod.GET,
                    "Публично проверить сертификат", "Авторизация не требуется. valid=false означает, что сертификат отозван.");

            // Public in SecurityConfig despite the controller-level annotation.
            Operation contact = operation(api, "/api/support/requests/contact", PathItem.HttpMethod.GET);
            if (contact != null) {
                contact.setSecurity(List.of());
                contact.getResponses().remove("401");
            }
        };
    }

    private static void addErrorSchema(OpenAPI api) {
        if (api.getComponents() == null) api.setComponents(new Components());
        if (api.getComponents().getSchemas() == null || !api.getComponents().getSchemas().containsKey("ApiError")) {
            api.getComponents().addSchemas("ApiError", new ObjectSchema()
                    .addProperty("code", new StringSchema().description("Машинный код ошибки"))
                    .addProperty("message", new StringSchema().description("Пояснение ошибки"))
                    .addProperty("timestamp", new StringSchema().format("date-time"))
                    .addProperty("path", new StringSchema())
                    .required(List.of("code", "message", "timestamp", "path")));
        }
    }

    private static Content errorContent() {
        return new Content().addMediaType("application/json", new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ApiError")));
    }

    private static void addError(Operation operation, String code, String description) {
        if (operation == null) return;
        if (operation.getResponses() == null) operation.setResponses(new ApiResponses());
        operation.getResponses().computeIfAbsent(code, ignored -> new ApiResponse().description(description).content(errorContent()));
    }

    private static Operation operation(OpenAPI api, String path, PathItem.HttpMethod method) {
        PathItem item = api.getPaths().get(path);
        return item == null ? null : item.readOperationsMap().get(method);
    }

    private static void describe(OpenAPI api, String path, PathItem.HttpMethod method, String summary, String description) {
        Operation operation = operation(api, path, method);
        if (operation != null) {
            operation.setSummary(summary);
            operation.setDescription(description);
        }
    }

    private static void example(OpenAPI api, String path, PathItem.HttpMethod method,
                                String name, String summary, Map<String, Object> value) {
        Operation operation = operation(api, path, method);
        if (operation == null || operation.getRequestBody() == null || operation.getRequestBody().getContent() == null) return;
        MediaType mediaType = operation.getRequestBody().getContent().get("application/json");
        if (mediaType != null) {
            mediaType.setExample(null);
            mediaType.addExamples(name, new Example().summary(summary).value(value));
        }
    }
}
