package com.example.cleancarsapi;

import com.example.cleancarsapi.entity.FeatureProperty;
import com.example.cleancarsapi.exception.BadRequestException;
import com.example.cleancarsapi.exception.ConflictException;
import com.example.cleancarsapi.repository.FeaturePropertyRepository;
import com.example.cleancarsapi.service.FeaturePropertyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Switch reads, the public {@code Client.*} filter, and the keep-the-JSON-type rule on writes. */
class FeaturePropertyServiceTest {

    final ObjectMapper mapper = new ObjectMapper();
    FeaturePropertyRepository repo;
    FeaturePropertyService service;

    @BeforeEach
    void setup() {
        repo = mock(FeaturePropertyRepository.class);
        when(repo.save(any(FeatureProperty.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new FeaturePropertyService(repo, mapper);
    }

    @Test
    void isEnabledReadsBooleanAndTreatsMissingOrNonBooleanAsOff() {
        when(repo.findByOrderByKeyAsc()).thenReturn(List.of(
                new FeatureProperty("Client.Maintenance.Mode.Enable", "true"),
                new FeatureProperty("Client.New.Logins.Disabled", "\"true\"")));

        assertTrue(service.isEnabled(FeaturePropertyService.MAINTENANCE_MODE));
        assertFalse(service.isEnabled(FeaturePropertyService.NEW_LOGINS_DISABLED));
        assertFalse(service.isEnabled("Client.Unknown"));
    }

    @Test
    void publicPropertiesOnlyExposeClientPrefix() {
        when(repo.findByOrderByKeyAsc()).thenReturn(List.of(
                new FeatureProperty("Client.A", "false"),
                new FeatureProperty("Server.Secret", "\"x\"")));

        assertEquals(List.of("Client.A"), List.copyOf(service.publicProperties().keySet()));
    }

    @Test
    void setRejectsChangingTheJsonType() {
        when(repo.findByKey("Client.A")).thenReturn(Optional.of(new FeatureProperty("Client.A", "false")));

        assertThrows(ConflictException.class,
                () -> service.set("Client.A", mapper.readTree("\"false\""), null, "dev@x.com"));
    }

    @Test
    void setUpdatesSameTypeAndCreatesNewKey() {
        when(repo.findByKey("Client.A")).thenReturn(Optional.of(new FeatureProperty("Client.A", "false")));
        when(repo.findByKey("Client.B")).thenReturn(Optional.empty());

        assertEquals("true", service.set("Client.A", mapper.readTree("true"), null, "dev@x.com").getValueJson());
        FeatureProperty created = service.set("Client.B", mapper.readTree("{\"n\":1}"), "desc", "dev@x.com");
        assertEquals("{\"n\":1}", created.getValueJson());
        assertEquals("desc", created.getDescription());
    }

    @Test
    void setRejectsBadKeyAndNullValue() {
        assertThrows(BadRequestException.class,
                () -> service.set("bad key!", mapper.readTree("true"), null, "dev@x.com"));
        assertThrows(BadRequestException.class,
                () -> service.set("Client.A", mapper.readTree("null"), null, "dev@x.com"));
    }
}
