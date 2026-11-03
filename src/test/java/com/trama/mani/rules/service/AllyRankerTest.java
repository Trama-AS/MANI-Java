package com.trama.mani.rules.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.trama.mani.rules.dto.AllyRankingRequest.Candidato;
import com.trama.mani.rules.dto.AllyRankingResponse.RankedAlly;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class AllyRankerTest {

    private final AllyRanker ranker = new AllyRanker();

    private static Candidato valorado(String id, String promedio, int total, Integer zonas) {
        return new Candidato(id, new BigDecimal(promedio), total, zonas);
    }

    private static Candidato sinValorar(String id, Integer zonas) {
        return new Candidato(id, null, 0, zonas);
    }

    private static List<String> ids(List<RankedAlly> ranking) {
        return ranking.stream().map(RankedAlly::id).toList();
    }

    @Test
    void calificacionOrdenaDeMayorAMenorConPuntajeNormalizado() {
        List<RankedAlly> ranking = ranker.rank(CriterioRanking.CALIFICACION, List.of(
                valorado("a", "3.5", 4, null),
                valorado("b", "5.0", 1, null),
                valorado("c", "4.8", 12, null)));

        assertThat(ids(ranking)).containsExactly("b", "c", "a");
        assertThat(ranking).extracting(RankedAlly::posicion).containsExactly(1, 2, 3);
        assertThat(ranking.get(0).puntaje()).isEqualByComparingTo("1");
        assertThat(ranking.get(1).puntaje()).isEqualByComparingTo("0.96");
        assertThat(ranking.get(2).puntaje()).isEqualByComparingTo("0.7");
        assertThat(ranking.get(0).razones()).containsExactly("Calificación promedio 5 de 5 (1 calificación)");
        assertThat(ranking.get(1).razones()).containsExactly("Calificación promedio 4.8 de 5 (12 calificaciones)");
    }

    @Test
    void coberturaOrdenaPorZonasConPuntajeRelativoAlMayor() {
        List<RankedAlly> ranking = ranker.rank(CriterioRanking.COBERTURA, List.of(
                valorado("a", "5.0", 9, 1),
                valorado("b", "1.0", 1, 4),
                valorado("c", "3.0", 2, 0)));

        assertThat(ids(ranking)).containsExactly("b", "a", "c");
        assertThat(ranking.get(0).puntaje()).isEqualByComparingTo("1");
        assertThat(ranking.get(1).puntaje()).isEqualByComparingTo("0.25");
        assertThat(ranking.get(2).puntaje()).isEqualByComparingTo("0");
        assertThat(ranking.get(0).razones()).containsExactly("Cubre 4 zonas");
        assertThat(ranking.get(1).razones()).containsExactly("Cubre 1 zona");
        assertThat(ranking.get(2).razones()).containsExactly("Sin zonas de cobertura");
    }

    @Test
    void coberturaSinZonasEnNingunCandidatoDaPuntajeCero() {
        List<RankedAlly> ranking = ranker.rank(CriterioRanking.COBERTURA, List.of(valorado("a", "4.0", 1, 0)));

        assertThat(ranking.get(0).puntaje()).isEqualByComparingTo("0");
    }

    @Test
    void empatesSeResuelvenPorIdEnCadaCriterio() {
        List<Candidato> candidatos = List.of(
                valorado("c", "4.50", 1, 2),
                valorado("a", "4.5", 30, 2),
                valorado("b", "4.500", 7, 2));

        assertThat(ids(ranker.rank(CriterioRanking.CALIFICACION, candidatos))).containsExactly("a", "b", "c");
        assertThat(ids(ranker.rank(CriterioRanking.COBERTURA, candidatos))).containsExactly("a", "b", "c");
    }

    @Test
    void sinCalificacionVaDespuesDeLosValoradosYNoValeCero() {
        List<RankedAlly> ranking = ranker.rank(CriterioRanking.CALIFICACION, List.of(
                sinValorar("z", null),
                valorado("y", "1.0", 1, null),
                sinValorar("a", null)));

        assertThat(ids(ranking)).containsExactly("y", "a", "z");
        assertThat(ranking.get(1).puntaje()).isNull();
        assertThat(ranking.get(2).puntaje()).isNull();
        assertThat(ranking.get(1).razones()).containsExactly("Sin calificaciones todavía: va después de los valorados");
    }

    @Test
    void conCoberturaElSinCalificacionTambienVaDespuesPeroConservaSuPuntaje() {
        List<RankedAlly> ranking = ranker.rank(CriterioRanking.COBERTURA, List.of(
                sinValorar("nuevo", 8),
                valorado("viejo", "2.0", 3, 2)));

        assertThat(ids(ranking)).containsExactly("viejo", "nuevo");
        assertThat(ranking.get(1).puntaje()).isEqualByComparingTo("1");
        assertThat(ranking.get(1).razones())
                .containsExactly("Cubre 8 zonas", "Sin calificaciones todavía: va después de los valorados");
    }

    @Test
    void mismoInputDaMismoOrdenSinImportarComoLlegue() {
        List<Candidato> candidatos = new ArrayList<>(List.of(
                valorado("a", "4.0", 2, 3),
                valorado("b", "4.0", 5, 3),
                valorado("c", "4.9", 1, 1),
                sinValorar("d", 9),
                sinValorar("e", 9)));
        List<RankedAlly> calificacion = ranker.rank(CriterioRanking.CALIFICACION, candidatos);
        List<RankedAlly> cobertura = ranker.rank(CriterioRanking.COBERTURA, candidatos);

        Random azar = new Random(1462);
        for (int i = 0; i < 20; i++) {
            Collections.shuffle(candidatos, azar);
            assertThat(ranker.rank(CriterioRanking.CALIFICACION, candidatos)).isEqualTo(calificacion);
            assertThat(ranker.rank(CriterioRanking.COBERTURA, candidatos)).isEqualTo(cobertura);
        }
    }

    @Test
    void datosCoherentesNoTienenProblema() {
        List<Candidato> candidatos = List.of(valorado("a", "4.0", 2, 3), sinValorar("b", 0));

        assertThat(ranker.datosInvalidos(CriterioRanking.COBERTURA, candidatos)).isEmpty();
    }

    @Test
    void zonasSoloSonObligatoriasConCobertura() {
        List<Candidato> candidatos = List.of(valorado("a", "4.0", 2, null));

        assertThat(ranker.datosInvalidos(CriterioRanking.CALIFICACION, candidatos)).isEmpty();
        assertThat(ranker.datosInvalidos(CriterioRanking.COBERTURA, candidatos)).hasValueSatisfying(
                problema -> assertThat(problema).contains("a").contains("zonasCobertura"));
    }

    @Test
    void rechazaIdRepetido() {
        List<Candidato> candidatos = List.of(valorado("a", "4.0", 2, 1), valorado("a", "3.0", 1, 1));

        assertThat(ranker.datosInvalidos(CriterioRanking.CALIFICACION, candidatos)).hasValueSatisfying(
                problema -> assertThat(problema).contains("repetido"));
    }

    @Test
    void rechazaPromedioIncoherenteConElTotal() {
        List<Candidato> totalSinPromedio = List.of(new Candidato("a", null, 3, null));
        List<Candidato> promedioSinTotal = List.of(new Candidato("b", BigDecimal.valueOf(4), 0, null));

        assertThat(ranker.datosInvalidos(CriterioRanking.CALIFICACION, totalSinPromedio)).isPresent();
        assertThat(ranker.datosInvalidos(CriterioRanking.CALIFICACION, promedioSinTotal)).isPresent();
    }

    @Test
    void criterioSoloAceptaLosValoresDefinidos() {
        assertThat(CriterioRanking.parse(" calificacion ")).contains(CriterioRanking.CALIFICACION);
        assertThat(CriterioRanking.parse("COBERTURA")).contains(CriterioRanking.COBERTURA);
        assertThat(CriterioRanking.parse("OTRO")).isEmpty();
        assertThat(CriterioRanking.parse(null)).isEmpty();
    }
}
