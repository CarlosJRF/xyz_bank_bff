package dtos;

import java.math.BigDecimal;

public record CuentaBackendDTO(
        Long cuentaId,
        String nombre,
        BigDecimal saldo,
        Integer edad,
        String tipo
) {}