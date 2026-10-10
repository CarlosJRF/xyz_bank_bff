# Instrucciones para ejecutar y probar la solución

Guía paso a paso para desplegar los tres sistemas en una instancia EC2 y probarlos en orden: primero el batch, luego el BFF y por último la plataforma `kafka_S8`. Sirve para las pruebas y para grabar el video: cada prueba indica el método, la autenticación y la respuesta esperada.

## 0. Qué se prueba y con qué herramienta

| Orden | Sistema | Herramienta | Qué demuestra |
|---|---|---|---|
| 1 | `xyz_bank_batch` | **SSH** (el batch no expone HTTP) | Limpia y valida los CSV legados y puebla las tablas de PostgreSQL |
| 2 | `xyz_bank_bff` | **Postman** (HTTPS :8443) | Respuesta distinta por canal y según el cliente |
| 3 | `kafka_S8` | **Postman** (HTTP :80) y SSH | OAuth2, ingesta por Kafka, resiliencia y escalado |

Dos aclaraciones para no confundirse al explicarlo:

- **El batch no se llama desde Postman.** `xyz_bank_batch` es un job de línea de comandos (`masterJob`): se ejecuta con `docker compose run` y su resultado se verifica en PostgreSQL.
- **Cada sistema lee sus propios datos.** El BFF devuelve los datos de `ms-cuentas`, `ms-transacciones` y `ms-clientes`, que cargan sus CSV en memoria al arrancar. No lee las tablas que deja el batch. Tampoco `kafka_S8`, que usa su propio PostgreSQL (`banking`). Los tres sistemas están desacoplados (ver `readme.md`, secciones 3 y 7).

### Resumen de pruebas

| Id | Sistema | Método y ruta | Auth | Esperado |
|---|---|---|---|---|
| A1–A3 | Batch (SSH) | `docker compose run --rm batch` y consultas SQL | — | Job `COMPLETED`; 392 / 320 / 603 filas |
| B1 | BFF | `GET /api/web/cuentas/101` | Basic `clienteWeb` | 200, perfil completo y resumen |
| B2 | BFF | `GET /api/movil/cuentas/101` | Basic `clienteMovil` | 200, titular y 5 movimientos |
| B3 | BFF | `GET /api/cajero/cuentas/101/saldo` | Basic `atmXyz` | 200, titular enmascarado y saldo |
| B4 | BFF | `GET /api/cajero/cuentas/{id}/saldo` (varias) | Basic `atmXyz` | 200, titular según la cuenta |
| B5 | BFF | `GET /api/web/cuentas/129` | Basic `clienteWeb` | 200, `cliente: null` |
| B6–B8 | BFF | Sin credenciales / canal ajeno / cuenta inexistente | — | 401 / 403 / 500 |
| K1 | kafka_S8 | `POST /oauth2/token` | Basic `banking-client` | 200, JWT |
| K2 | kafka_S8 | `GET /api/stats` sin token | — | 401 |
| K3 | kafka_S8 | `GET /api/stats` (antes de la ingesta) | Bearer | 200, todo en cero |
| K4 | kafka_S8 | `POST /api/ingestion/run` | Bearer | 200, informe: 1.552 aceptadas, 1.448 rechazadas |
| K5 | kafka_S8 | `GET /api/stats` (después) | Bearer | 200, conteos poblados |
| K6 | kafka_S8 | `GET /api/stats/rejected?...` | Bearer | 200, filas rechazadas con motivo |
| K7 | kafka_S8 | `GET /api/accounts` y `/api/accounts/101/summary` | Bearer | 200 |
| K8 | kafka_S8 | `GET /api/transactions/summary` | Bearer | 200 |
| K9 | kafka_S8 | `GET /api/client/stats` | Bearer | 200, `source: "live"` |
| K10 | kafka_S8 | `POST /api/ingestion/run` con token de solo lectura | Bearer | 403 |
| K11 | kafka_S8 | Segunda ingesta | Bearer | 200, mismos conteos |
| K12 | kafka_S8 | Circuit breaker (SSH + Postman) | Bearer | `source: "cache"` mientras el servicio está caído |

## 1. Desplegar en la instancia EC2

### 1.1 Crear la instancia

Los valores completos están en [`despliegue.md`](despliegue.md). En resumen:

| Campo | Valor |
|---|---|
| AMI | Amazon Linux 2023 (x86_64) |
| Tipo | `t3.xlarge` (4 vCPU, 16 GiB) |
| Disco | 30 GiB gp3 |
| Security Group (entrada) | 22, 80 y 8443, solo desde tu IP |

### 1.2 Ejecutar el script de despliegue

Por SSH (o pegando el script como *User data* al crear la instancia):

```bash
ssh -i clave.pem ec2-user@<IP-publica>
curl -fsSL https://raw.githubusercontent.com/CarlosJRF/xyz_bank_bff/main/deploy/ec2-setup.sh -o ec2-setup.sh
sudo bash ec2-setup.sh
```

### 1.3 Orden en que se despliega

El script levanta los sistemas siempre en este orden y espera a que cada uno quede `healthy` antes de seguir:

1. **`kafka_S8`**: Kafka, PostgreSQL, Config Server, Eureka, Auth Server, API Gateway, ingesta, procesamiento y resilient-client (9 contenedores). Va primero por ser el más pesado.
2. **`xyz_bank_bff`**: `ms-cuentas`, `ms-transacciones`, `ms-clientes` y `MiBff` (4 contenedores). El BFF espera a que sus tres microservicios estén sanos.
3. **`xyz_bank_batch`**: PostgreSQL (1 contenedor) y una ejecución del job `masterJob`.

No hay dependencias entre los tres sistemas; el orden solo reparte la espera. Tarda unos 11 minutos en frío, porque compila las 12 imágenes en la propia instancia.

### 1.4 Verificar el despliegue

Al terminar, el script ejecuta tres pruebas de humo y debe mostrar:

```
==> Smoke tests
  OK     kafka_S8: token OAuth2 y GET /api/stats por el gateway (:80)
  OK     xyz_bank_bff: canales web, móvil y cajero con datos de cuenta
  OK     xyz_bank_batch: transacciones persistidas en PostgreSQL
==> Ready
```

Comprobación manual (14 contenedores, todos `healthy`; el job del batch no aparece porque termina al acabar):

```bash
cd /opt/xyz-bank
for d in kafka_S8 xyz_bank_bff xyz_bank_batch; do (cd $d && docker compose ps); done
```

## 2. Preparar Postman

1. **Desactivar la verificación SSL:** *Settings → General → SSL certificate verification: Off* (el certificado del BFF es autofirmado).
2. **Crear un Environment** con estas variables:

| Variable | Valor |
|---|---|
| `ip` | La IP pública de la instancia |
| `token` | (vacía; se llena sola en la prueba K1) |

3. En la prueba **K1**, pega en la pestaña **Scripts → Post-response** (o *Tests*) para guardar el token automáticamente:

```javascript
pm.environment.set("token", pm.response.json().access_token);
```

Las URL de este documento usan `{{ip}}`. El token dura 1.799 segundos (30 minutos): si una prueba de `kafka_S8` empieza a responder 401, repite K1.

## 3. Prueba 1: Batch (por SSH)

El job lee los tres CSV del sistema legado, rechaza o corrige los registros inválidos, y escribe el resultado en PostgreSQL. Trabaja con la carpeta del batch:

```bash
cd /opt/xyz-bank/xyz_bank_batch
```

### A1. Partir de una base limpia (opcional, para el video)

```bash
docker compose down -v
docker compose up -d --wait
docker compose exec postgres psql -U xyzbank -d xyzbank -c '\dt'
```

Respuesta esperada: `Did not find any relations.` (las tablas aún no existen).

### A2. Ejecutar el job

```bash
docker compose run --rm batch
```

El contenedor termina con código 0 (`echo $?` muestra `0`). En el log aparece un resumen por paso (los tiempos varían):

```
RESUMEN transactionStep status=COMPLETED leidos=1000 escritos=392 rechazados(lectura/proceso/escritura)=0/608/0 correcciones=284 ...
RESUMEN statementStep   status=COMPLETED leidos=1000 escritos=603 rechazados(lectura/proceso/escritura)=0/397/0 correcciones=607 ...
RESUMEN interestStep    status=COMPLETED leidos=1000 escritos=320 rechazados(lectura/proceso/escritura)=0/680/0 correcciones=0 ...
CALIDAD interestStep RECHAZADO MISSING_BALANCE=197
CALIDAD interestStep RECHAZADO INELIGIBLE_ACCOUNT_TYPE=163
CALIDAD interestStep RECHAZADO AGE_OUT_OF_RANGE=146
...
BENCHMARK job=masterJob status=COMPLETED chunkSize=100 stepThreads=2 totalMs=892
```

Cada archivo tiene 1.000 filas de entrada: el job las separa entre las aceptadas (corregidas si hacía falta) y las rechazadas.

### A3. Verificar en PostgreSQL

```bash
docker compose exec postgres psql -U xyzbank -d xyzbank -c "
SELECT 'transaction_report' AS tabla, count(*) FROM transaction_report
UNION ALL SELECT 'account', count(*) FROM account
UNION ALL SELECT 'annual_statement', count(*) FROM annual_statement
UNION ALL SELECT 'data_quality_log', count(*) FROM data_quality_log;"
```

Respuesta esperada:

```
       tabla        | count 
--------------------+-------
 transaction_report |   392
 account            |   320
 annual_statement   |   603
 data_quality_log   |  2576
```

Qué se limpió y por qué (bitácora de calidad de datos):

```bash
docker compose exec postgres psql -U xyzbank -d xyzbank -c "
SELECT step_name, action, error_code, COUNT(*) AS casos FROM data_quality_log
GROUP BY 1,2,3 ORDER BY 1,2,4 DESC;"
```

Respuesta esperada (primeras filas):

```
   step_name   |  action   |         error_code          | casos 
---------------+-----------+-----------------------------+-------
 interestStep  | RECHAZADO | MISSING_BALANCE             |   197
 interestStep  | RECHAZADO | INELIGIBLE_ACCOUNT_TYPE     |   163
 interestStep  | RECHAZADO | AGE_OUT_OF_RANGE            |   146
 interestStep  | RECHAZADO | MISSING_AGE                 |   126
 interestStep  | RECHAZADO | INVALID_CLIENT_NAME         |    48
 statementStep | CORREGIDO | DATE_FORMAT_NORMALIZED      |   472
 statementStep | CORREGIDO | MISSING_DESCRIPTION         |   106
 statementStep | CORREGIDO | TRANSACTION_TYPE_NORMALIZED |    29
 ...
```

Datos ya limpios, listos para consultar:

```bash
docker compose exec postgres psql -U xyzbank -d xyzbank -c \
  "SELECT account_id, client_name, account_type, balance, interest, age FROM account ORDER BY account_id LIMIT 4;"
```

```
 account_id | client_name  | account_type | balance  | interest | age 
------------+--------------+--------------+----------+----------+-----
        101 | Diana Prince | HIPOTECA     |  7210.00 |   210.00 |  25
        101 | Alice Brown  | AHORRO       | 12060.00 |    60.00 |  45
        101 | Jane Smith   | AHORRO       |  5025.00 |    25.00 |  45
        101 | Steve Rogers | AHORRO       | 12060.00 |    60.00 |  40
```

(El archivo de origen repite `cuenta_id` para varios titulares, por eso hay filas distintas con el mismo número de cuenta.)

**Repetir el job** (`docker compose run --rm batch`) recarga las tablas desde cero: los conteos son siempre los mismos, no se duplican datos.

## 4. Prueba 2: BFF (Postman)

Base: `https://{{ip}}:8443`. Todas las pruebas usan **Authorization → Basic Auth**. Los datos de las cuentas, transacciones y clientes los cargan los microservicios internos desde sus CSV al arrancar.

| Canal | Usuario | Contraseña | Rol |
|---|---|---|---|
| Web | `clienteWeb` | `web123` | `WEB` |
| Móvil | `clienteMovil` | `movil123` | `MOVIL` |
| Cajero | `atmXyz` | `atm123` | `CAJERO` |

### B1. Canal web: detalle completo

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/web/cuentas/101`
- **Auth:** Basic Auth, `clienteWeb` / `web123`
- **Respuesta esperada:** `200 OK` (la lista de movimientos se acorta aquí; la real trae 46)

```json
{
  "cuentaId": 101,
  "nombre": "Charlie Green",
  "saldo": 8000.00,
  "edad": 100,
  "tipo": "prestamo",
  "cliente": {
    "rut": "14.567.890-1",
    "nombre": "Charlie Green",
    "email": "charlie.green@xyzbank.cl",
    "telefono": "+56934567890",
    "direccion": "Pasaje Las Rosas 789 Valparaiso",
    "fechaNacimiento": "1990-11-02"
  },
  "movimientos": [
    { "fecha": "16/02/2024", "tipo": "compra", "monto": -100.00, "descripcion": "Retiro parcial" },
    { "fecha": "2024-02-19", "tipo": "compra", "monto": 2000.00, "descripcion": "Retiro parcial" }
  ],
  "resumen": {
    "totalIngresos": 60000.00,
    "totalEgresos": -3100.00,
    "saldoNeto": 56900.00,
    "totalTransacciones": 46
  }
}
```

### B2. Canal móvil: respuesta liviana

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/movil/cuentas/101`
- **Auth:** Basic Auth, `clienteMovil` / `movil123`
- **Respuesta esperada:** `200 OK`, solo el titular, el saldo y los últimos 5 movimientos

```json
{
  "id": 101,
  "titular": "Charlie Green",
  "saldo": 8000.00,
  "ultimosMovimientos": [
    { "fecha": "16/02/2024", "monto": -100.00 },
    { "fecha": "2024-02-19", "monto": 2000.00 },
    { "fecha": "2024-01-07", "monto": 2500.00 },
    { "fecha": "2024/07/16", "monto": 2500.00 },
    { "fecha": "2024/07/09", "monto": -1000.00 }
  ]
}
```

### B3. Canal cajero: mínimo y seguro

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/cajero/cuentas/101/saldo`
- **Auth:** Basic Auth, `atmXyz` / `atm123`
- **Respuesta esperada:** `200 OK`, solo saldo y titular enmascarado, sin datos de contacto

```json
{ "cuentaId": 101, "titular": "Charlie G.", "saldo": 8000.00 }
```

Compara B1, B2 y B3 para mostrar que cada canal recibe solo lo que necesita.

### B4. La respuesta cambia según el cliente

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/cajero/cuentas/{id}/saldo`, cambiando `{id}`
- **Auth:** Basic Auth, `atmXyz` / `atm123`
- **Respuesta esperada:** `200 OK` en todas; el titular y el saldo dependen de la cuenta

| `{id}` | Respuesta |
|---|---|
| 101 | `{"cuentaId":101,"titular":"Charlie G.","saldo":8000.00}` |
| 102 | `{"cuentaId":102,"titular":"Alice B.","saldo":12000.00}` |
| 103 | `{"cuentaId":103,"titular":"Bob J.","saldo":7000.00}` |
| 105 | `{"cuentaId":105,"titular":"Steve R.","saldo":10000.00}` |
| 106 | `{"cuentaId":106,"titular":"John D.","saldo":5000.00}` |
| 107 | `{"cuentaId":107,"titular":"Diana P.","saldo":5000.00}` |

En el canal web, la cuenta 102 devuelve el perfil de Alice Brown (`rut` `12.345.678-9`, `email` `alice.brown@xyzbank.cl`) y 55 movimientos.

### B5. Titular sin ficha de cliente (degradación controlada)

La cuenta 129 tiene como titular "Unknown", que no existe en `ms-clientes`. El canal no falla: responde sin los datos del cliente.

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/web/cuentas/129`
- **Auth:** Basic Auth, `clienteWeb` / `web123`
- **Respuesta esperada:** `200 OK` con `"cliente": null`, `"movimientos": []` y el resumen en cero

Y en el canal cajero: `GET https://{{ip}}:8443/api/cajero/cuentas/129/saldo` (Basic `atmXyz` / `atm123`) devuelve `200` con `{"cuentaId":129,"titular":null,"saldo":7000.00}`.

### B6. Sin credenciales

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/web/cuentas/101`
- **Auth:** No Auth
- **Respuesta esperada:** `401 Unauthorized` (cuerpo vacío). Con una contraseña incorrecta también responde `401`.

### B7. Credenciales de otro canal

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/web/cuentas/101`
- **Auth:** Basic Auth, `clienteMovil` / `movil123`
- **Respuesta esperada:** `403 Forbidden`

```json
{ "timestamp": "2026-10-10T19:09:33.689Z", "status": 403, "error": "Forbidden", "path": "/api/web/cuentas/101" }
```

### B8. Cuenta inexistente

- **Método:** `GET`
- **URL:** `https://{{ip}}:8443/api/web/cuentas/99999`
- **Auth:** Basic Auth, `clienteWeb` / `web123`
- **Respuesta esperada:** `500 Internal Server Error`

```json
{ "timestamp": "2026-10-10T19:09:33.689Z", "status": 500, "error": "Internal Server Error", "path": "/api/web/cuentas/99999" }
```

Esta respuesta es una limitación conocida: `ms-cuentas` lanza un error genérico cuando no encuentra la cuenta y el BFF no lo traduce a `404`. Se incluye entre las mejoras posibles.

### B9. Microservicios internos (por SSH, opcional)

Los `ms-*` no son accesibles desde Postman a propósito (no tienen autenticación propia). Para mostrar `ms-clientes` en el video, llámalo desde dentro de la red de Docker:

```bash
cd /opt/xyz-bank/xyz_bank_bff
docker compose exec -T mibff wget -qO- "http://ms-clientes:8082/clientes/buscar?nombre=Alice%20Brown"
```

Respuesta esperada:

```json
{"clienteId":1,"rut":"12.345.678-9","nombre":"Alice Brown","email":"alice.brown@xyzbank.cl","telefono":"+56912345678","direccion":"Av. Providencia 1234 Santiago","fechaNacimiento":"1985-03-14"}
```

Alta de un cliente (`ms-clientes` ofrece un CRUD completo) y respuesta esperada:

```bash
docker compose exec -T mibff wget -qO- --header "Content-Type: application/json" \
  --post-data '{"rut":"22.345.678-0","nombre":"Cliente Demo","email":"demo@xyzbank.cl","telefono":"+56911111111","direccion":"Calle Falsa 123","fechaNacimiento":"2000-01-01"}' \
  http://ms-clientes:8082/clientes
```

```json
{"clienteId":11,"rut":"22.345.678-0","nombre":"Cliente Demo","email":"demo@xyzbank.cl","telefono":"+56911111111","direccion":"Calle Falsa 123","fechaNacimiento":"2000-01-01"}
```

## 5. Prueba 3: kafka_S8 (Postman y SSH)

Base: `http://{{ip}}` (puerto 80). Las pruebas usan **OAuth2 Client Credentials**: primero se obtiene un token y luego se envía como **Authorization → Bearer Token** con el valor `{{token}}`.

| Cliente OAuth2 | Secreto | Scopes que puede pedir |
|---|---|---|
| `banking-client` | `banking-secret` | `data.read`, `ingestion.write` |
| `resilient-client` | `resilient-secret` | `data.read` |

### K1. Obtener el token

- **Método:** `POST`
- **URL:** `http://{{ip}}/oauth2/token`
- **Auth:** Basic Auth, `banking-client` / `banking-secret`
- **Body:** `x-www-form-urlencoded`

| Key | Value |
|---|---|
| `grant_type` | `client_credentials` |
| `scope` | `data.read ingestion.write` |

- **Respuesta esperada:** `200 OK`

```json
{
  "access_token": "eyJraWQiOiI2ZWYyOGI5...",
  "scope": "data.read ingestion.write",
  "token_type": "Bearer",
  "expires_in": 1799
}
```

Con un secreto incorrecto responde `401`.

### K2. Sin token

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/stats`
- **Auth:** No Auth
- **Respuesta esperada:** `401 Unauthorized`. El gateway rechaza todo `/api/**` sin un JWT válido.

### K3. Estado antes de la ingesta

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/stats`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK`, todo en cero (la plataforma arranca sin datos; la ingesta es explícita)

```json
{ "productosConInteres": 0, "transacciones": 0, "movimientosAnuales": 0, "filasRechazadas": 0, "rechazadasPorArchivo": {} }
```

### K4. Ejecutar la ingesta

Lee los tres CSV, valida y normaliza cada fila, y publica en Kafka; el procesador las consume y las guarda en PostgreSQL.

- **Método:** `POST`
- **URL:** `http://{{ip}}/api/ingestion/run`
- **Auth:** Bearer Token, `{{token}}` (exige el scope `ingestion.write`)
- **Body:** ninguno
- **Respuesta esperada:** `200 OK` con el informe por archivo (la duración varía)

```json
{
  "startedAt": "2026-10-10T19:09:58.460559105Z",
  "duration": "PT1.493665375S",
  "totalAccepted": 1552,
  "totalRejected": 1448,
  "files": [
    { "file": "intereses.csv", "topic": "interests-validated", "read": 1000, "accepted": 465, "rejected": 535,
      "rejectionReasons": { "edad": 52, "nombre": 50, "saldo": 211, "tipo": 331 } },
    { "file": "transacciones.csv", "topic": "transactions-validated", "read": 1000, "accepted": 401, "rejected": 599,
      "rejectionReasons": { "fecha": 55, "monto": 327, "tipo": 371 } },
    { "file": "cuentas_anuales.csv", "topic": "annual-accounts-validated", "read": 1000, "accepted": 686, "rejected": 314,
      "rejectionReasons": { "monto": 314 } }
  ]
}
```

Si ya hay una ingesta en curso responde `409`. El último informe se puede volver a leer con `GET http://{{ip}}/api/ingestion/last-report` (Bearer, `data.read`).

### K5. Estado después de la ingesta

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/stats`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK` (el procesador tarda unos segundos en terminar de consumir: repite la petición si ves valores parciales)

```json
{
  "productosConInteres": 465,
  "transacciones": 401,
  "movimientosAnuales": 686,
  "filasRechazadas": 1448,
  "rechazadasPorArchivo": { "cuentas_anuales.csv": 314, "transacciones.csv": 599, "intereses.csv": 535 }
}
```

### K6. Filas rechazadas y su motivo

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/stats/rejected?file=transacciones.csv&limit=2`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK`

```json
[
  { "sourceId": "transacciones.csv:4", "line": 4,
    "raw": "{\"id\":\"3\",\"fecha\":\"2024-04-09\",\"monto\":\"800\",\"tipo\":\"invalid\"}",
    "reasons": "tipo: desconocido ('invalid')" },
  { "sourceId": "transacciones.csv:5", "line": 5,
    "raw": "{\"id\":\"4\",\"fecha\":\"04/05/2024\",\"monto\":\"\",\"tipo\":\"invalid\"}",
    "reasons": "monto: vacio o no numerico; tipo: desconocido ('invalid')" }
]
```

### K7. Cuentas y resumen de una cuenta

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/accounts`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK`, un arreglo de 20 cuentas

```json
[
  { "cuentaId": 101, "movimientos": 29, "saldoNeto": 14000.00 },
  { "cuentaId": 102, "movimientos": 35, "saldoNeto": -28000.00 },
  { "cuentaId": 103, "movimientos": 29, "saldoNeto": -50500.00 }
]
```

Detalle de una cuenta, con los intereses calculados según el tipo de producto:

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/accounts/101/summary`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK` (la lista `productos` se acorta aquí; la real trae 13)

```json
{
  "cuentaId": 101,
  "movimientos": 29,
  "totalesPorTipo": { "compra": 12000.00, "deposito": 37000.00, "retiro": 9500.00, "pago": 1500.00 },
  "saldoNeto": 14000.00,
  "productos": [
    { "tipo": "ahorro", "titular": "Diana Prince", "saldo": 8000.00, "tasaAnual": 0.0250, "interesAnual": 200.00 },
    { "tipo": "prestamo", "titular": "Diana Prince", "saldo": 12000.00, "tasaAnual": 0.1200, "interesAnual": 1440.00 }
  ],
  "interesAnualTotal": 8735.00
}
```

### K8. Resumen de transacciones

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/transactions/summary`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK`

```json
{ "transacciones": 401, "totalCredito": 259000.00, "totalDebito": 268000.00, "neto": -9000.00 }
```

### K9. Consulta resiliente (resilient-client)

Las mismas consultas, pero pasando por `resilient-client`, que las protege con Circuit Breaker, Retry y Rate Limiter.

- **Método:** `GET`
- **URL:** `http://{{ip}}/api/client/stats`
- **Auth:** Bearer Token, `{{token}}`
- **Respuesta esperada:** `200 OK`, con la respuesta del procesador dentro de un sobre

```json
{
  "source": "live",
  "retrievedAt": "2026-10-10T19:10:36.269822035Z",
  "data": {
    "productosConInteres": 465,
    "transacciones": 401,
    "movimientosAnuales": 686,
    "filasRechazadas": 1448,
    "rechazadasPorArchivo": { "cuentas_anuales.csv": 314, "transacciones.csv": 599, "intereses.csv": 535 }
  },
  "error": null
}
```

`GET http://{{ip}}/api/client/accounts/101/summary` devuelve el mismo sobre con el resumen de la cuenta en `data`. Ejecuta K9 antes de K12: así `resilient-client` guarda una respuesta buena para usar como respaldo.

### K10. Control de permisos (scopes)

Primero pide un token de solo lectura: repite **K1** cambiando el valor de `scope` a `data.read` (guárdalo como `{{token}}` o úsalo solo para esta prueba).

- **Método:** `POST`
- **URL:** `http://{{ip}}/api/ingestion/run`
- **Auth:** Bearer Token con el token de solo lectura
- **Respuesta esperada:** `403 Forbidden`. Con ese mismo token, `GET /api/stats` sí responde `200`.

Vuelve a ejecutar K1 con ambos scopes para seguir con las demás pruebas.

### K11. La ingesta es idempotente

- **Método:** `POST`
- **URL:** `http://{{ip}}/api/ingestion/run`
- **Auth:** Bearer Token, `{{token}}` (con `ingestion.write`)
- **Respuesta esperada:** `200 OK` con el mismo informe que K4. Luego `GET /api/stats` devuelve exactamente los mismos conteos que en K5: volver a ingerir actualiza las filas, no las duplica.

### K12. Resiliencia: el circuit breaker (SSH + Postman)

Demuestra que, aunque el procesador se caiga, el cliente resiliente sigue respondiendo.

**Opción 1: script automático (por SSH)**

```bash
cd /opt/xyz-bank/kafka_S8
GATEWAY_URL=http://localhost bash scripts/circuit-breaker-demo.sh
```

Detiene `data-processor-service`, hace llamadas y lo vuelve a levantar. Salida esperada (resumida):

```
==> Downstream healthy
source=live error=null
circuit breaker: CLOSED

==> Stopping data-processor-service
call 1: source=cache error="ResourceAccessException: I/O error on GET request for ...
call 2: source=cache error="ResourceAccessException: I/O error on GET request for ...
call 3: source=cache error="ResourceAccessException: I/O error on GET request for ...
call 4: source=cache error="CallNotPermittedException: CircuitBreaker 'dataProcessor' is OPEN ...
...
circuit breaker: OPEN

==> Starting data-processor-service again
...
HALF_OPEN: source=live error=null
circuit breaker: CLOSED

==> Circuit breaker transitions
  State transition from CLOSED to OPEN
  State transition from OPEN to HALF_OPEN
  State transition from HALF_OPEN to OPEN
  State transition from OPEN to HALF_OPEN
  State transition from HALF_OPEN to CLOSED
```

**Opción 2: manual, con Postman**

1. Por SSH: `cd /opt/xyz-bank/kafka_S8 && docker compose stop data-processor-service`
2. En Postman, repite varias veces `GET http://{{ip}}/api/client/stats` (Bearer `{{token}}`).
   - **Respuesta esperada:** `200 OK` con `"source": "cache"` (la última respuesta buena) y un `error` que describe la falla. Tras unas llamadas el error pasa a `CallNotPermittedException: CircuitBreaker 'dataProcessor' is OPEN`.
3. Por SSH: `docker compose start data-processor-service`. Cuando el servicio vuelve a estar sano y registrado en Eureka (puede tardar cerca de un minuto, porque el circuito espera 15 segundos antes de probar de nuevo), la misma petición vuelve a responder `"source": "live"` y `"error": null`.

### K13. Kafka y Eureka (por SSH)

Topics de Kafka:

```bash
cd /opt/xyz-bank/kafka_S8
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list
```

```
__consumer_offsets
annual-accounts-validated
ingestion-rejected
interests-validated
transactions-validated
```

Particiones: `transactions-validated`, `interests-validated` y `annual-accounts-validated` tienen 3; `ingestion-rejected` tiene 1.

El procesador consumió todo (columna `LAG` en 0):

```bash
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh \
  --bootstrap-server localhost:9092 --describe --group data-processor
```

Servicios registrados en Eureka:

```bash
curl -s -H "Accept: application/json" http://localhost:8761/eureka/apps | grep -o '"name":"[A-Z-]*"' | sort -u
```

Se esperan `API-GATEWAY`, `AUTH-SERVER`, `DATA-INGESTION-SERVICE`, `DATA-PROCESSOR-SERVICE` y `RESILIENT-CLIENT`. Para ver el panel gráfico, abre un túnel SSH (`ssh -i clave.pem -L 8761:localhost:8761 ec2-user@<IP-publica>`) y entra a `http://localhost:8761`.

### K14. Escalado horizontal (opcional)

```bash
cd /opt/xyz-bank/kafka_S8
docker compose up -d --scale data-processor-service=2 --no-recreate --wait
```

En Eureka (comando de K13 o el panel) aparecen **2 instancias** de `DATA-PROCESSOR-SERVICE` en estado `UP`, y el gateway puede repartir las peticiones entre ellas mediante `lb://`. Para volver a una sola:

```bash
docker compose up -d --scale data-processor-service=1 --no-recreate --wait
```

## 6. Antes de grabar el video

### Volver al estado inicial

| Sistema | Comandos | Resultado |
|---|---|---|
| `kafka_S8` | `cd /opt/xyz-bank/kafka_S8 && docker compose down -v && docker compose up -d --wait` | `GET /api/stats` vuelve a ceros (tarda 1 a 2 minutos; no recompila) |
| `xyz_bank_batch` | `cd /opt/xyz-bank/xyz_bank_batch && docker compose down -v && docker compose up -d --wait` | Base sin tablas; `docker compose run --rm batch` las crea y las puebla |
| `xyz_bank_bff` | `cd /opt/xyz-bank/xyz_bank_bff && docker compose restart ms-clientes` | Solo hace falta si diste de alta clientes en B9: recarga los 10 clientes iniciales |

### Orden sugerido para la demostración

Los tiempos son una guía para mantenerse entre 5 y 7 minutos.

| Tiempo | Qué mostrar | Pruebas |
|---|---|---|
| 0:00 a 0:45 | Resumen ejecutivo: el diagrama de arquitectura global (`diagramas/arquitectura-global.png`) | — |
| 0:45 a 1:30 | La instancia EC2, los 14 contenedores `healthy` y las pruebas de humo | 1.4 |
| 1:30 a 2:30 | Batch: ejecución del job y conteos en PostgreSQL | A1 a A3 |
| 2:30 a 3:45 | BFF: tres canales, cambio según el cliente, 401 y 403 | B1 a B7 |
| 3:45 a 5:30 | kafka_S8: token, ingesta, estadísticas, scopes, circuit breaker | K1 a K12 |
| 5:30 a 6:30 | Resultados frente al legacy, desafíos y soluciones, mejoras (`readme.md`, secciones 5 y 7) | — |

La pauta pide cubrir cuatro puntos en el video: resumen ejecutivo, resultados y comparación con el legacy, desafíos con sus soluciones, y propuestas de mejora.

## 7. Solución de problemas

| Síntoma | Causa y solución |
|---|---|
| Postman no conecta al BFF | Revisa que el Security Group permita el 8443 desde tu IP actual y que la verificación SSL esté desactivada |
| `401` en `kafka_S8` con un token que antes funcionaba | El token venció (30 minutos): repite K1 |
| `K3` no muestra ceros | Ya se ejecutó una ingesta antes: usa el reinicio de `kafka_S8` de la sección 6 |
| `K5` muestra conteos parciales | El procesador aún consume los mensajes de Kafka: repite la petición a los pocos segundos |
| `K12` o el script no muestran `source: "cache"` | No hubo una respuesta buena previa: ejecuta K9 con el servicio sano y repite |
| El script del circuit breaker no encuentra el gateway | Pasa `GATEWAY_URL=http://localhost` (en la instancia el gateway está en el puerto 80, no en el 8080) |
| `docker compose` no encuentra el archivo | Estás en otra carpeta: entra a la carpeta del sistema bajo `/opt/xyz-bank` |
