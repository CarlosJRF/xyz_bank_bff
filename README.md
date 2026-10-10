# xyz_bank_bff

Aplicación basada en microservicios que implementa el patrón de diseño **Backend-For-Frontend (BFF)**. Está diseñada para orquestar y adaptar respuestas desde microservicios backend hacia diferentes canales de clientes (Web, Móvil y Cajeros Automáticos/ATM) de manera eficiente y segura.

Forma parte de una solución mayor junto a `xyz_bank_batch` y `kafka_S8`; el documento maestro con la arquitectura global está en [`entrega/readme.md`](entrega/readme.md).

## 🏗️ Arquitectura del Sistema

El ecosistema se compone de cuatro microservicios principales:

1. **MiBff (Gateway / Agregador - Puerto 8443):**
   Actúa como punto de entrada único (con seguridad TLS/SSL) para los clientes. Orquesta llamadas concurrentes hacia los microservicios y adapta las respuestas (DTOs) según las necesidades específicas de la interfaz de usuario que realiza la solicitud.
2. **ms-cuentas (Backend - Puerto 8081):**
   Microservicio encargado del dominio de Cuentas. Persistencia gestionada en base de datos H2 en memoria con precarga de datos desde archivos CSV (`intereses.csv`).
3. **ms-transacciones (Backend - Puerto 8094):**
   Microservicio encargado del dominio de Transacciones. Persistencia en H2 y precarga de historial mediante CSV (`cuentas_anuales.csv`).
4. **ms-clientes (Backend - Puerto 8082):**
   Microservicio encargado de la gestión de clientes (perfiles y datos personales). CRUD completo (`/clientes`) y búsqueda por nombre (`/clientes/buscar?nombre=`). Persistencia en H2 con precarga desde `clientes.csv`.

```mermaid
graph TD;
    Client_Web[💻 App Web] -->|HTTPS :8443| BFF[🛡️ BFF MiBff];
    Client_Movil[📱 App Móvil] -->|HTTPS :8443| BFF;
    Client_Cajero[🏧 Cajero / ATM] -->|HTTPS :8443| BFF;

    BFF -->|HTTP :8081| MS_Cuentas[(📦 ms-cuentas)];
    BFF -->|HTTP :8094| MS_Transacciones[(📦 ms-transacciones)];
    BFF -->|HTTP :8082| MS_Clientes[(📦 ms-clientes)];
```

## ✨ Características Principales

- **Patrón BFF por Canal:** Exposición de endpoints diferenciados (`/api/web/`, `/api/movil/`, `/api/cajero/`) que retornan estrictamente los datos que cada interfaz necesita (por ejemplo, la app móvil solo recibe los últimos 5 movimientos, mientras que la web recibe un resumen detallado y cálculos de ingresos/egresos).
- **Procesamiento Concurrente:** Uso de `CompletableFuture` y un `ExecutorService` de tamaño fijo en el `AgregadorService` para realizar llamadas en paralelo a los microservicios `ms-cuentas` y `ms-transacciones`; el titular se consulta en `ms-clientes` en cuanto se conoce la cuenta. Si `ms-clientes` falla o el titular no existe, el canal responde igual con el cliente en `null`.
- **Seguridad Robusta:**
  - **Transporte:** Encriptación TLS/SSL (HTTPS) mediante Keystore (`bff-keystore.p12`).
  - **Autenticación y Autorización:** Spring Security con `Basic Auth` y control de acceso basado en roles (`ROLE_WEB`, `ROLE_MOVIL`, `ROLE_CAJERO`).
- **Resiliencia REST:** Implementación nativa de `RestClient` (Spring 3.2+) configurado con tiempos de espera estrictos (`connect-timeout` y `read-timeout`) a través de `SimpleClientHttpRequestFactory`.

## 🛠️ Tecnologías Utilizadas

- **Lenguaje:** Java 21
- **Framework Core:** Spring Boot
- **Base de Datos:** H2 (In-Memory)
- **Persistencia:** Spring Data JPA
- **Seguridad:** Spring Security
- **Cliente HTTP:** Spring `RestClient`
- **Utilidades:** Lombok

## 🚀 Guía de Inicio Rápido

### Prerrequisitos
- JDK 21 instalado.
- Apache Maven instalado.

### Pasos para ejecutar

> Alternativa sin instalar Java ni Maven: ver [Ejecutar con Docker](#-ejecutar-con-docker).

1. **Iniciar el Microservicio de Cuentas (`ms-cuentas`):**
   ```bash
   cd ms-cuentas
   ./mvnw spring-boot:run
   ```
   Estará disponible en `http://localhost:8081`.

2. **Iniciar el Microservicio de Transacciones (`ms-transacciones`):**
   ```bash
   cd ms-transacciones
   ./mvnw spring-boot:run
   ```
   Estará disponible en `http://localhost:8094`.

3. **Iniciar el Microservicio de Clientes (`ms-clientes`):**
   ```bash
   cd ms-clientes
   ./mvnw spring-boot:run
   ```
   Estará disponible en `http://localhost:8082`.

4. **Iniciar el Backend-For-Frontend (`MiBff`):**
   ```bash
   cd MiBff
   ./mvnw spring-boot:run
   ```
   Estará disponible en `https://localhost:8443`.

## 🔒 Credenciales de Prueba (Basic Auth)

El BFF está protegido mediante roles específicos para cada canal. Utiliza las siguientes credenciales para probar los endpoints:

| Canal | Usuario | Contraseña | Rol Requerido |
|-------|---------|------------|---------------|
| **Web** | `clienteWeb` | `web123` | `WEB` |
| **Móvil** | `clienteMovil` | `movil123` | `MOVIL` |
| **Cajero (ATM)** | `atmXyz` | `atm123` | `CAJERO` |

## 📡 Endpoints del BFF

### 1. Canal Web (Resumen detallado)
**GET** `https://localhost:8443/api/web/cuentas/{id}`
- **Auth:** Basic (`clienteWeb` / `web123`)
- **Respuesta:** Detalle completo de la cuenta, perfil completo del cliente (RUT, correo, teléfono, dirección, fecha de nacimiento), lista íntegra de movimientos y un resumen calculado (total ingresos, total egresos, saldo neto).

### 2. Canal Móvil (Vista simplificada)
**GET** `https://localhost:8443/api/movil/cuentas/{id}`
- **Auth:** Basic (`clienteMovil` / `movil123`)
- **Respuesta:** Titular, saldo actual y únicamente los **últimos 5 movimientos** optimizados para renderizado en pantallas pequeñas.

### 3. Canal Cajero Automático (Operación rápida)
**GET** `https://localhost:8443/api/cajero/cuentas/{id}/saldo`
- **Auth:** Basic (`atmXyz` / `atm123`)
- **Respuesta:** Consultas ultra-rápidas directas al saldo actual, sin consultar el historial de transacciones. El titular se muestra enmascarado (por ejemplo `Charlie G.`) y nunca se incluyen datos de contacto.

## 🐳 Ejecutar con Docker

Requisitos: Docker con Compose v2. Las imágenes se compilan dentro de Docker (Dockerfile multietapa en cada módulo), así que no hace falta Java ni Maven en el host.

```bash
cp .env.example .env              # opcional: puerto del BFF y memoria
docker compose up -d --build --wait
curl -sk -u clienteWeb:web123 https://localhost:8443/api/web/cuentas/101
docker compose down
```

Solo el BFF se publica (`BFF_PORT`, por defecto 8443). Los tres microservicios no tienen autenticación propia, por eso viven únicamente en la red interna `backend`; el BFF los alcanza por nombre de servicio (`MS_CUENTAS_BASE_URL`, `MS_TRANSACCIONES_BASE_URL` y `MS_CLIENTES_BASE_URL` reemplazan las URL `localhost` de `application.properties`). El certificado del BFF es autofirmado: usa `-k` en curl o desactiva la verificación SSL en Postman.

## ☁️ Despliegue en AWS EC2

`deploy/ec2-setup.sh` prepara una instancia Amazon Linux 2023 y levanta los tres sistemas de la solución (`kafka_S8`, `xyz_bank_bff` y `xyz_bank_batch`), con pruebas de humo al final. Instancia, Security Group y pasos completos en [`entrega/despliegue.md`](entrega/despliegue.md).

---
