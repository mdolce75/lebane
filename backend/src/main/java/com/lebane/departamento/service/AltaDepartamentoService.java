package com.lebane.departamento.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.entity.Imagen;
import com.lebane.exception.InvalidRequestException;
import com.lebane.storage.service.ImageType;
import com.lebane.storage.service.ObjectStorageService;

/**
 * Alta de un departamento con sus fotos en el mismo request ({@code POST /api/departamentos}, multipart). Todo o
 * nada: si algo falla, no queda ni el departamento ni fotos sueltas en el storage.
 *
 * <ol>
 *   <li>Validación barata antes de tocar la red: hasta {@value Imagen#MAX_POR_DEPARTAMENTO} fotos, cada una no vacía,
 *       dentro del tamaño máximo y con contenido real JPEG, PNG o WebP; reglas del alta (no vendido, dirección
 *       libre).</li>
 *   <li>Subida de las fotos a MinIO, fuera de toda transacción (nunca se retiene una conexión de base mientras se
 *       transfiere un archivo).</li>
 *   <li>Registro del departamento y de sus fotos en una sola transacción.</li>
 *   <li>Si falla una subida o el registro, borrado compensatorio de las fotos ya subidas.</li>
 * </ol>
 */
@Service
public class AltaDepartamentoService {

    private static final Logger log = LoggerFactory.getLogger(AltaDepartamentoService.class);
    static final String CAMPO_IMAGENES = "imagenes";

    private final DepartamentoService departamentoService;
    private final ImagenService imagenService;
    private final ObjectStorageService storage;

    public AltaDepartamentoService(DepartamentoService departamentoService, ImagenService imagenService,
            ObjectStorageService storage) {
        this.departamentoService = departamentoService;
        this.imagenService = imagenService;
        this.storage = storage;
    }

    public DepartamentoDetailResponse crear(DepartamentoRequest request, List<MultipartFile> archivos) {
        List<MultipartFile> fotos = archivos == null ? List.of()
                : archivos.stream().filter(archivo -> archivo != null && !archivo.isEmpty()).toList();
        if (fotos.size() > Imagen.MAX_POR_DEPARTAMENTO) {
            throw new InvalidRequestException(CAMPO_IMAGENES,
                    "admite hasta " + Imagen.MAX_POR_DEPARTAMENTO + " fotos");
        }
        List<ImageType> tipos = new ArrayList<>(fotos.size());
        for (int i = 0; i < fotos.size(); i++) {
            MultipartFile foto = fotos.get(i);
            tipos.add(imagenService.validarArchivo(foto, foto.getSize(), CAMPO_IMAGENES + "[" + i + "]"));
        }
        if (fotos.isEmpty()) {
            return departamentoService.crear(request);
        }
        departamentoService.validarAlta(request);

        List<ImagenSubida> subidas = new ArrayList<>(fotos.size());
        try {
            for (int i = 0; i < fotos.size(); i++) {
                MultipartFile foto = fotos.get(i);
                ImageType tipo = tipos.get(i);
                // Clave generada por el backend: sin nombre ni texto del usuario (evita path traversal y colisiones).
                String objectKey = "departamentos/altas/%s.%s".formatted(UUID.randomUUID(), tipo.extension());
                storage.upload(objectKey, foto, foto.getSize(), tipo.contentType());
                subidas.add(new ImagenSubida(objectKey, tipo.contentType(), foto.getSize()));
            }
            return departamentoService.crear(request, subidas);
        } catch (RuntimeException e) {
            if (!subidas.isEmpty()) {
                log.warn("Alta con fotos fallida: se borran las fotos ya subidas", kv("fotos", subidas.size()));
                subidas.forEach(subida -> storage.deleteCompensating(subida.objectKey()));
            }
            throw e;
        }
    }
}
