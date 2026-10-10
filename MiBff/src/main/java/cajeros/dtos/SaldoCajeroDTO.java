package cajeros.dtos;

import java.math.BigDecimal;

public record SaldoCajeroDTO(Long cuentaId, String titular, BigDecimal saldo) {}