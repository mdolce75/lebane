package com.lebane.address;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.lebane.address.controller.DireccionController;
import com.lebane.address.dto.AutocompleteResponse;
import com.lebane.address.dto.SugerenciaDireccion;
import com.lebane.address.service.AddressAutocompleteService;
import com.lebane.config.ActuatorSecurityProperties;
import com.lebane.config.CorsProperties;
import com.lebane.config.SecurityConfig;

@WebMvcTest(DireccionController.class)
@Import(SecurityConfig.class)
@EnableConfigurationProperties({ActuatorSecurityProperties.class, CorsProperties.class})
class DireccionControllerTest {

    private static final String URL = "/api/direcciones/autocompletar";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressAutocompleteService service;

    @Test
    void returnsSuggestions() throws Exception {
        when(service.autocompletar(eq("Gorriti 48"), isNull())).thenReturn(AutocompleteResponse.ok(List.of(
                new SugerenciaDireccion("Gorriti", "4850", "CABA", "CABA", null, null, "stub:0", "Gorriti 4850")),
                "stub"));

        mockMvc.perform(get(URL).param("q", "Gorriti 48"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sugerencias[0].calle").value("Gorriti"))
                .andExpect(jsonPath("$.proveedor").value("stub"))
                .andExpect(jsonPath("$.degradado").value(false))
                .andExpect(jsonPath("$.mensaje").doesNotExist());
    }

    @Test
    void degradedResponseIs200() throws Exception {
        when(service.autocompletar(eq("Gorriti"), isNull())).thenReturn(AutocompleteResponse.degradado("georef"));

        mockMvc.perform(get(URL).param("q", "Gorriti"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.degradado").value(true))
                .andExpect(jsonPath("$.sugerencias").isEmpty())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void validatesQueryParams() throws Exception {
        mockMvc.perform(get(URL).param("q", "ab").param("limite", "50"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.q").exists())
                .andExpect(jsonPath("$.fieldErrors.limite").exists());
        mockMvc.perform(get(URL))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.q").value("es obligatorio"));
        verifyNoInteractions(service);
    }
}
