package com.hexora.network.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.hexora.hexora.HexoraApplication;
import com.hexora.test.IsolatedMySqlTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

@SpringBootTest(classes = HexoraApplication.class, properties = "hexora.network.discovery-enabled=false")
@AutoConfigureMockMvc
class NetworkMonitoringWebTest extends IsolatedMySqlTestDatabase {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesAnIndependentMonitoringPage() throws Exception {
        performAsAuditor(get("/network-monitoring"))
            .andExpect(status().isOk())
            .andExpect(view().name("network-monitoring"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Monitorización de red")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dispositivos conectados")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Ver historial")));
    }

    @Test
    void servesDiscoveryHistoryInItsOwnPage() throws Exception {
        performAsAuditor(get("/network-monitoring/history"))
            .andExpect(status().isOk())
            .andExpect(view().name("network-monitoring-history"))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Historial de detecciones")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Disponibilidad estimada")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Borrar todo el historial")));
    }

    @Test
    void exposesDashboardStatus() throws Exception {
        performAsAuditor(get("/api/network/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.hostsMonitored").isNumber())
            .andExpect(jsonPath("$.hosts").isArray());
    }

    @Test
    void exposesTheDetectedDeviceInventory() throws Exception {
        performAsAuditor(get("/api/network/detected-devices"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());
    }

    @Test
    void exposesTheGatewayEndpoint() throws Exception {
        performAsAuditor(get("/api/network/gateway"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void exposesDiscoverySnapshotsAndTheLatestDetection() throws Exception {
        performAsAuditor(get("/api/network/discoveries/history"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray());

        performAsAuditor(get("/api/network/discoveries/latest"))
            .andExpect(status().is2xxSuccessful());
    }

    @Test
    void monitoringPageShowsTheAutomaticScanActionAndDetectedInventory() throws Exception {
        performAsAuditor(get("/network-monitoring"))
            .andExpect(status().isOk())
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Detectar dispositivos en la red")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Método de detección")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("Dispositivos conectados")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("ACTUALIZACIÓN AUTOMÁTICA")));
    }

    @Test
    void rejectsAnOversizedRangeBeforeStartingAScan() throws Exception {
        performAsAuditor(post("/api/network/scan").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hostId\":1,\"startPort\":1,\"endPort\":129}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(
                "Port range must be valid, ascending, and no larger than 128 ports"));
    }

    @Test
    void refusesToRegisterDevicesOutsidePrivateIpv4Ranges() throws Exception {
        performAsAuditor(post("/api/network/hosts").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"External host\",\"address\":\"8.8.8.8\",\"ports\":[443]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Enter a private IPv4 address from your local network"));
    }

    @Test
    void mapsDeviceRegistrationPostAndReturnsValidationInsteadOfMethodNotAllowed() throws Exception {
        performAsAuditor(post("/api/network/devices").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"address\":\"192.168.1.25\",\"ports\":[443]}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("Device name must contain between 1 and 100 characters"));
    }

    @Test
    void exposesExpectedMethodsForDeviceAndPortEndpoints() throws Exception {
        performAsAuditor(options("/api/network/devices"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("POST")));

        performAsAuditor(options("/api/network/devices/1/ports/443"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")));
        performAsAuditor(options("/api/network/devices/1/ports/443"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("PUT")));

        performAsAuditor(options("/api/network/history"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")));
        performAsAuditor(options("/api/network/history/scans"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("DELETE")));
        performAsAuditor(options("/api/network/discover"))
            .andExpect(status().isOk())
            .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                .header().string("Allow", org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void mapsDevicePortAndDeleteActionsToTheirHttpMethods() throws Exception {
        String missingHostId = "9223372036854775807";

        performAsAuditor(post("/api/network/devices/" + missingHostId + "/ports").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"port\":8080}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());

        performAsAuditor(put("/api/network/devices/" + missingHostId + "/ports/8080").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":false}"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());

        performAsAuditor(delete("/api/network/devices/" + missingHostId + "/ports/8080").with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());

        performAsAuditor(delete("/api/network/devices/" + missingHostId).with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void reportsReceivedAndExpectedMethodsForMethodNotAllowed() throws Exception {
        performAsAuditor(put("/api/network/devices").with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
            .andExpect(status().isMethodNotAllowed())
            .andExpect(jsonPath("$.path").value("/api/network/devices"))
            .andExpect(jsonPath("$.receivedMethod").value("PUT"))
            .andExpect(jsonPath("$.allowedMethods").value(org.hamcrest.Matchers.containsString("POST")));
    }

    private ResultActions performAsAuditor(MockHttpServletRequestBuilder request) throws Exception {
        return mockMvc.perform(request.with(user("network-test").roles("AUDITOR")));
    }
}
