package movil.dtos;

import java.math.BigDecimal;
import java.util.List;

public record CuentaMovilDetalleDTO(Long id, String titular, BigDecimal saldo, List ultimosMovimientos) {}