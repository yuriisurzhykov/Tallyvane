# Полный локальный стенд Tallyvane

Эта инструкция поднимает настоящий backend, PostgreSQL, три frontend-приложения и Mailpit. Она
предназначена для Windows + Docker Desktop и PowerShell. Mailpit принимает письма по SMTP, но не
пересылает их в интернет. Для обычной почтовой регистрации Google Cloud и SMTP-провайдер не нужны.

Стенд использует отдельные Docker volumes и не читает production `.env`. Откройте PowerShell в корне
репозитория `E:\Projects\job-search-console` и не запускайте команды из `ops/`.

## 1. Требования

- Docker Desktop запущен и использует Linux containers.
- Git checkout проекта, Java 21 и PowerShell доступны на Windows.
- Для сборки Next.js образов Docker должен иметь доступ к npm registry; для backend — к Maven
  repositories. Dockerfile backend также скачивает OpenTelemetry Java agent с GitHub.
- Свободен локальный порт 80, а также 3080, 3081, 8025 и 8080. Порт 80 нужен для имён без номера
  порта. Если его занимает IIS или другой proxy, остановите этот локальный сервис на время проверки.

Проверьте Docker:

```powershell
docker version
docker compose version
```

## 2. Локальные имена

Compose настроен на следующие имена:

| URL | Сервис |
| --- | --- |
| `http://app.localhost` | Основное приложение |
| `http://admin.localhost` | Admin приложение |
| `http://surzhykov.localhost` и `http://web.localhost` | Публичный сайт |
| `http://mail.localhost` | Mailpit — просмотр писем |
| `http://localhost:3080` | Совместимый адрес app для Google OAuth callback |
| `http://localhost:3081` | Совместимый адрес admin |
| `http://localhost:8080` | Backend API для диагностики |
| `http://localhost:8025` | Прямой адрес Mailpit для диагностики |

В браузерах с поддержкой special-use домена `.localhost` поддомены обычно уже ведут на loopback.
Для стабильной работы на Windows добавьте запись в hosts от имени администратора. Откройте
PowerShell **Run as administrator** и выполните:

```powershell
$hostsPath = Join-Path $env:SystemRoot 'System32\drivers\etc\hosts'
Add-Content -Path $hostsPath -Value "`r`n127.0.0.1 app.localhost admin.localhost mail.localhost surzhykov.localhost web.localhost"
ipconfig /flushdns
```

Проверьте разрешение имён:

```powershell
[System.Net.Dns]::GetHostAddresses('app.localhost')
[System.Net.Dns]::GetHostAddresses('admin.localhost')
[System.Net.Dns]::GetHostAddresses('mail.localhost')
```

В каждом случае должен присутствовать `127.0.0.1`. Чтобы отменить настройку, удалите добавленную
строку из hosts-файла от имени администратора и снова выполните `ipconfig /flushdns`.

Браузер хранит cookies отдельно для `app.localhost` и `admin.localhost`. Это намеренно: вход
пользователя приложения не создаёт admin-сессию.

## 3. Создать локальные настройки

Из корня репозитория скопируйте пример. Файл назначения уже исключён из Git:

```powershell
Copy-Item ops/auth-local.env.example ops/auth-local.env
```

Откройте `ops/auth-local.env` и проверьте `TALLYVANE_ADMIN_EMAILS`. Значение должно совпадать с
адресом, которым вы будете входить в admin. При регистрации на этот адрес код всё равно попадёт в
Mailpit, даже если адрес оканчивается на `gmail.com`.

**Не копируйте production `.env` и не используйте production TOTP keyset.** Локальная база и
Mailpit отдельны от production, но secrets всё равно следует держать только в игнорируемом
`ops/auth-local.env` и в пользовательском каталоге.

## 4. Создать отдельный ключ для Authenticator

Backend требует ключ шифрования TOTP. Создайте его один раз в отдельном каталоге пользователя;
генератор не выводит ключ в консоль и откажется перезаписывать существующий файл.

```powershell
$keyDirectory = Join-Path $env:LOCALAPPDATA 'Tallyvane\auth-local'
$keysetPath = Join-Path $keyDirectory 'totp-keyset.json'
New-Item -ItemType Directory -Force -Path $keyDirectory | Out-Null

Push-Location backend
try {
    .\gradlew.bat :modules:identity:infrastructure:generateTotpKeyset "-PkeysetOutput=$keysetPath"
}
finally {
    Pop-Location
}

```

Оркестратор ниже сам загружает сохранённый keyset перед `up`, `rebuild` и `start`. Если переменная
`DEMO_TOTP_KEYSET` уже была задана в PowerShell, он сохраняет прежнее значение после команды. Для
ручного запуска Compose задайте переменную так:

```powershell
$env:DEMO_TOTP_KEYSET = (Get-Content -Raw $keysetPath).Trim()
```

Если потерять или заменить этот ключ при сохранённой базе, ранее подключённые TOTP factors нельзя
будет расшифровать.

## 5. Единая сборка и запуск

Из корня репозитория поднимите весь стек одной командой:

```powershell
.\ops\local-stack.ps1 up
```

Оркестратор проверяет локальный env и TOTP keyset, временно останавливает работающие контейнеры этого
стенда, собирает backend distributions и последовательно собирает образы, затем запускает Compose.
Остановка освобождает память в WSL во время Docker build; именованные volumes остаются на месте. Если
сборка завершится ошибкой, скрипт поднимет прежние контейнеры с прежними образами без пересоздания.
Каждый BuildKit build ограничен 1536 MiB; для другой конфигурации WSL лимит можно переопределить,
например `-BuildMemoryLimit 2g`. Сборка всё равно запускается по одному образу за раз.
`GRADLE_USER_HOME` по умолчанию указывает на `%USERPROFILE%\.gradle`; это сохраняет Gradle distributions,
зависимости и build cache между запусками. Можно задать другой постоянный каталог заранее:

```powershell
$env:GRADLE_USER_HOME = 'D:\dev-cache\gradle'
.\ops\local-stack.ps1 up
```

Frontend Dockerfiles сохраняют pnpm store в BuildKit cache mount. Он переиспользуется между сборками и
не попадает в итоговый image. Docker layer cache отдельно сохраняет установку пакетов, пока не изменились
lockfile или package manifests. При первой сборке зависимости всё равно скачиваются.

Полезные команды из корня репозитория:

```powershell
.\ops\local-stack.ps1 rebuild frontend-app  # пересобрать изменённый сервис и обновить весь стек
.\ops\local-stack.ps1 rebuild backend       # пересобрать backend и обновить весь стек
.\ops\local-stack.ps1 rebuild all           # пересобрать все образы
.\ops\local-stack.ps1 start                 # запустить уже собранные образы без сборки
.\ops\local-stack.ps1 status                # состояние контейнеров
.\ops\local-stack.ps1 logs                  # последние 100 строк логов
.\ops\local-stack.ps1 logs -Follow          # продолжать показывать новые строки
.\ops\local-stack.ps1 stats                 # текущая память работающих контейнеров
```

Если менялся только код одного frontend, укажите его цель. Изменение общего пакета, используемого
несколькими приложениями, требует пересобрать каждое затронутое приложение либо выполнить `rebuild all`.

## 6. (Необязательно) Включить Google OAuth

Google OAuth — единственная часть, которой нужна внешняя настройка. Без неё пароль, email-коды,
восстановление и MFA продолжают работать.

1. Откройте Google Cloud Console и выберите отдельный проект для разработки либо существующий проект.
2. Настройте Google Auth Platform: укажите название приложения и контактный email.
3. Для Audience выберите `External` и оставьте publishing status `Testing`. Добавьте
   `yuriisurzhykov@gmail.com` в список test users. В режиме Testing Google ограничивает вход
   добавленными тестовыми пользователями и может показывать предупреждение тестового приложения.
4. В Clients создайте OAuth client типа **Web application**.
5. Добавьте в **Authorized redirect URIs** точное значение:
   `http://localhost:3080/api/v1/auth/google/callback`.
6. Скопируйте Client ID и Client Secret. Не добавляйте секрет в репозиторий и не помещайте его в
   frontend variables. В `ops/auth-local.env` заполните все три строки согласованно:

```dotenv
TALLYVANE_GOOGLE_CLIENT_ID=your-client-id
TALLYVANE_GOOGLE_CLIENT_SECRET=your-client-secret
TALLYVANE_GOOGLE_REDIRECT_URI=http://localhost:3080/api/v1/auth/google/callback
```

Оставьте Client Secret только в локальном ignored-файле. Перезапустите backend после изменения env.
Для Google-проверок открывайте app через `http://localhost:3080`, чтобы OAuth state cookie и callback
остались на одном hostname. Обычные password/email/MFA проверки можно выполнять через
`http://app.localhost`. Google требует точного совпадения redirect URI и разрешает HTTP для localhost;
см. [официальную документацию web-server OAuth](https://developers.google.com/identity/protocols/oauth2/web-server?authuser=2).

## 7. Проверить конфигурацию и запустить весь стек

Команда `up` сначала валидирует Compose-конфигурацию (`config --quiet`), после чего запускает сборку и
контейнеры. Для уже собранных образов используйте `start`; состояние проверьте через `status`:

```powershell
.\ops\local-stack.ps1 start
.\ops\local-stack.ps1 status
```

Миграционный контейнер запускается перед backend и должен завершиться с кодом `0`. Посмотрите логи
если backend не перешёл в состояние running:

```powershell
.\ops\local-stack.ps1 logs
```

Для фильтрации логов по сервисам используйте обычную команду Compose из корня репозитория после
загрузки keyset из раздела 4. Docker Desktop показывает те же stdout/stderr в разделе Logs каждого
контейнера: local-стенд использует `json-file` с ограничением 10 MiB × 3 файла на контейнер, поэтому
история доступна и в UI, и через Compose без неограниченного роста файлов.

Backend и три frontend пишут access-события по одному контракту: `severity`, стабильный `event`,
читаемый `body` и структурированные `attributes` (`http.request.method`, `url.path`, HTTP status,
длительность). Query string, заголовки и тело запроса в эти события не попадают. В local Compose
`OTEL_LOGS_EXPORTER=none` выбирает JSON stdout; production Compose задаёт `otlp`, и те же события
передаются в Grafana Cloud. Local логирование можно смотреть в реальном времени:

```powershell
.\ops\local-stack.ps1 logs -Follow
```

Откройте сайт:

- `http://app.localhost/login`
- `http://app.localhost/register`
- `http://mail.localhost`
- `http://admin.localhost/authentication`
- `http://surzhykov.localhost`

Проверка API providers должна вернуть JSON. Google будет `false`, если OAuth credentials не заданы:

```powershell
Invoke-RestMethod http://app.localhost/api/v1/auth/providers
```

## 8. Проверить email, пароль и восстановление

Mailpit не доставляет письма в Gmail. Даже если вы вводите Gmail-адрес, письмо перехватывается внутри
локальной Docker-сети и читается в `http://mail.localhost`.

1. Откройте `/register`, раскройте email-секцию и зарегистрируйте свежий адрес. Для проверки admin
   используйте адрес из `TALLYVANE_ADMIN_EMAILS`; для обычного пользователя можно выбрать другой.
2. Откройте Mailpit, найдите письмо подтверждения и перейдите по ссылке либо возьмите код, если форма
   его запрашивает. Завершите подтверждение в том же браузере.
3. Выйдите и проверьте вход на `/login` по паролю. Пароль должен быть длиной 15–128 символов.
4. Выйдите и проверьте вход по email-коду на `/otp`; письмо с кодом появится в Mailpit.
5. Откройте `/forgot-password`, запросите восстановление, возьмите код из Mailpit и задайте новый
   пароль. Убедитесь, что вход со старым паролем не проходит, а с новым проходит.
6. Проверьте, что код подходит только к своему назначению, одноразовый, истекает через 10 минут,
   повторная отправка доступна через 60 секунд, а после пяти неверных попыток проверка блокируется.

Для негативных проверок запрашивайте каждый code заново и проверяйте ограничения через UI/API;
не используйте одно письмо для разных целей.

## 9. Проверить Authenticator, email MFA и резервные коды

После входа откройте `/account/security`.

1. Начните TOTP enrollment. Добавьте secret в Authenticator app через QR или ручной ключ и подтвердите
   текущий шестизначный код.
2. Сохраните показанные backup codes вне браузера для теста. Backend должен показать набор при
   выпуске; каждый код одноразовый.
3. Выйдите и снова войдите по паролю. Выберите TOTP, введите свежий код Authenticator и подтвердите
   вход.
4. Повторите вход с одним backup code; попробуйте его повторно и убедитесь, что повтор отклонён.
5. Если email factor разрешён активной policy, подключите и проверьте его через код в Mailpit.
6. Проверьте resend cooldown, expired/wrong code, ограничение попыток, явное подтверждение перед
   отключением фактора и запрет удалить единственный допустимый фактор.
7. Проверьте просмотр сессий, отзыв одной сессии, logout и logout-all. Отозванная сессия больше не
   должна авторизовать защищённый запрос.
8. Перепроверьте учетные данные перед изменением MFA: повторная проверка действует пять минут.

## 10. Проверить Google OAuth

Тестируйте только после конфигурации раздела 6, перезапустив stack после env-файла. Вход должен
открываться тем же hostname `localhost:3080`, для которого зарегистрирован callback.

Проверьте успешный вход test user, отмену на экране Google, ошибочный callback/state, MFA после
Google-входа, а также linking из account security после повторной проверки. Попробуйте связать Google
с адресом существующего password-аккаунта без явного linking: автоматического объединения быть не
должно. Повторите linking для уже занятого Google subject и unlink последнего sign-in provider —
оба опасных варианта должны быть отклонены.

Поскольку OAuth state хранится в HttpOnly cookie, не начинайте Google flow на `app.localhost`, а
возвращайтесь на `localhost:3080`: разные hostnames означают разные browser cookies.

## 11. Admin policy и отдельная admin-сессия

Admin проверяет личность на backend; Cloudflare Access не заменяет эту проверку. Admin user должен
быть указан в `TALLYVANE_ADMIN_EMAILS`. При первом открытии `http://admin.localhost/login` backend
создаёт отдельную admin identity для каждого активного подтверждённого пользователя из allowlist,
копирует начальные password/Google credentials и подключённые факторы MFA, но не переносит его
сессии. После этого пользовательская и admin identity изменяются независимо. Cookies на
`app.localhost` и `admin.localhost` намеренно не общие.

Войдите непосредственно через `http://admin.localhost/login`, затем откройте `/authentication`,
измените готовую sign-in policy, подтвердите предупреждение для расширенной комбинации и сохраните
изменения. Сброс MFA для тестового пользователя отзовёт пользовательские сессии и потребует повторно
подключить факторы при следующем входе. Пользователь вне admin allowlist не должен войти в admin.

Подробнее об админском логине, первоначальном provision, Google callback и разделении sessions см.
[admin-login.md](admin-login.md).

## 12. Остановить, перезапустить и сбросить локальные данные

Остановить контейнеры без удаления БД, почты и аккаунтов:

```powershell
.\ops\local-stack.ps1 down
```

Команда `down` останавливает контейнеры и сохраняет БД, почту и аккаунты. Позже поднимите их без
пересборки командой `.\ops\local-stack.ps1 start`, либо пересоберите изменённый сервис через `rebuild`.

Полностью сбросить **только локальные** аккаунты, сессии, policies и сохранённые письма:

```powershell
.\ops\local-stack.ps1 reset-data
```

Команда запросит подтверждение перед удалением только volumes локального стенда. После сброса вызовите
`up` заново: миграции выполнятся автоматически. Не используйте production compose-файл.

### Память Docker Desktop и WSL

У работающих сервисов локального Compose заданы memory limits; backend, база, три frontend, proxy,
Mailpit и контейнер миграций не смогут расти сверх своих лимитов. Оркестратор запускает Gradle без
постоянного daemon и строит Docker images по одному. `stats` показывает память контейнеров, а не весь
WSL page cache:

```powershell
.\ops\local-stack.ps1 stats
.\ops\local-stack.ps1 down
```

Сравните показатели в диспетчере задач Windows после `down`. Если контейнеры остановлены, а общий
показатель RAM/WSL снижается не сразу, это может быть память WSL, удержанная под кэш, а не живая Java
или Node нагрузка. Docker Desktop использует собственный WSL distribution; не выполняйте
`wsl --shutdown` из скрипта, потому что он завершает все WSL distributions и их процессы.

Для WSL 2 параметр `autoMemoryReclaim` находится в секции `[experimental]`, а не `[wsl2]`. Значения
`dropCache` и `gradual` возвращают неиспользуемый page cache сразу или постепенно. Например:

```ini
[wsl2]
memory=2GB

[experimental]
autoMemoryReclaim=dropCache
```

Это настройка машины, она затрагивает все WSL distributions. После изменения прочитайте
[официальную документацию WSL](https://learn.microsoft.com/windows/wsl/wsl-config) и выполните
`wsl --shutdown` вручную в удобное время, чтобы применить её. Эта команда завершает все WSL
distributions, включая Docker Desktop; локальный оркестратор её не запускает. Не задавайте слишком
низкий общий memory ceiling: он ограничит и сборку, и другие WSL задачи.

## 13. Типовые сбои

- **`DEMO_TOTP_KEYSET is missing`:** проверьте, что keyset существует в
  `%LOCALAPPDATA%\Tallyvane\auth-local\totp-keyset.json`; оркестратор загружает его автоматически.
- **Порт 80 занят:** найдите и остановите локальный сервис, который его занимает; либо используйте
  только доступные порты 3080/3081, но тогда имена без порта не заработают.
- **`mail.localhost` возвращает 400/blocked host:** проверьте `MP_ALLOWED_HOSTS` в Compose и
  перезапустите Mailpit.
- **В Google `redirect_uri_mismatch`:** сравните URI в Google Cloud с
  `TALLYVANE_GOOGLE_REDIRECT_URI`, включая схему, порт и полный путь. Начинайте flow с
  `http://localhost:3080`.
- **Код не приходит в Gmail:** это ожидаемо, он хранится в Mailpit. Проверяйте
  `http://mail.localhost` и логи `mailpit`.
- **Admin UI сообщает 401:** это может означать отсутствующую admin-сессию. Текущему admin frontend
  ещё требуется отдельная страница входа; см. раздел 11.
- **Argon2 не загружается при запуске JVM на Windows:** запускайте backend в предоставленном Docker
  image, который устанавливает системную `libargon2-1`; integration suite также запускается внутри
  Docker test runner.
