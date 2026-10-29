# Multi-tenant database configuration

## Overview

Each tenant is routed to its own database. The user selects a tenant before
login; the frontend sends `X-Tenant-ID`, the BFF translates it to
`X-Company-ID`, and the backend resolves the configured datasource for that
ID. After login, the backend derives tenant context from the signed
`company_id` JWT claim rather than trusting a client-supplied tenant header.

There is no cross-tenant fallback: an unknown tenant, missing tenant context,
or absent datasource configuration must fail rather than use another tenant's
database.

## Backend property prefix

The Java configuration binds tenant entries under **`varista.tenants`**. This
prefix is an application configuration key; it is unrelated to the MySQL
database naming convention. Do not rename it to `syncra.tenants` unless the
backend's configuration binding is changed too.

Use external configuration kept outside source control:

```properties
varista.tenants[101].name=Leadac
varista.tenants[101].url=jdbc:mysql://192.168.0.228:3306/syncra_leadac?useSSL=false&serverTimezone=UTC
varista.tenants[101].username=syncra_leadac_user
varista.tenants[101].password=${TENANT_LEADAC_DB_PASSWORD}
```

The current Leadac example is stored locally in
`config/leadac.properties`. Supply the password from an environment variable or
secret manager before starting the service:

```powershell
$env:TENANT_LEADAC_DB_PASSWORD = "<Leadac database password>"
.\mvnw.cmd spring-boot:run --spring.config.additional-location=optional:file:./config/leadac.properties
```

A restart is required whenever a tenant is added or its connection settings
change because tenant connection pools are initialized at startup.

## Datasource settings

`spring.datasource.url` supports an environment override with a local H2
fallback:

```properties
spring.datasource.url=${DB_URL:jdbc:h2:file:./data/h2/varista;MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE}
```

`DB_URL` takes precedence when set; otherwise the local H2 database is used for
development. It is not the tenant isolation mechanism. Tenant traffic uses the
routing datasource configured by `varista.tenants[...]`.

An embedded H2 file database can be locked by another Java process, IDE
connection, or H2 console. Stop the process holding the file rather than
deleting database files.

## Leadac provisioning

The following PowerShell script creates the Leadac MySQL database and user,
then writes an external backend configuration file. It prompts for both
passwords and does not save either password in the properties file.

```powershell
param(
    [string]$MySqlHost = "192.168.0.228",
    [int]$MySqlPort = 3306,
    [string]$MySqlAdminUser = "root",
    [string]$ServicePath = "D:\Sandbox\Varista\varista-service"
)

$ErrorActionPreference = "Stop"
$tenantId = 101
$tenantName = "Leadac"
$databaseName = "syncra_leadac"
$databaseUser = "syncra_leadac_user"
$mysqlExe = "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
$configDirectory = Join-Path $ServicePath "config"
$configFile = Join-Path $configDirectory "leadac.properties"

$adminPassword = Read-Host "Enter MySQL password for $MySqlAdminUser" -AsSecureString
$adminPasswordPlain = [System.Net.NetworkCredential]::new("", $adminPassword).Password
$tenantPassword = Read-Host "Enter a strong password for $databaseUser" -AsSecureString
$tenantPasswordPlain = [System.Net.NetworkCredential]::new("", $tenantPassword).Password

if (-not (Test-NetConnection -ComputerName $MySqlHost -Port $MySqlPort -InformationLevel Quiet)) {
    throw "Cannot reach MySQL at $MySqlHost`:$MySqlPort."
}

$sql = @"
CREATE DATABASE IF NOT EXISTS ``$databaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$databaseUser'@'%' IDENTIFIED BY '$tenantPasswordPlain';
ALTER USER '$databaseUser'@'%' IDENTIFIED BY '$tenantPasswordPlain';
GRANT ALL PRIVILEGES ON ``$databaseName``.* TO '$databaseUser'@'%';
FLUSH PRIVILEGES;
"@

$sql | & $mysqlExe `
    --protocol=TCP `
    --connect-timeout=10 `
    --host=$MySqlHost `
    --port=$MySqlPort `
    --user=$MySqlAdminUser `
    --password=$adminPasswordPlain `
    --default-character-set=utf8mb4

if ($LASTEXITCODE -ne 0) {
    throw "MySQL provisioning failed with exit code $LASTEXITCODE."
}

New-Item -ItemType Directory -Path $configDirectory -Force | Out-Null
@"
# Keep this file outside source control.
varista.tenants[$tenantId].name=$tenantName
varista.tenants[$tenantId].url=jdbc:mysql://$MySqlHost`:$MySqlPort/$databaseName?useSSL=false&serverTimezone=UTC
varista.tenants[$tenantId].username=$databaseUser
varista.tenants[$tenantId].password=`${TENANT_LEADAC_DB_PASSWORD}
"@ | Set-Content -Path $configFile -Encoding utf8
```

The `--password=...` form lets `mysql.exe` run non-interactively. MySQL prints
a warning because command-line passwords can be visible to local process
inspection; use a secret manager for production automation.

For production, restrict the MySQL account host to the backend host or subnet
instead of `%`, use a distinct random password, and grant only the privileges
required by the application.

## Schema and data preparation

Creating a database does not initialize application tables or data. Export the
schema from a trusted source database and apply it to the tenant database:

```powershell
mysqldump.exe --host 192.168.0.228 --port 3306 --user root --password --no-data --routines --triggers <source_database> > .\tenant-schema.sql
mysql.exe --host 192.168.0.228 --port 3306 --user syncra_leadac_user --password syncra_leadac < .\tenant-schema.sql
```

Before enabling a tenant, verify the database, account permissions, schema,
required tenant records, backups, and a successful tenant-aware login. Migrate
only data belonging to that tenant; do not copy a shared database wholesale to
every tenant.

## Security requirements

- Never commit database passwords, JWT secrets, or external `config/*.properties` files.
- Do not expose database provisioning or credential management through a public API.
- Keep the frontend tenant ID aligned with the backend `varista.tenants` key.
- Require tenant context for login and password-reset requests.
- Restrict tenant database users and rotate their credentials through operations tooling.
