# MANI-Java — Motor de Reglas de Negocio de MANI

Microservicio del ecosistema **MANI** (*TRAMA · Ingeniería de Software*), desarrollado en **Java 17 y Spring Boot 3.3.x**, responsable del motor de reglas de negocio y cálculo dinámico de tarifas.

Este repositorio encapsula la lógica de dominio requerida para la evaluación de condiciones por tenant, verificación de límites tarifarios y ranking ponderado de profesionales.

---

## 🏛️ Rol en la Arquitectura SOA (ADR-0019)

Dentro de la arquitectura de servicios de MANI, **`MANI-Java`** opera como un servicio de lógica de decisión y cálculo puro:

* **Puerto Interno:** `8080`
* **Exposición Externa:** Vía **`MANI-APIGateway`** bajo el prefijo `/api/v1/rules/*`.
* **Capacidades de Negocio (SRS V3):**
  - **RF-02:** Evaluación de reglas configurables por tenant sin almacenar valores fijos en código.
  - **RF-13:** Algoritmo de ranking y ordenamiento de aliados por calificación y experiencia.
  - **RF-16 / RF-22:** Validación contra tarifario base, cálculo de recargos por día/horario y límites de cobro.
* **Interacción en el Ecosistema:**
  - Puede ser consultado por **`MANI-Node`** durante el ciclo de cotización previa al guardado.
  - Puede ser consultado por **`MANI-.NET`** para priorizar aliados antes del despacho.
  - Expuesto a **`MANI-Flutter`** a través del API Gateway para cotizaciones dinámicas en tiempo real.

```mermaid
flowchart LR
    GW["MANI-APIGateway (Puerto :80)"] -->|/api/v1/rules/*| Java["MANI-Java (:8080)"]
    Node["MANI-Node"] -.->|Consultar Tarifas| Java
    DotNet["MANI-.NET"] -.->|Consultar Ranking| Java
```

---

## 🚀 Endpoints Principales

| Método | Ruta en Gateway | Descripción | Body Requerido |
| :--- | :--- | :--- | :---: |
| `GET` | `/api/v1/rules/health` | Verificación de estado del motor de reglas. | No |
| `POST` | `/api/v1/rules/evaluate-rate` | Cálculo de precio final con desglose de recargos. | Sí (JSON) |
| `POST` | `/api/v1/rules/rank-allies` | Ordenamiento y ponderación de aliados para servicio. | Opcional |
| `POST` | `/api/v1/rules/categories/evaluate` | Reglas operativas de la categoría de servicio (US-03.1.1-M6). | Sí (JSON) |

> El Gateway recorta `/api/v1/rules` antes de reenviar: cada ruta también responde sin el prefijo. Core la invoca completa por la red interna (`RULES_SERVICE_URL`, p. ej. `http://rules-service:8080`).

### Reglas de categoría (`POST /api/v1/rules/categories/evaluate`)

Reemplaza las validaciones que vivían en `crear_categoria_servicio()` (migración 003, SCRUM-857) y en Core Node. Contrato publicado en `MANI-APIGateway/docs/openapi/rules.yaml`.

**Encabezados** (los pone el Gateway tras validar el JWT, o Core al llamar internamente): `X-Tenant-Id` (obligatorio; el tenant **nunca** se toma del cuerpo), `X-User-Role` (obligatorio; `CREATE`/`UPDATE` exigen `ADMIN`), `X-Correlation-ID`.

```json
{ "categoryId": null, "context": { "operation": "CREATE", "name": "  Plomería  Express ", "flujoOperativo": "tarifa_estandar" } }
```

**Respuesta `200`** (la regla incumplida es `allowed=false`, no un error HTTP):
```json
{ "allowed": true, "ruleSetVersion": "category-rules-v1", "violations": [],
  "normalized": { "name": "Plomería Express", "flujoOperativo": "TARIFA_ESTANDAR" },
  "tenantId": "…", "categoryId": null, "operation": "CREATE", "correlationId": "…" }
```

| Operación | Reglas | Código |
| :--- | :--- | :--- |
| `CREATE` | nombre normalizado de 3 a 60 caracteres con al menos una letra | `MANI-CAT-422N` |
| `CREATE` | flujo operativo en `COTIZACION_PREVIA` / `TARIFA_ESTANDAR` | `MANI-CAT-422F` |
| `UPDATE` | las mismas, solo sobre los campos enviados | `422N` / `422F` |
| `OPERATE` | la categoría debe estar activa | `MANI-CAT-422C` |

Errores: `400 VALIDATION_ERROR | TENANT_REQUIRED`, `401 UNAUTHORIZED`, `403 FORBIDDEN | TENANT_MISMATCH`. El nombre único por tenant (`409`) sigue en el índice `ux_categoria_tenant_nombre`, porque necesita estado.

Los parámetros viven en `application.properties` (`mani.rules.category.*`) con override por tenant (`mani.rules.category.tenants[<uuid>].*`): cambiarlos solo exige redesplegar Rules. Un flujo nuevo además requiere ampliar el `CHECK ck_categoria_flujo` en BD.

### Ejemplo: Evaluación de Tarifa (`POST /api/v1/rules/evaluate-rate`)
**Petición:**
```json
{
  "basePrice": 45000,
  "isWeekend": true,
  "category": "Semipermanente"
}
```

**Respuesta (`200 OK`):**
```json
{
  "service": "MANI-Java",
  "basePrice": 45000.0,
  "surcharge": 5000.0,
  "finalPrice": 50000.0,
  "ruleApplied": "REGLA-FIN-DE-SEMANA (+5000)",
  "status": "APPLIED",
  "correlationId": "c9a4b2a8-1234-5678-90ab-cdef12345678"
}
```

---

## 🛠️ Stack Tecnológico

* **Lenguaje:** Java 17 LTS (Eclipse Temurin).
* **Framework:** Spring Boot 3.3.4 (Spring Web, Spring Test).
* **Gestor de Dependencias:** Apache Maven 3.9+.
* **Contenerización:** Docker multi-stage (Maven builder + JRE Alpine).

---

## ⚙️ Configuración (`application.properties`)

```properties
server.port=8080
spring.application.name=mani-java

# Formato de logs con correlation ID
logging.pattern.console=%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - [Correlation: %X{X-Correlation-ID}] %msg%n
```

---

## 💻 Ejecución Local

### Opción 1: Con Maven
```bash
# Compilar proyecto
mvn clean package -DskipTests

# Ejecutar aplicación
mvn spring-boot:run
```

### Opción 2: Con Docker
```bash
# Construir imagen Docker
docker build -t mani-rules-java:local .

# Ejecutar contenedor en puerto 8080
docker run -d -p 8080:8080 --name mani-rules mani-rules-java:local
```

---

## 🧪 Pruebas y cobertura

Pruebas con **JUnit 5** (vía `spring-boot-starter-test`) y cobertura con **JaCoCo**.

```bash
# Ejecutar pruebas y generar el reporte de cobertura
mvn verify
```

El reporte queda en `target/site/jacoco/` (HTML) y `target/site/jacoco/jacoco.xml`. `mvn verify` **falla** si la cobertura baja de 85 % de líneas u 80 % de ramas (`jacoco:check`, umbrales en `pom.xml`).

| Suite | Qué cubre |
| :--- | :--- |
| `CategoryRuleEvaluatorTest` | Reglas puras: límites 2/3/60/61, conteo por caracteres (emoji), letras Unicode, normalización, flujos, UPDATE parcial, OPERATE, reglas configurables, determinismo |
| `CategoryRulesPropertiesTest` | Binding de `mani.rules.category.*`, override por tenant, versión trazable, configuración incoherente que impide el arranque |
| `CategoryRulesControllerTest` | Contrato HTTP: forma de respuesta, prefijo del Gateway, roles, tenant (`TENANT_REQUIRED`, `TENANT_MISMATCH`), 400/401/403/415 |
| `UnexpectedErrorTest`, `WebSkeletonTest`, `HealthControllerSmokeTest` | 500 uniforme sin filtrar detalle, correlation ID, CORS, 404/405 |

El workflow `CI` ejecuta `mvn -B verify` en cada Pull Request y push a `main`/`develop`, publica cobertura y reportes de Surefire, y corre **SonarCloud con `sonar.qualitygate.wait=true`** (el PR falla si no pasa el Quality Gate). Ese paso se activa cuando el repositorio tiene el secret `SONAR_TOKEN` y el proyecto `Trama-AS_MANI-Java` existe en SonarCloud (DevOps); mientras tanto CI emite una advertencia en vez de fallar.

Las pruebas de contrato extremo a extremo (Gateway → Rules) viven en `MANI-APIGateway` (`postman/MANI-Rules.postman_collection.json`, job `contract-gate-rules`).

---

## 👥 Equipo y Gobernanza
* **Organización:** [TRAMA · Ingeniería de Software](https://github.com/Trama-AS)
* **Repositorio Oficial:** [MANI-Java](https://github.com/Trama-AS/MANI-Java)
