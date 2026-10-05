package com.lebane.departamento.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Direccion;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.ConsultaRepository;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.DepartamentoSpecifications;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.ErrorCode;
import com.lebane.exception.PreconditionFailedException;
import com.lebane.exception.ResourceNotFoundException;

/**
 * Casos de uso de departamentos (alta, edición y detalle). El listado paginado se implementa en la Fase 3.
 *
 * <p>El detalle se arma con una cantidad fija de consultas, independiente de la cantidad de fotos o consultas:
 * departamento por PK, imágenes por FK (ordenadas) y {@code COUNT} de consultas. Sin N+1 ni colecciones en memoria.
 */
@Service
@Transactional(readOnly = true)
public class DepartamentoService {

    private static final Logger log = LoggerFactory.getLogger(DepartamentoService.class);
    static final String RECURSO = "departamento";

    private final DepartamentoRepository departamentoRepository;
    private final ImagenRepository imagenRepository;
    private final ConsultaRepository consultaRepository;
    private final DepartamentoMapper mapper;
    private final CodigoDepartamentoGenerator codigoGenerator;
    private final Clock clock;

    public DepartamentoService(DepartamentoRepository departamentoRepository, ImagenRepository imagenRepository,
            ConsultaRepository consultaRepository, DepartamentoMapper mapper,
            CodigoDepartamentoGenerator codigoGenerator, Clock clock) {
        this.departamentoRepository = departamentoRepository;
        this.imagenRepository = imagenRepository;
        this.consultaRepository = consultaRepository;
        this.mapper = mapper;
        this.codigoGenerator = codigoGenerator;
        this.clock = clock;
    }

    @Transactional
    public DepartamentoDetailResponse crear(DepartamentoRequest request) {
        if (request.estado() != null && !request.estado().admiteAlta()) {
            throw new BusinessRuleException(ErrorCode.TRANSICION_DE_ESTADO_INVALIDA,
                    "Un departamento no se puede publicar directamente como vendido");
        }
        Departamento nuevo = mapper.toNewEntity(request, codigoGenerator.generate());
        validarDireccionLibre(nuevo.getDireccion(), null);
        Departamento departamento = departamentoRepository.saveAndFlush(nuevo);
        log.info("Departamento creado", kv("departamentoId", departamento.getId()),
                kv("codigo", departamento.getCodigo()));
        // Recién creado: sin imágenes ni consultas.
        return mapper.toDetail(departamento, List.of(), 0);
    }

    /**
     * Edición completa con concurrencia optimista.
     *
     * @param versionesEsperadas versiones aceptadas según {@code If-Match}; vacío si el cliente no envió el header.
     *                           Si no coincide con la versión actual, 412 sin modificar nada. Las escrituras
     *                           concurrentes entre la lectura y el flush las detecta {@code @Version} (409).
     */
    @Transactional
    public DepartamentoDetailResponse actualizar(Long id, DepartamentoRequest request, Set<Long> versionesEsperadas) {
        Departamento departamento = buscarConVersion(id, versionesEsperadas);
        validarVigente(departamento);
        validarCambio(departamento.getEstado(), request.estado());
        // Solo si cambia la dirección: un aviso cargado antes de la regla sigue siendo editable.
        Direccion direccion = DepartamentoMapper.toDireccion(request.direccion());
        if (!direccion.mismaUbicacion(departamento.getDireccion())) {
            validarDireccionLibre(direccion, id);
        }
        mapper.applyUpdate(departamento, request);
        // Flush explícito: incrementa la versión ahora, para devolverla en la respuesta y el ETag.
        departamentoRepository.flush();
        log.info("Departamento actualizado", kv("departamentoId", id), kv("version", departamento.getVersion()));
        return detalle(departamento);
    }

    /**
     * Baja lógica con concurrencia optimista: igual que la edición, si {@code If-Match} no coincide responde 412 sin
     * cambiar nada. Cualquier estado se puede dar de baja (también un vendido, para sacarlo del listado). Desde ese
     * momento sale del listado, no admite cambios (409 {@code DEPARTAMENTO_DADO_DE_BAJA}) y su dirección queda libre
     * para otro aviso. El detalle se sigue pudiendo leer, para reactivarlo.
     */
    @Transactional
    public void darDeBaja(Long id, Set<Long> versionesEsperadas) {
        Departamento departamento = buscarConVersion(id, versionesEsperadas);
        validarVigente(departamento);
        departamento.darDeBaja(clock.instant());
        departamentoRepository.flush();
        log.info("Departamento dado de baja", kv("departamentoId", id), kv("codigo", departamento.getCodigo()));
    }

    /**
     * Revierte la baja: vuelve al listado con el mismo estado, datos, fotos y consultas. Si está disponible o reservado,
     * su dirección no puede estar ocupada por otro aviso publicado mientras tanto (409 {@code AVISO_DUPLICADO}); un
     * vendido no ocupa la dirección. Con concurrencia optimista, como la edición y la baja.
     */
    @Transactional
    public DepartamentoDetailResponse reactivar(Long id, Set<Long> versionesEsperadas) {
        Departamento departamento = buscarConVersion(id, versionesEsperadas);
        if (!departamento.estaDadoDeBaja()) {
            throw new BusinessRuleException(ErrorCode.DEPARTAMENTO_NO_DADO_DE_BAJA,
                    "El departamento no está dado de baja");
        }
        if (EstadoDepartamento.activos().contains(departamento.getEstado())) {
            validarDireccionLibre(departamento.getDireccion(), id);
        }
        departamento.reactivar();
        departamentoRepository.flush();
        log.info("Departamento reactivado", kv("departamentoId", id), kv("codigo", departamento.getCodigo()),
                kv("version", departamento.getVersion()));
        return detalle(departamento);
    }

    public DepartamentoDetailResponse obtenerDetalle(Long id) {
        return detalle(buscar(id));
    }

    private DepartamentoDetailResponse detalle(Departamento departamento) {
        List<Imagen> imagenes = imagenRepository.findByDepartamentoIdOrdered(departamento.getId());
        long cantidadConsultas = consultaRepository.countByDepartamentoId(departamento.getId());
        return mapper.toDetail(departamento, imagenes, cantidadConsultas);
    }

    /** Cualquier departamento existente, también dado de baja (el detalle lo muestra para poder reactivarlo). */
    private Departamento buscar(Long id) {
        return departamentoRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException(RECURSO));
    }

    /**
     * Lectura para modificar: 404 si no existe y 412 si {@code If-Match} no coincide con la versión actual. Va antes
     * que las reglas de negocio (409), para que el cliente recargue y vea el estado actual.
     */
    private Departamento buscarConVersion(Long id, Set<Long> versionesEsperadas) {
        Departamento departamento = buscar(id);
        if (!versionesEsperadas.isEmpty() && !versionesEsperadas.contains(departamento.getVersion())) {
            throw new PreconditionFailedException();
        }
        return departamento;
    }

    /**
     * Departamento que admite cambios (edición, baja, fotos, consultas): 404 si no existe, 409
     * {@code DEPARTAMENTO_DADO_DE_BAJA} si está dado de baja.
     */
    static Departamento vigente(Optional<Departamento> departamento) {
        Departamento encontrado = departamento.orElseThrow(() -> new ResourceNotFoundException(RECURSO));
        validarVigente(encontrado);
        return encontrado;
    }

    static void validarVigente(Departamento departamento) {
        if (departamento.estaDadoDeBaja()) {
            throw dadoDeBaja();
        }
    }

    static BusinessRuleException dadoDeBaja() {
        return new BusinessRuleException(ErrorCode.DEPARTAMENTO_DADO_DE_BAJA,
                "El departamento está dado de baja: hay que reactivarlo para modificarlo");
    }

    /**
     * Regla de avisos duplicados: no puede haber dos departamentos publicados (no vendidos) en la misma unidad física.
     * Se controla en el servicio y no con un índice único porque una base existente puede tener duplicados cargados
     * antes de la regla, y la migración fallaría; dos altas idénticas simultáneas podrían pasar ambas el control.
     */
    private void validarDireccionLibre(Direccion direccion, Long excluirId) {
        if (departamentoRepository.exists(
                DepartamentoSpecifications.publicadoEnLaMismaDireccion(direccion, excluirId))) {
            throw new BusinessRuleException(ErrorCode.AVISO_DUPLICADO,
                    "Ya hay un departamento publicado en la misma dirección (calle, número, piso y unidad)");
        }
    }

    /**
     * Reglas del ciclo de vida ({@link EstadoDepartamento}): un departamento vendido no se modifica, y el estado solo
     * puede cambiar por una transición permitida. Si el request no informa estado, se conserva el actual.
     */
    private static void validarCambio(EstadoDepartamento actual, EstadoDepartamento nuevo) {
        if (!actual.esModificable()) {
            throw new BusinessRuleException(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE,
                    "El departamento ya fue vendido y no se puede modificar");
        }
        if (nuevo != null && !actual.puedeCambiarA(nuevo)) {
            throw new BusinessRuleException(ErrorCode.TRANSICION_DE_ESTADO_INVALIDA,
                    "No se puede pasar de " + actual + " a " + nuevo);
        }
    }
}
