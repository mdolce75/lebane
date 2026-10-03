package com.lebane.departamento.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.InputStreamSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.lebane.departamento.dto.ImagenResponse;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.mapper.DepartamentoMapper;
import com.lebane.departamento.repository.DepartamentoRepository;
import com.lebane.departamento.repository.ImagenRepository;
import com.lebane.exception.BusinessRuleException;
import com.lebane.exception.DependencyUnavailableException;
import com.lebane.exception.ErrorCode;
import com.lebane.exception.InvalidRequestException;
import com.lebane.exception.PayloadTooLargeException;
import com.lebane.exception.ResourceNotFoundException;
import com.lebane.storage.config.StorageProperties;
import com.lebane.storage.service.ImageType;
import com.lebane.storage.service.ObjectStorageService;

/**
 * Fotos de un departamento: el binario va a MinIO y los metadatos a PostgreSQL.
 *
 * <p>Subida (una foto por request):
 * <ol>
 *   <li>Validación barata antes de tocar la red: el departamento existe, el archivo no está vacío, no supera el
 *       tamaño máximo y su contenido real (magic bytes) es JPEG, PNG o WebP. Prechequeo del límite de 5 fotos.</li>
 *   <li>Subida a MinIO, fuera de toda transacción: nunca se mantiene una conexión ni un lock de base abiertos
 *       mientras se transfiere un archivo por red.</li>
 *   <li>Registro en una transacción corta con la fila del departamento bloqueada: se vuelve a verificar el límite
 *       (dos subidas concurrentes no pueden superar 5) y se asigna la primera posición libre.</li>
 *   <li>Si el registro falla, borrado compensatorio del objeto ya subido.</li>
 * </ol>
 *
 * <p>Eliminación: primero la fila (la base es la fuente de verdad: la foto desaparece de inmediato), después el
 * objeto. Si MinIO falla en ese segundo paso, el objeto queda huérfano —inaccesible desde la aplicación— y se
 * registra para limpieza; nunca queda una referencia a una foto inexistente.
 */
@Service
public class ImagenService {

    private static final Logger log = LoggerFactory.getLogger(ImagenService.class);
    static final String CAMPO_ARCHIVO = "archivo";

    private final DepartamentoRepository departamentoRepository;
    private final ImagenRepository imagenRepository;
    private final ObjectStorageService storage;
    private final DepartamentoMapper mapper;
    private final TransactionTemplate transactionTemplate;
    private final long maxFileSize;

    public ImagenService(DepartamentoRepository departamentoRepository, ImagenRepository imagenRepository,
            ObjectStorageService storage, DepartamentoMapper mapper, TransactionTemplate transactionTemplate,
            StorageProperties storageProperties) {
        this.departamentoRepository = departamentoRepository;
        this.imagenRepository = imagenRepository;
        this.storage = storage;
        this.mapper = mapper;
        this.transactionTemplate = transactionTemplate;
        this.maxFileSize = storageProperties.maxFileSize().toBytes();
    }

    public ImagenResponse subir(Long departamentoId, InputStreamSource contenido, long sizeBytes) {
        Departamento departamento = departamentoRepository.findById(departamentoId)
                .orElseThrow(() -> new ResourceNotFoundException(DepartamentoService.RECURSO));
        validarModificable(departamento);
        ImageType tipo = validar(contenido, sizeBytes);
        if (imagenRepository.countByDepartamentoId(departamentoId) >= Imagen.MAX_POR_DEPARTAMENTO) {
            throw limiteAlcanzado();
        }

        // Clave generada por el backend: sin nombre ni texto del usuario (evita path traversal y colisiones).
        String objectKey = "departamentos/%d/%s.%s".formatted(departamentoId, UUID.randomUUID(), tipo.extension());
        storage.upload(objectKey, contenido, sizeBytes, tipo.contentType());

        Imagen imagen;
        try {
            imagen = transactionTemplate.execute(status -> registrar(departamentoId, objectKey, tipo, sizeBytes));
        } catch (RuntimeException e) {
            storage.deleteCompensating(objectKey);
            throw e;
        }
        log.info("Imagen agregada", kv("departamentoId", departamentoId), kv("imagenId", imagen.getId()),
                kv("posicion", imagen.getPosicion()), kv("objectKey", objectKey));
        return mapper.toResponse(imagen);
    }

    public void eliminar(Long departamentoId, Long imagenId) {
        String objectKey = transactionTemplate.execute(status -> {
            Imagen imagen = imagenRepository.findByIdAndDepartamentoId(imagenId, departamentoId)
                    .orElseThrow(() -> new ResourceNotFoundException("imagen"));
            validarModificable(imagen.getDepartamento());
            imagenRepository.delete(imagen);
            return imagen.getObjectKey();
        });
        log.info("Imagen eliminada", kv("departamentoId", departamentoId), kv("imagenId", imagenId),
                kv("objectKey", objectKey));
        try {
            storage.delete(objectKey);
        } catch (DependencyUnavailableException e) {
            log.warn("Imagen eliminada de la base pero no del storage: objeto huérfano",
                    kv("departamentoId", departamentoId), kv("imagenId", imagenId), kv("objectKey", objectKey));
        }
    }

    private Imagen registrar(Long departamentoId, String objectKey, ImageType tipo, long sizeBytes) {
        departamentoRepository.lockById(departamentoId)
                .orElseThrow(() -> new ResourceNotFoundException(DepartamentoService.RECURSO));
        List<Integer> ocupadas = imagenRepository.findPosiciones(departamentoId);
        if (ocupadas.size() >= Imagen.MAX_POR_DEPARTAMENTO) {
            throw limiteAlcanzado();
        }
        int posicion = 0;
        while (ocupadas.contains(posicion)) {
            posicion++;
        }
        Departamento departamento = departamentoRepository.getReferenceById(departamentoId);
        return imagenRepository.saveAndFlush(
                new Imagen(departamento, objectKey, tipo.contentType(), sizeBytes, posicion));
    }

    private ImageType validar(InputStreamSource contenido, long sizeBytes) {
        if (sizeBytes <= 0) {
            throw new InvalidRequestException(CAMPO_ARCHIVO, "no puede estar vacío");
        }
        if (sizeBytes > maxFileSize) {
            throw new PayloadTooLargeException("La imagen supera el tamaño máximo de " + (maxFileSize / 1024 / 1024)
                    + " MB");
        }
        byte[] header;
        try (InputStream stream = contenido.getInputStream()) {
            header = stream.readNBytes(ImageType.SIGNATURE_LENGTH);
        } catch (IOException e) {
            throw new InvalidRequestException(CAMPO_ARCHIVO, "no se pudo leer el archivo");
        }
        return ImageType.detect(header).orElseThrow(() ->
                new InvalidRequestException(CAMPO_ARCHIVO, "debe ser una imagen JPEG, PNG o WebP"));
    }

    /** Un departamento vendido es un registro cerrado: sus fotos tampoco cambian. */
    private static void validarModificable(Departamento departamento) {
        if (!departamento.getEstado().esModificable()) {
            throw new BusinessRuleException(ErrorCode.DEPARTAMENTO_NO_DISPONIBLE,
                    "El departamento ya fue vendido: no se pueden agregar ni eliminar fotos");
        }
    }

    private static BusinessRuleException limiteAlcanzado() {
        return new BusinessRuleException(ErrorCode.LIMITE_IMAGENES_ALCANZADO,
                "El departamento ya tiene el máximo de " + Imagen.MAX_POR_DEPARTAMENTO + " fotos");
    }
}
