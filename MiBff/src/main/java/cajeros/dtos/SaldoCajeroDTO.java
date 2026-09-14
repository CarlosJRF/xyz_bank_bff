package cajeros.dtos;

import java.math.BigDecimal;

public record SaldoCajeroDTO(Long cuentaId, BigDecimal saldo) {}