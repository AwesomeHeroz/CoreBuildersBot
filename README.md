# Core Builders

Core Builders is a Discord bot Paper 1.21.4 plugin that runs a Discord bot and Minecraft integration from the same JAR.

Why keep plugin and discord bot same in Jar? Save cost 

It provides:

* Member progression and Core XP
* Core Credits economy
* Discord and Minecraft commands
* Applications and review tickets
* Projects and missions
* Achievements
* Leaderboards
* Member reputation
* Minecraft ↔ Discord account linking
* 2b2t new-player lookup through Hypergliding
* MySQL persistence through QueryDSL
* Flyway database migrations

## Requirements

* Java 21
* Paper 1.21.4
* MySQL
* Discord bot application

## Build

Windows:

```bat
gradlew.bat clean build
```

Linux/macOS:

```bash
./gradlew clean build
```

The plugin JAR will be generated in:

```text
build/libs/
```

Copy the generated JAR into:

```text
server/plugins/
```

Then start the Paper server.

---

## Configuration

On first startup, Core Builders creates:

```text
plugins/CoreBuilders/config.yml
```

The runtime configuration should not be committed to Git because it may contain sensitive values.

### Recommended environment variables

```text
DISCORD_BOT_TOKEN
COREBOT_DB_USERNAME
COREBOT_DB_PASSWORD
COREBOT_HYPERGLIDING_API_KEY
```

---

## Discord

Configure the Discord server:

```yaml
discord:
  guild-id: "YOUR_GUILD_ID"

  permissions:
    trusted-staff-role-ids:
      - "ROLE_ID"

    admin-role-ids:
      - "ROLE_ID"

    leadership-role-ids:
      - "ROLE_ID"
```

Role IDs are used instead of role names for security.

The bot only operates in the configured Discord guild.

---

## Database

Example MySQL configuration:

```yaml
database:
  host: "localhost"
  port: 3306
  name: "corebuilders_bot"
  username: ""
  password: ""

  ssl-mode: "DISABLED"

  migrations:
    enabled: true
```

For a local MySQL server, `DISABLED` SSL can be used.

For remote databases, TLS should be enabled.

Flyway automatically creates and upgrades the required tables when migrations are enabled.

---

## Applications

Applications can be started using the Discord application panel or `/apply`.

Example configuration:

```yaml
applications:
  enabled: true

  channels:
    pending: "PENDING_CHANNEL_ID"
    accepted: "ACCEPTED_CHANNEL_ID"
    rejected: "REJECTED_CHANNEL_ID"

  reviewer-role-ids:
    - "LEADER_ROLE_ID"
    - "NOBLE_ROLE_ID"

  approval:
    role-id: "MEMBER_ROLE_ID"

  tickets:
    category: "TICKET_CATEGORY_ID"
```

Application reviewers can:

* Approve
* Reject
* Create a private discussion ticket

Discussion tickets are available to:

* The applicant
* Configured reviewer roles
* The bot

On approval, the configured member role is automatically assigned.

Application questions are configurable and support:

* Short text
* Paragraph text
* File uploads

---

## Application Panel

The bot can maintain a permanent Apply button in a configured Discord channel.

Example:

```yaml
applications:
  entry-panel:
    channel: "APPLICATION_CHANNEL_ID"

    title: "Core Builders Applications"

    description: |
      Interested in joining Core Builders?

      Click Apply Now to begin your application.

    button:
      label: "Apply Now"
```

The created panel message ID is stored automatically.

---

## Discord Commands

Main commands include:

```text
/core profile
/core balance
/core leaderboard
/core achievements
/core transactions
/core stats
/core link
/core newplayers
```

Other systems include:

```text
/profile
/balance
/leaderboard
/contribute
/project
/mission
/shop
/achievements
/application
/award
/reputation
/audit
```

### New Players

Example:

```text
/core newplayers server:2b2t size:5 page:1
```

For `2b2t`, the bot queries the configured Hypergliding API.

Configure:

```yaml
integrations:
  hypergliding:
    base-url: "https://hypergliding.com/api/"
    api-key: ""
    timeout-seconds: 15
```

The integration includes:

* Request rate limiting
* Response caching
* Response size limits
* HTTPS host validation
* Safe public error messages

---

## Minecraft Commands

```text
/core profile
/core balance
/core leaderboard
/core link <code>
/core unlink
```

Discord users can generate a one-time link code and connect their Discord account to their Minecraft UUID.

Both platforms then use the same member profile.

---

## Architecture

```text
Paper 1.21.4
    │
    └── CoreBuilders Plugin
          │
          ├── Minecraft integration
          ├── Discord integration
          ├── Application system
          ├── Business services
          ├── QueryDSL repositories
          │
          └── MySQL
```


Main technologies:

* Java 21
* Paper API
* JDA
* QueryDSL SQL
* HikariCP
* Flyway
* MySQL Connector/J
* Jackson

Business logic is kept separate from Discord, Paper and database infrastructure where possible.

---

## Tests

Run:

```bash
./gradlew test
```

The test suite covers areas including:

* Progression ranks
* Discord command routing
* Command registration
* Application pagination
* Application sessions
* Application formatting
* Hypergliding request handling
* New-player formatting
* Permission-related behavior
* Error formatting

---

## Security Checks

The build includes checks for:

* Embedded secrets
* Raw SQL in runtime persistence code
* Architecture dependency violations
* Missing shaded runtime dependencies
* Unexpected Spring or PostgreSQL classes

Run the full validation with:

```bash
./gradlew clean build
```

Keep production secrets outside Git and use environment variables wherever possible.

---

## Development

When changing persistence code, use QueryDSL instead of handwritten SQL.

Database schema changes should be added as Flyway migrations.

Keep Discord and Minecraft interaction handling separate from business logic so the same services can be reused by both platforms.
