# DocMind AI

Document Q&A service built with Spring Boot. Upload PDF or text files and ask questions about
them; answers come with references to the source passages.

## Tech stack

- Java 21, Spring Boot 3.5
- Spring AI (Ollama and OpenAI)
- PostgreSQL with pgvector
- Flyway, Testcontainers, Docker

## Getting started

Requirements: Java 21 and Docker.

```bash
# start PostgreSQL with the pgvector extension
docker compose up -d

# run the application
./mvnw spring-boot:run
```

Health check: <http://localhost:8080/actuator/health>

## Build and test

```bash
./mvnw verify
```

## License

[MIT](LICENSE)
