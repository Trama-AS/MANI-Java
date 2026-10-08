package com.trama.mani.rules;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Prueba de humo (QA-06 / SCRUM-1123): el contexto de Spring arranca completo.
 * Si una configuración o un bean futuro rompe el arranque, falla aquí primero.
 */
@SpringBootTest
class ManiRulesApplicationTests {

    @Test
    void contextLoads() {
    }
}
