package com.lebane.departamento.mapper;

import static com.lebane.departamento.mapper.Textos.opcional;
import static com.lebane.departamento.mapper.Textos.requerido;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import com.lebane.departamento.dto.DepartamentoDetailResponse;
import com.lebane.departamento.dto.DepartamentoListItemResponse;
import com.lebane.departamento.dto.DepartamentoListadoParams;
import com.lebane.departamento.dto.DepartamentoRequest;
import com.lebane.departamento.dto.DireccionRequest;
import com.lebane.departamento.dto.DireccionResponse;
import com.lebane.departamento.dto.ImagenResponse;
import com.lebane.departamento.entity.Departamento;
import com.lebane.departamento.entity.Direccion;
import com.lebane.departamento.entity.EstadoDepartamento;
import com.lebane.departamento.entity.Imagen;
import com.lebane.departamento.repository.DepartamentoAgregados;
import com.lebane.departamento.repository.DepartamentoFiltro;
import com.lebane.departamento.repository.DepartamentoListadoRow;
import com.lebane.storage.service.ImageUrlResolver;

/** Conversión entre DTOs y entidades de departamento. Las entidades nunca salen de la capa de servicio. */
@Component
public class DepartamentoMapper {

    private final ImageUrlResolver imageUrlResolver;

    public DepartamentoMapper(ImageUrlResolver imageUrlResolver) {
        this.imageUrlResolver = imageUrlResolver;
    }

    public Departamento toNewEntity(DepartamentoRequest request, String codigo) {
        EstadoDepartamento estado = request.estado() != null ? request.estado() : EstadoDepartamento.DISPONIBLE;
        Departamento departamento = new Departamento(codigo, estado);
        applyDatos(departamento, request);
        return departamento;
    }

    /** Edición como reemplazo completo; el estado solo cambia si se informa. */
    public void applyUpdate(Departamento departamento, DepartamentoRequest request) {
        applyDatos(departamento, request);
        if (request.estado() != null) {
            departamento.cambiarEstado(request.estado());
        }
    }

    public DepartamentoDetailResponse toDetail(Departamento departamento, List<Imagen> imagenes,
            long cantidadConsultas) {
        return new DepartamentoDetailResponse(
                departamento.getId(),
                departamento.getCodigo(),
                departamento.getTitulo(),
                departamento.getDescripcion(),
                departamento.getPrecio(),
                departamento.getMoneda(),
                departamento.getAmbientes(),
                departamento.getDormitorios(),
                departamento.getBanos(),
                departamento.getSuperficieM2(),
                departamento.getEstado(),
                toResponse(departamento.getDireccion()),
                imagenes.stream().map(this::toResponse).toList(),
                cantidadConsultas,
                departamento.getVersion(),
                departamento.getCreatedAt(),
                departamento.getUpdatedAt());
    }

    /** Una fila del listado con sus agregados (calculados en PostgreSQL; ceros si no tiene fotos ni consultas). */
    public DepartamentoListItemResponse toListItem(DepartamentoListadoRow row, DepartamentoAgregados agregados) {
        DepartamentoAgregados datos = agregados != null ? agregados : DepartamentoAgregados.VACIO;
        String imagenPrincipalUrl = datos.imagenPrincipalKey() != null
                ? imageUrlResolver.urlFor(datos.imagenPrincipalKey())
                : null;
        return new DepartamentoListItemResponse(row.id(), row.codigo(), row.titulo(), row.precio(), row.moneda(),
                row.ambientes(), row.dormitorios(), row.banos(), row.superficieM2(), row.estado(), row.ciudad(),
                row.provincia(), imagenPrincipalUrl, datos.cantidadImagenes(), datos.cantidadConsultas(),
                row.createdAt());
    }

    /** Parámetros web (ya validados y normalizados) → criterios del repositorio. */
    public DepartamentoFiltro toFiltro(DepartamentoListadoParams params) {
        Set<EstadoDepartamento> estados = params.estado().isEmpty() ? Set.of() : EnumSet.copyOf(params.estado());
        return new DepartamentoFiltro(params.q(), params.ciudad(), estados, params.moneda(), params.precioMin(),
                params.precioMax(), params.ambientesMin(), params.dormitoriosMin(), params.banosMin(),
                params.superficieMin(), params.superficieMax(), params.conImagenes());
    }

    public ImagenResponse toResponse(Imagen imagen) {
        return new ImagenResponse(imagen.getId(), imageUrlResolver.urlFor(imagen.getObjectKey()),
                imagen.getContentType(), imagen.getSizeBytes(), imagen.getPosicion());
    }

    private void applyDatos(Departamento departamento, DepartamentoRequest request) {
        departamento.actualizarDatos(
                requerido(request.titulo()),
                opcional(request.descripcion()),
                request.precio(),
                request.moneda(),
                request.ambientes(),
                request.dormitorios(),
                request.banos(),
                request.superficieM2(),
                toEntity(request.direccion()));
    }

    private static Direccion toEntity(DireccionRequest request) {
        return new Direccion(
                requerido(request.calle()),
                requerido(request.numero()),
                opcional(request.piso()),
                opcional(request.unidad()),
                requerido(request.ciudad()),
                requerido(request.provincia()),
                opcional(request.codigoPostal()),
                request.latitud(),
                request.longitud(),
                opcional(request.placeId()));
    }

    private static DireccionResponse toResponse(Direccion direccion) {
        return new DireccionResponse(direccion.getCalle(), direccion.getNumero(), direccion.getPiso(),
                direccion.getUnidad(), direccion.getCiudad(), direccion.getProvincia(), direccion.getCodigoPostal(),
                direccion.getLatitud(), direccion.getLongitud(), direccion.getPlaceId());
    }
}
