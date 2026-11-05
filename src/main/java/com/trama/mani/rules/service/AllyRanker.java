package com.trama.mani.rules.service;

import com.trama.mani.rules.dto.AllyRankingRequest.Candidato;
import com.trama.mani.rules.dto.AllyRankingResponse.RankedAlly;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Orden de los aliados candidatos (RF-13, SCRUM-1462). Función pura: Rules no tiene base de datos, el dueño de los
 * datos envía las métricas reales y aquí solo se aplica la regla, así que el mismo conjunto de candidatos da siempre el
 * mismo orden, sin importar cómo lleguen.
 *
 * <p>Un aliado sin calificaciones no vale 0: no tiene dato, así que va después de los valorados con cualquier criterio.
 * Dentro de cada grupo manda la métrica del criterio y el empate se resuelve por id.
 */
@Service
public class AllyRanker {

    /** Las calificaciones de Core van de 1.0 a 5.0. */
    private static final BigDecimal CALIFICACION_MAXIMA = BigDecimal.valueOf(5);
    private static final int DECIMALES_PUNTAJE = 4;
    private static final String RAZON_SIN_CALIFICACIONES = "Sin calificaciones todavía: va después de los valorados";

    /** Primer problema de coherencia en los hechos recibidos, si lo hay. */
    public Optional<String> datosInvalidos(CriterioRanking criterio, List<Candidato> candidatos) {
        Set<String> ids = new HashSet<>();
        for (Candidato c : candidatos) {
            if (!ids.add(c.id())) {
                return Optional.of("El candidato " + c.id() + " está repetido");
            }
            if (tieneCalificaciones(c) == (c.calificacionPromedio() == null)) {
                return Optional.of("El candidato " + c.id()
                        + " debe traer calificacionPromedio si y solo si totalCalificaciones es mayor que 0");
            }
            if (criterio == CriterioRanking.COBERTURA && c.zonasCobertura() == null) {
                return Optional.of("El candidato " + c.id() + " debe traer zonasCobertura con el criterio COBERTURA");
            }
        }
        return Optional.empty();
    }

    public List<RankedAlly> rank(CriterioRanking criterio, List<Candidato> candidatos) {
        List<Candidato> ordenados = candidatos.stream().sorted(comparador(criterio)).toList();
        int maxZonas = criterio == CriterioRanking.COBERTURA ? maxZonas(candidatos) : 0;

        List<RankedAlly> resultado = new ArrayList<>(ordenados.size());
        for (Candidato c : ordenados) {
            int posicion = resultado.size() + 1;
            resultado.add(criterio == CriterioRanking.CALIFICACION
                    ? porCalificacion(posicion, c)
                    : porCobertura(posicion, c, maxZonas));
        }
        return resultado;
    }

    private static Comparator<Candidato> comparador(CriterioRanking criterio) {
        Comparator<Candidato> valoradosPrimero = Comparator.comparing(c -> !tieneCalificaciones(c));
        Comparator<Candidato> metrica = criterio == CriterioRanking.CALIFICACION
                ? Comparator.comparing(Candidato::calificacionPromedio,
                        Comparator.nullsLast(Comparator.reverseOrder()))
                : Comparator.comparing(Candidato::zonasCobertura, Comparator.reverseOrder());
        return valoradosPrimero.thenComparing(metrica).thenComparing(Candidato::id);
    }

    private static RankedAlly porCalificacion(int posicion, Candidato c) {
        if (!tieneCalificaciones(c)) {
            return new RankedAlly(c.id(), posicion, null, List.of(RAZON_SIN_CALIFICACIONES));
        }
        BigDecimal promedio = c.calificacionPromedio();
        String razon = "Calificación promedio " + promedio.stripTrailingZeros().toPlainString() + " de "
                + CALIFICACION_MAXIMA + " (" + cantidad(c.totalCalificaciones(), "calificación", "calificaciones") + ")";
        return new RankedAlly(c.id(), posicion, proporcion(promedio, CALIFICACION_MAXIMA), List.of(razon));
    }

    private static RankedAlly porCobertura(int posicion, Candidato c, int maxZonas) {
        int zonas = c.zonasCobertura();
        BigDecimal puntaje = maxZonas == 0
                ? BigDecimal.ZERO.setScale(DECIMALES_PUNTAJE)
                : proporcion(BigDecimal.valueOf(zonas), BigDecimal.valueOf(maxZonas));
        String razon = zonas == 0 ? "Sin zonas de cobertura" : "Cubre " + cantidad(zonas, "zona", "zonas");
        List<String> razones = tieneCalificaciones(c) ? List.of(razon) : List.of(razon, RAZON_SIN_CALIFICACIONES);
        return new RankedAlly(c.id(), posicion, puntaje, razones);
    }

    private static boolean tieneCalificaciones(Candidato c) {
        return c.totalCalificaciones() > 0;
    }

    private static int maxZonas(List<Candidato> candidatos) {
        return candidatos.stream().mapToInt(Candidato::zonasCobertura).max().orElse(0);
    }

    private static BigDecimal proporcion(BigDecimal valor, BigDecimal maximo) {
        return valor.divide(maximo, DECIMALES_PUNTAJE, RoundingMode.HALF_UP);
    }

    private static String cantidad(int n, String singular, String plural) {
        return n + " " + (n == 1 ? singular : plural);
    }
}
