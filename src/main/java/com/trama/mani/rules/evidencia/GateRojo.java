package com.trama.mani.rules.evidencia;

/**
 * SCRUM-1681 (QAS-45). NO FUSIONAR.
 * Codigo con issues a proposito para comprobar que el Quality Gate bloquea el pull request.
 */
public class GateRojo {

    private int sinUsar = 0;

    public int suma(int a, int b) {
        int temporal = a * b;
        if (a > b) {
            return a + b;
        } else {
            return a + b;
        }
    }
}
