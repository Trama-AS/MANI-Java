# AGENTS.md — MANI-Rules-Java (Motor de Reglas)

Bienvenido al repositorio **MANI-Rules-Java**. Este archivo define el rol y las directrices operativas de IA y desarrollo para el microservicio de Reglas de Negocio del ecosistema MANI.

---

## 1. Rol y Responsabilidad del Repositorio
* **Tecnología:** Java 17 / Spring Boot 3.3.x.
* **Puerto:** `8080` (interno).
* **Dominio Funcional:** Motor de cálculo y validación de reglas de negocio:
  - **RF-02:** Evaluación de condiciones dinámicas y reglas por tenant.
  - **RF-13:** Ranking y ordenamiento ponderado de aliados para despacho.
  - **RF-16 / RF-22:** Validación contra tarifario, recargos por día/hora y cálculo de precios finales.

---

## 2. Topología de Comunicación y API Gateway

```
[ MANI-Flutter ] 
       │
       ▼ (Peticiones al puerto 80)
[ MANI-APIGateway ]
       │
       ▼ (/api/v1/rules/* ──> puerto 8080)
[ MANI-Rules-Java ] ◄──(Llamadas internas REST)─── [ MANI-Node ] / [ MANI-Dispatch-DotNet ]
```

### Relación con otros repositorios:
1. **`MANI-APIGateway`:** Redirige todas las peticiones con prefijo `/api/v1/rules/` hacia este servicio en el puerto `8080`.
2. **`MANI-Node`:** Consulta este servicio cuando el backend Core necesita cotizar un servicio o calcular el precio final antes de registrar la orden.
3. **`MANI-Dispatch-DotNet`:** Consulta la ruta `/api/v1/rules/rank-allies` para obtener los candidatos priorizados antes de lanzar las solicitudes de asignación.
4. **`MANI-Flutter`:** Invoca el cálculo de tarifas a través del Gateway.

---

## 3. Protocolos y Estándares
* **Endpoints:**
  - `GET /health` y `GET /api/v1/rules/health`: Healthcheck.
  - `POST /api/v1/rules/evaluate-rate`: Cálculo dinámico de tarifa y desglose de recargos.
  - `POST /api/v1/rules/rank-allies`: Ordenamiento y ranking de profesionales disponibles.
* **Encabezados Requeridos:**
  - `X-Correlation-ID`: Identificador de correlación para observabilidad y trazabilidad distribuida.
  - `Authorization`: Token JWT para validaciones contextuales por tenant.

---

## 4. Comandos de Desarrollo
```bash
# Compilar con Maven
mvn clean package

# Ejecutar localmente
mvn spring-boot:run

# Ejecutar pruebas
mvn test
```
