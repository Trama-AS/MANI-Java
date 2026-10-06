# CLAUDE.md — Guía de Desarrollo para MANI-Rules-Java

Este documento sintetiza las reglas de arquitectura y patrones de código para trabajar en el microservicio de Reglas de Negocio de MANI.

---

## 1. Contexto de Arquitectura
* **Microservicio:** Motor de Reglas (Java / Spring Boot).
* **Consumidores:** Tráfico externo vía **`MANI-APIGateway`** (`/api/v1/rules/*`) y llamadas REST internas desde **`MANI-Node`** y **`MANI-Dispatch-DotNet`**.
* **Propósito:** Evaluar y calcular tarifas y rankings sin almacenar reglas rígidas en duro por código (soporte configurable por tenant).

---

## 2. Convenciones de Código
* **Estructura de Paquetes:**
  - `com.trama.mani.rules.controller`: Exposición de endpoints REST.
  - `com.trama.mani.rules.service`: Algoritmos de cálculo de tarifas y evaluación de condiciones.
  - `com.trama.mani.rules.dto`: Objetos de transferencia de datos de entrada/salida.
* **Inmutabilidad y Pureza:** Los métodos de cálculo de reglas deben ser en lo posible funciones deterministas (mismas entradas generan el mismo resultado de cálculo).
* **Logging:** Propagar en los logs el encabezado `X-Correlation-ID` recibido.
