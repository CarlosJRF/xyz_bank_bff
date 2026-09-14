package src.main.java.controller;

import java.util.List;
import org.springframework.web.bind.annotation.*;
import cl.xyzbank.cuentas.repositories.CuentaRepository;
import cl.xyzbank.cuentas.dtos.CuentaDTO;
import cl.xyzbank.cuentas.entities.CuentaEntity;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/cuentas")
@RequiredArgsConstructor
public class CuentaController {
    private final CuentaRepository repository;

    @GetMapping
    public List listarCuentas() {
        return repository.findAll().stream()
            .map(this::toDTO)
            .toList();
    }

    @GetMapping("/{cuentaId}")
    public CuentaDTO obtenerCuenta(@PathVariable Long cuentaId) {
        return repository.findById(cuentaId)
            .map(this::toDTO)
            .orElseThrow(() -> new RuntimeException("Cuenta no encontrada"));
    }

    private CuentaDTO toDTO(CuentaEntity entity) {
        return new CuentaDTO(
            entity.getCuentaId(),
            entity.getNombre(),
            entity.getSaldo(),
            entity.getEdad(),
            entity.getTipo()
        );
    }
}
