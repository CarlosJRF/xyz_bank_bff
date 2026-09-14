package dtos;

import java.util.List;

public record CuentaAgregadaDTO(
        CuentaBackendDTO cuenta,
        List transacciones
) {}