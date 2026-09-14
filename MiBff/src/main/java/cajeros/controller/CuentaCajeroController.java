package cajeros.controller;

import org.springframework.web.bind.annotation.*;
import cajeros.dtos.SaldoCajeroDTO;
import cajeros.service.BffCajeroService;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/cajero/cuentas")
@RequiredArgsConstructor
public class CuentaCajeroController {
    private final BffCajeroService service;

    @GetMapping("/{cuentaId}/saldo")
    public SaldoCajeroDTO consultarSaldo(@PathVariable Long cuentaId) {
        return service.consultarSaldo(cuentaId);
    }
}