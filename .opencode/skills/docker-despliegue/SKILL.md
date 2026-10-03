---
name: docker-despliegue
description: "Usa esta skill cuando configures contenedores Docker, pipelines CI/CD o infraestructura de despliegue para aplicaciones Spring Boot y FastAPI. Cubre multi-stage builds, docker-compose para entornos POS, GitHub Actions y Nginx reverse proxy."
---

# Docker - Despliegue

Skill especializada para el agente `devops-specialist`. Proporciona templates, fragmentos de configuracion y buenas practicas para Docker, CI/CD y despliegue con Nginx en el contexto del proyecto app-comida.

## Contexto del proyecto app-comida

- **Backend**: Java + Spring Boot + Maven. Base de datos H2 en desarrollo; en produccion se recomienda PostgreSQL.
- **Frontend**: Vue 3 + TypeScript + Vite + Tailwind CSS v4.
- **Alternativa backend**: Python + FastAPI + SQLAlchemy + Alembic.
- **Infraestructura objetivo**: Docker Compose para entornos POS (punto de venta) y servidores dedicados.

---

## 1. Dockerfile multi-stage: Spring Boot

```dockerfile
# ---- Stage 1: Build ----
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ---- Stage 2: Runtime ----
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8080/actuator/health || exit 1
USER appuser
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### .dockerignore para Spring Boot

```dockerignore
target/
*.class
*.jar
!target/*.jar
.git
.gitignore
README.md
.vscode/
.idea/
*.iml
```

---

## 2. Dockerfile multi-stage: FastAPI

```dockerfile
# ---- Stage 1: Build ----
FROM python:3.12-slim AS build
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir --user -r requirements.txt

# ---- Stage 2: Runtime ----
FROM python:3.12-slim
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
WORKDIR /app
COPY --from=build /root/.local /root/.local
COPY ./app ./app
ENV PATH=/root/.local/bin:$PATH
EXPOSE 8000
HEALTHCHECK --interval=10s --timeout=5s --retries=3 \
  CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1
USER appuser
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
```

---

## 3. docker-compose.yml (ecosistema completo)

```yaml
version: "3.9"

services:

  postgres:
    image: postgres:16-alpine
    container_name: app-comida-db
    restart: unless-stopped
    volumes:
      - pgdata:/var/lib/postgresql/data
    environment:
      POSTGRES_DB: appcomida
      POSTGRES_USER: ${DB_USER:-appcomida}
      POSTGRES_PASSWORD: ${DB_PASSWORD:-secret}
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U appcomida"]
      interval: 10s
      timeout: 5s
      retries: 5
    networks:
      - app-network

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: app-comida-backend
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/appcomida
      SPRING_DATASOURCE_USERNAME: ${DB_USER:-appcomida}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD:-secret}
      SPRING_PROFILES_ACTIVE: ${SPRING_PROFILES_ACTIVE:-prod}
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:8080/actuator/health"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - app-network

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: app-comida-frontend
    restart: unless-stopped
    depends_on:
      - backend
    healthcheck:
      test: ["CMD", "wget", "--quiet", "--tries=1", "--spider", "http://localhost:80"]
      interval: 10s
      timeout: 5s
      retries: 3
    networks:
      - app-network

  nginx:
    image: nginx:1.26-alpine
    container_name: app-comida-nginx
    restart: unless-stopped
    ports:
      - "80:80"
      - "443:443"
    volumes:
      - ./nginx/nginx.conf:/etc/nginx/nginx.conf:ro
      - ./nginx/ssl:/etc/nginx/ssl:ro
      - static-files:/usr/share/nginx/html
    depends_on:
      - backend
      - frontend
    healthcheck:
      test: ["CMD", "nginx", "-t"]
      interval: 30s
      timeout: 5s
      retries: 3
    networks:
      - app-network

volumes:
  pgdata:
  static-files:

networks:
  app-network:
    driver: bridge
```

---

## 4. Nginx: configuracion como reverse proxy

```nginx
events {
    worker_connections 1024;
}

http {
    include       /etc/nginx/mime.types;
    default_type  application/octet-stream;

    # SSL termination
    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         HIGH:!aNULL:!MD5;

    # Frontend (static files)
    server {
        listen 80;
        server_name app-comida.local;
        return 301 https://$host$request_uri;
    }

    server {
        listen 443 ssl http2;
        server_name app-comida.local;

        # Frontend static
        location / {
            proxy_pass http://frontend:80;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Backend API
        location /api/ {
            proxy_pass http://backend:8080;
            proxy_set_header Host $host;
            proxy_set_header X-Real-IP $remote_addr;
            proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
            proxy_set_header X-Forwarded-Proto $scheme;
        }

        # Static assets con cache
        location /assets/ {
            alias /usr/share/nginx/html/assets/;
            expires 1y;
            add_header Cache-Control "public, immutable";
        }
    }
}
```

---

## 5. GitHub Actions: CI/CD

```yaml
name: CI/CD Pipeline

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main]

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: ${{ github.repository }}

jobs:
  # ---- CI: Build & Test ----
  ci:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: 21
          distribution: temurin
          cache: maven

      - name: Build & Test (Backend)
        run: |
          cd backend
          mvn verify -B

      - name: Set up Node
        uses: actions/setup-node@v4
        with:
          node-version: 20
          cache: npm
          cache-dependency-path: frontend/package-lock.json

      - name: Build & Test (Frontend)
        run: |
          cd frontend
          npm ci
          npm run build

      - name: Lint Dockerfiles
        uses: hadolint/hadolint-action@v3
        with:
          dockerfile: backend/Dockerfile frontend/Dockerfile

  # ---- CD: Build & Push & Deploy ----
  cd:
    needs: ci
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Log in to GitHub Container Registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ github.actor }}
          password: ${{ secrets.GITHUB_TOKEN }}

      - name: Build & Push Backend
        uses: docker/build-push-action@v6
        with:
          context: ./backend
          file: ./backend/Dockerfile
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/backend:${{ github.sha }}

      - name: Build & Push Frontend
        uses: docker/build-push-action@v6
        with:
          context: ./frontend
          file: ./frontend/Dockerfile
          push: true
          tags: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/frontend:${{ github.sha }}

      - name: Deploy via SSH
        uses: appleboy/ssh-action@v1
        with:
          host: ${{ secrets.DEPLOY_HOST }}
          username: ${{ secrets.DEPLOY_USER }}
          key: ${{ secrets.DEPLOY_SSH_KEY }}
          script: |
            cd /opt/app-comida
            docker compose pull
            docker compose up -d --remove-orphans
            docker system prune -af
```

---

## 6. Estrategias de despliegue para POS

### Zero-downtime con blue/green

```yaml
# docker-compose.bluegreen.yml
services:
  backend-blue:
    build: ./backend
    container_name: app-comida-backend-blue
    ports:
      - "8081:8080"
    networks:
      - app-network

  backend-green:
    build: ./backend
    container_name: app-comida-backend-green
    ports:
      - "8082:8080"
    networks:
      - app-network
```

Script de conmutacion:

```bash
#!/bin/bash
# deploy-bluegreen.sh
ACTIVE=$(docker ps --filter "name=backend-blue" --format "{{.Names}}")

if [ "$ACTIVE" == "app-comida-backend-blue" ]; then
  IDLE="green"
  IDLE_PORT=8082
else
  IDLE="green"
  IDLE_PORT=8081
fi

echo "Desplegando en backend-$IDLE..."
docker compose -f docker-compose.bluegreen.yml up -d backend-$IDLE --build

sleep 5

echo "Verificando healthcheck..."
if curl -f http://localhost:$IDLE_PORT/actuator/health; then
  echo "OK. Conmutando trafico a backend-$IDLE..."
  sed -i "s/proxy_pass http:\/\/backend:[0-9]\+/proxy_pass http:\/\/backend-$IDLE:$IDLE_PORT/" nginx/nginx.conf
  docker exec app-comida-nginx nginx -s reload

  echo "Deteniendo instancia anterior..."
  if [ "$IDLE" == "green" ]; then
    docker compose -f docker-compose.bluegreen.yml stop backend-blue
  else
    docker compose -f docker-compose.bluegreen.yml stop backend-green
  fi
else
  echo "Healthcheck fallo. Revirtiendo..."
  docker compose -f docker-compose.bluegreen.yml stop backend-$IDLE
  exit 1
fi
```

### Rollback

```bash
#!/bin/bash
# rollback.sh
TAG=$1
if [ -z "$TAG" ]; then
  echo "Uso: ./rollback.sh <tag-anterior>"
  exit 1
fi

docker pull ghcr.io/app-comida/backend:$TAG
docker tag ghcr.io/app-comida/backend:$TAG ghcr.io/app-comida/backend:latest
docker compose up -d backend
echo "Rollback completado a tag $TAG"
```

---

## 7. Manejo de variables de entorno y secrets

```bash
# .env.example (no versionar)
DB_USER=appcomida
DB_PASSWORD=secret
SPRING_PROFILES_ACTIVE=prod
JWT_SECRET=change-me
```

```yaml
# docker-compose.override.yml (no versionar)
services:
  backend:
    environment:
      JWT_SECRET: ${JWT_SECRET}
```

En GitHub Actions, usar **secrets** del repositorio:
- `DEPLOY_HOST`, `DEPLOY_USER`, `DEPLOY_SSH_KEY`
- `DB_PASSWORD`, `JWT_SECRET`

Para entornos POS locales, usar **Docker secrets**:

```yaml
services:
  backend:
    secrets:
      - db_password
      - jwt_secret

secrets:
  db_password:
    file: ./secrets/db_password.txt
  jwt_secret:
    file: ./secrets/jwt_secret.txt
```

---

## 8. Optimizacion de imagenes

| Practica | Detalle |
|---|---|
| **Multi-stage** | Separa build de runtime. La imagen final solo contiene JRE/python-slim + artefacto. |
| **.dockerignore** | Excluye `target/`, `node_modules/`, `.git/`, archivos locales. |
| **Orden de capas** | `pom.xml`/`requirements.txt` primero (cache de dependencias), luego `src/`. |
| **Imagen base minima** | `alpine` para Java (JRE), `slim` para Python. |
| **No-root user** | Crea `appuser` y usa `USER appuser`. |
| **Healthcheck** | Integrado en el Dockerfile; evita herramientas externas pesadas. |
| **Labels** | Anade `org.opencontainers.image.source` para trazabilidad. |

```dockerfile
LABEL org.opencontainers.image.source="https://github.com/app-comida/app-comida"
LABEL org.opencontainers.image.version="1.0.0"
```

---

## 9. Colaboracion

- Coordinar con `backend-developer` para revisar las propiedades de Spring Boot (actuator, perfiles, puertos).
- Coordinar con `frontend-developer` para la configuracion de Vite (base path, proxy dev).
- Coordinar con `database-architect` para validar healthcheck de PostgreSQL y migraciones con Alembic/Flyway.
