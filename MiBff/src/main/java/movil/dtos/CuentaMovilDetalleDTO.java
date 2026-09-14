package movil.dtos;

import java.math.BigDecimal;
import java.util.List;

public record CuentaMovilDetalleDTO(Long id, BigDecimal saldo, List ultimosMovimientos) {} // Sin nombre, edad o tipo
