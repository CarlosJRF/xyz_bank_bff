package main.java.repositories;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import cl.xyzbank.transacciones.entities.TransaccionEntity;

public interface TransaccionRepository extends JpaRepository {
    List findByCuentaId(Long cuentaId);
}