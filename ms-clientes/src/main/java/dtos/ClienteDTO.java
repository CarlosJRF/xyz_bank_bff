package main.java.dtos;

public record ClienteDTO(
        Long clienteId,
        String rut,
        String nombre,
        String email,
        String telefono,
        String direccion,
        String fechaNacimiento
) {}
