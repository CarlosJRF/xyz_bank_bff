package main.java.loaders;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import main.java.entities.TransaccionEntity;
import main.java.repositories.TransaccionRepository;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class DataLoader implements CommandLineRunner {
    
    private final TransaccionRepository repository;

    @Override
    public void run(String... args) throws Exception {
        if (repository.count() > 0) return;

        List transacciones = new ArrayList<>();
        
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/cuentas_anuales.csv").getInputStream()))) {
            
            reader.readLine(); // Saltar la primera línea (cabeceras)
            String linea;
            
            while ((linea = reader.readLine()) != null) {
                if (linea.isBlank()) continue;
                
                String[] campos = linea.split(",", -1);
                
                // Validación para campos numéricos que puedan venir como "NaN" o vacíos
                String montoStr = campos[3].trim();
                BigDecimal monto = (montoStr.isEmpty() || montoStr.equalsIgnoreCase("NaN")) 
                                   ? BigDecimal.ZERO 
                                   : new BigDecimal(montoStr);
                
                TransaccionEntity entidad = new TransaccionEntity(
                    null, // El ID se autogenera
                    Long.parseLong(campos[0].trim()),
                    campos[1].trim(),
                    campos[2].trim(),
                    monto,
                    campos.length > 4 ? campos[4].trim() : ""
                );
                
                transacciones.add(entidad);
            }
        }
        
        repository.saveAll(transacciones);
        System.out.println("Transacciones cargadas: " + transacciones.size());
    }
}