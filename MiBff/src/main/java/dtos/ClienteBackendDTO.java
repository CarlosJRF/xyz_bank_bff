package dtos;

public record ClienteBackendDTO(
        Long clienteId,
        String rut,
        String nombre,
        String email,
        String telefono,
        String direccion,
        String fechaNacimiento
) {}
