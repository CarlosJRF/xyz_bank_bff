# xyz_bank_bff

Backend for Frontend del Banco XYZ y sus microservicios de dominio. Parte de la solución descrita en
[`entrega/readme.md`](entrega/readme.md).

| Módulo | Rol | Puerto |
|---|---|---|
| `MiBff` | BFF para web, móvil y cajero (HTTPS, Basic Auth por rol) | 8443 |
| `ms-cuentas` | Cuentas | 8081 |
| `ms-transacciones` | Movimientos por cuenta | 8094 |
| `ms-clientes` | Perfiles de clientes (CRUD) | 8082 |

## Ejecutar con Docker

Requisitos: Docker con Compose v2. Las imágenes se compilan dentro de Docker (Dockerfile multietapa en
cada módulo), así que no hace falta Java ni Maven en el host.

```bash
cp .env.example .env              # opcional: puerto del BFF y memoria
docker compose up -d --build --wait
```

Solo el BFF se publica (`BFF_PORT`, por defecto 8443). Los tres microservicios no tienen autenticación
propia, por eso viven solo en la red interna `backend`. El BFF los alcanza por nombre de servicio
(`MS_CUENTAS_BASE_URL`, `MS_TRANSACCIONES_BASE_URL` y `MS_CLIENTES_BASE_URL` reemplazan las URL
`localhost` de `application.properties`).

```bash
curl -sk -u clienteWeb:web123     https://localhost:8443/api/web/cuentas/101
curl -sk -u clienteMovil:movil123 https://localhost:8443/api/movil/cuentas/101
curl -sk -u atmXyz:atm123         https://localhost:8443/api/cajero/cuentas/101/saldo
docker compose down
```

El certificado del BFF es autofirmado (`-k` en curl; desactiva la verificación SSL en Postman).

## Ejecutar sin Docker

Cada módulo es un proyecto Maven independiente (`./mvnw spring-boot:run` dentro de su carpeta). Hay que
levantar primero `ms-cuentas`, `ms-transacciones` y `ms-clientes`, y después `MiBff`.
