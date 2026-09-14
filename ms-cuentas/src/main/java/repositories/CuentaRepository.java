package main.java.repositories;


import org.springframework.data.jpa.repository.JpaRepository;
import main.java.entities.CuentaEntity;

public interface CuentaRepository extends JpaRepository<CuentaEntity, Long> {}