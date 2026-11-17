### Cómo se califican los hallazgos

Cada vulnerabilidad se registra con cuatro valores de 0 a 10: confidencialidad (C), integridad (I), disponibilidad (A) y probabilidad. Para presentarlos, esos valores se convierten a una escala de cinco niveles con los cortes de NIST SP 800-30 Rev. 1 llevados a la escala de 0 a 10:

| Valor registrado | Nivel | Significado |
|---|---|---|
| 0 | 1 | Muy bajo |
| 1 a 2 | 2 | Bajo |
| 3 a 7 | 3 | Medio |
| 8 a 9 | 4 | Alto |
| 10 | 5 | Muy alto |

**Impacto.** Es el nivel de la dimensión más alta entre C, I y A (regla del punto más alto de FIPS 199): un hallazgo grave en una sola dimensión basta para elevar su impacto.

**Severidad.** Se deriva solo del impacto: nivel 5 es Crítica, 4 Alta, 3 Media, 2 Baja y 1 Informativa. No depende de la probabilidad.

**Probabilidad.** Nivel de 1 a 5: Muy baja, Baja, Media, Alta y Muy alta. El auditor considera la complejidad del ataque, los privilegios requeridos, la interacción del usuario y si existe un exploit público.

**Riesgo.** Combina probabilidad e impacto en la matriz de NIST SP 800-30 Rev. 1 (Tabla I-2), con cinco niveles: Muy bajo, Bajo, Medio, Alto y Crítico. El puntaje (probabilidad por impacto, de 1 a 25) se usa solo para ordenar; el nivel sale de la matriz y no del puntaje.

**Fase sugerida.** Se deriva de la severidad: Crítica en F1, Alta en F2 y el resto en F3. Es una sugerencia; los plazos los acuerda el cliente.
