package md.ttm.service.tournament;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinalsSettingsTest {

    @Test
    void treiGrupeDe5_5_4_treiFinaleCuCate2() {
        // finalele 1 și 2: locurile 1–4 din fiecare grupă; finala 3: locurile 5 din grupele de 5 (2 jucători)
        assertThat(FinalsSettings.maxQualifiers(3, List.of(5, 5, 4))).isEqualTo(2);
        assertThat(FinalsSettings.maxQualifiers(2, List.of(5, 5, 4))).isEqualTo(4);
    }

    @Test
    void ultimaFinalaAreNevoieDeCelPutin2Jucatori() {
        // grupe de 4, 4, 4 și 3 finale: cu câte 2, în finala 3 nu mai rămâne nimeni
        assertThat(FinalsSettings.maxQualifiers(3, List.of(4, 4, 4))).isEqualTo(1);
        // grupe de 5 și 4, două finale: cu câte 4, în ultima ar rămâne un singur jucător
        assertThat(FinalsSettings.maxQualifiers(2, List.of(5, 4))).isEqualTo(3);
        assertThat(FinalsSettings.maxQualifiers(2, List.of(4, 4))).isEqualTo(3);
    }

    @Test
    void oSinguraGrupa_cateCelPutin2JucatoriInFiecareFinala() {
        assertThat(FinalsSettings.minQualifiers(1)).isEqualTo(2);
        assertThat(FinalsSettings.maxQualifiers(2, List.of(6))).isEqualTo(4);
        assertThat(FinalsSettings.maxQualifiers(3, List.of(6))).isEqualTo(2);
        assertThat(FinalsSettings.maxQualifiers(3, List.of(5))).isEqualTo(0);
    }

    @Test
    void preaMulteFinale() {
        assertThat(FinalsSettings.maxQualifiers(4, List.of(3, 3))).isEqualTo(0);
        assertThat(FinalsSettings.maxQualifiers(1, List.of(5, 5))).isEqualTo(0);
    }

    @Test
    void finalaPentruLoc() {
        FinalsSettings settings = new FinalsSettings(3, 2, java.util.Set.of(2));
        assertThat(settings.finalFor(1)).isEqualTo(1);
        assertThat(settings.finalFor(4)).isEqualTo(2);
        assertThat(settings.finalFor(5)).isEqualTo(3);
    }
}
