# voiceover-backend

## Requirements

- Java 21
- MySQL 8+

## Run locally

Set the database connection variables before starting the application:

```powershell
$env:DB_URL="jdbc:mysql://localhost:3306/voiceover?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
$env:DB_USERNAME="voiceover"
$env:DB_PASSWORD="voiceover"
.\mvnw spring-boot:run
```

The same variables can be placed in a local `.env` file for use with a
development environment. `.env` is ignored by Git and must not be committed.

## Build and test

```powershell
.\mvnw verify
```

## Container image

The GitHub Actions workflow runs tests against MySQL on pushes and pull
requests. A successful push to `main` builds and publishes
`ghcr.io/<owner>/voiceover-backend` with both a commit SHA tag and `latest`.

To run the image locally, pass the database variables explicitly:

```powershell
docker build -t voiceover-backend .
docker run --rm -p 8080:8080 `
  -e DB_URL="jdbc:mysql://host.docker.internal:3306/voiceover?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC" `
  -e DB_USERNAME="voiceover" `
  -e DB_PASSWORD="voiceover" `
  voiceover-backend
```