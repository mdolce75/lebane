package com.lebane.seed;

import static net.logstash.logback.argument.StructuredArguments.kv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.mapper.ConsultaMapper;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.departamento.service.ImagenService;
import com.lebane.exception.DependencyUnavailableException;
import com.lebane.seed.SeedData.SeedDepartamento;

/**
 * Seed opcional para desarrollo local ({@code SEED_ENABLED=true}).
 *
 * <ul>
 *   <li>Idempotente: cada departamento tiene un código fijo ({@code SEED-NNNN}); si ya existe no se toca, de modo
 *       que reiniciar la aplicación no duplica datos ni pisa ediciones hechas a mano.</li>
 *   <li>Un departamento y sus consultas se insertan en una misma transacción: nunca quedan consultas huérfanas.</li>
 *   <li>Seguro ante varias instancias arrancando a la vez: el índice único de {@code codigo} rechaza el duplicado y
 *       esa instancia simplemente lo omite.</li>
 *   <li>Pasa por los mismos mappers que la API, con los mismos datos normalizados.</li>
 *   <li>Fotos: se generan en memoria ({@link SeedImages}) y se suben con {@link ImagenService}, igual que desde la
 *       API (validación de tipo, MinIO, límite de 5). Solo a departamentos {@code SEED-} sin fotos; si MinIO no está
 *       disponible se omiten (WARN) y se completan en el próximo arranque.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(prefix = "lebane.seed", name = "enabled", havingValue = "true")
public class DevDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevDataSeeder.class);

    private final DepartamentoRepository departamentoRepository;
    private final ConsultaRepository consultaRepository;
    private final ImagenRepository imagenRepository;
    private final ImagenService imagenService;
    private final DepartamentoMapper departamentoMapper;
    private final ConsultaMapper consultaMapper;
    private final TransactionTemplate transactionTemplate;

    public DevDataSeeder(DepartamentoRepository departamentoRepository, ConsultaRepository consultaRepository,
            ImagenRepository imagenRepository, ImagenService imagenService, DepartamentoMapper departamentoMapper,
            ConsultaMapper consultaMapper, TransactionTemplate transactionTemplate) {
        this.departamentoRepository = departamentoRepository;
        this.consultaRepository = consultaRepository;
        this.imagenRepository = imagenRepository;
        this.imagenService = imagenService;
        this.departamentoMapper = departamentoMapper;
        this.consultaMapper = consultaMapper;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        int creados = 0;
        int existentes = 0;
        for (SeedDepartamento seed : SeedData.departamentos()) {
            if (insertarSiNoExiste(seed)) {
                creados++;
            } else {
                existentes++;
            }
        }
        int fotos = agregarFotos();
        log.info("Seed de desarrollo aplicado", kv("creados", creados), kv("existentes", existentes),
                kv("fotos", fotos));
    }

    /** Cantidad de fotos de ejemplo por departamento: de 0 a 3 (algunos sin fotos muestran el placeholder). */
    static int fotosPara(String codigo) {
        return Integer.parseInt(codigo.substring(codigo.indexOf('-') + 1)) % 4;
    }

    private int agregarFotos() {
        int subidas = 0;
        for (SeedDepartamento seed : SeedData.departamentos()) {
            int cantidad = fotosPara(seed.codigo());
            Long id = departamentoRepository.findIdByCodigo(seed.codigo()).orElse(null);
            if (cantidad == 0 || id == null || imagenRepository.countByDepartamentoId(id) > 0) {
                continue;
            }
            try {
                for (int foto = 0; foto < cantidad; foto++) {
                    byte[] png = SeedImages.png(id.intValue(), foto);
                    imagenService.subir(id, new ByteArrayResource(png), png.length);
                    subidas++;
                }
            } catch (DependencyUnavailableException e) {
                log.warn("Seed: storage no disponible, fotos de ejemplo omitidas (se reintentan al próximo arranque)",
                        kv("codigo", seed.codigo()));
                break;
            }
        }
        return subidas;
    }

    /** @return {@code true} si se insertó; {@code false} si ya existía. */
    boolean insertarSiNoExiste(SeedDepartamento seed) {
        try {
            Boolean creado = transactionTemplate.execute(status -> {
                if (departamentoRepository.existsByCodigo(seed.codigo())) {
                    return false;
                }
                Departamento departamento = departamentoRepository.save(
                        departamentoMapper.toNewEntity(seed.datos(), seed.codigo()));
                seed.consultas().forEach(consulta ->
                        consultaRepository.save(consultaMapper.toEntity(departamento, consulta)));
                return true;
            });
            return Boolean.TRUE.equals(creado);
        } catch (DataIntegrityViolationException e) {
            // Otra instancia lo insertó entre el exists y el commit.
            log.debug("Seed ya insertado por otra instancia", kv("codigo", seed.codigo()));
            return false;
        }
    }
}
