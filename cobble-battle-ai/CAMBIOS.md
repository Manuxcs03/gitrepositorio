# Cobble Battle AI: cambios generales

## Qué estaba demasiado ajustado a combates concretos

Cada derrota añadía un test de repetición (`Tower*Replay`) y una regla para ese caso. Casi todas las reglas
ya eran generales, pero algunas partes solo funcionaban bien con tu equipo de sol o con los rivales de la Torre:

| Antes | Problema | Ahora |
|---|---|---|
| El peligro al cambiar (`switchInDanger`, `entryDanger`, `outrunKo`, `comboDanger`) solo contaba movimientos **ya revelados** | Un rival que acaba de salir no ha enseñado nada, así que parecía inofensivo, que es justo cuando se deja un Pokémon con debilidad x4 delante (Garchomp contra Mewtwo con Rayo Hielo en el combate 31) | Cuentan también los movimientos probables no vistos, cada uno pesado por su probabilidad (`Planner.threatWeight`). Los que tienen menos de un 30 % no cuentan, y si el rival es más lento solo cuentan los revelados |
| "Quién ataca primero" comparaba la Velocidad a secas | Con Espacio Raro el rival lento ataca primero, y un Canto Helado o un Golpe Bajo ignoran la Velocidad | `Speed.movesFirst` tiene en cuenta Espacio Raro, y el cálculo de "me tumba antes de moverme" incluye los movimientos con prioridad |
| Probabilidad de Viento Afín por **especie** (tabla medida en la Torre) | Cualquier especie que no esté en la tabla recibía un 10 % aunque tuviera Bromista | Cualquier Pokémon que pueda tener Bromista cuenta como posible usuario de Viento Afín (≥45 %) |
| Movimientos adivinados, cada uno por separado | Si el rival ya enseñó Garra Dragón, se seguía temiendo un Enfado adivinado | Un ataque adivinado del mismo tipo y categoría que uno ya revelado cuenta la mitad (Avalancha con Roca Afilada sigue contando entera, porque uno es de área y el otro no) |

Ningún test anterior cambia de decisión. Todos siguen pasando.

## Tests nuevos que no dependen de tu equipo

- `GeneralScenarios`: equipos de lluvia, Espacio Raro, arena e individuales, sin nada que ver con tu equipo de sol.
  En los casos de peligro, el margen entre la opción segura y la peligrosa pasa de 0.46 a 0.86 (Espacio Raro)
  y de 0.27 a 1.17 (cobertura oculta).
- `FuzzScenarios`: 120 tableros aleatorios (tipos, stats, movimientos, clima, Espacio Raro y Viento Afín
  al azar). Comprueba que la jugada siempre es legal, que nada peta, que nunca ataca a un objetivo inmune
  teniendo otra opción y que cada decisión tarda poco (unos 25 ms de media).

Para ejecutarlo todo sin Minecraft: `./run-scenarios.sh`

## Motor en vivo (modo SUGERIR)

El mod ya tenía un modo en el que piensa pero no juega:

- **K**: cambia de modo (APAGADA → SUGERIR → AUTO).
- **J**: juega la sugerencia (en SUGERIR).
- **H** (nuevo): muestra u oculta el panel detallado.

El panel detallado (activo por defecto, `hudDetail` en `config/cobblebattleai.json`) muestra ahora:

1. La mejor jugada y el daño previsto (esto ya existía).
2. Alternativas, con cuánto peor es cada una.
3. **Decisión ajustada**, cuando la segunda opción es casi igual de buena. Ahí cuenta tu lectura del rival.
4. **Rival probable**: qué va a hacer cada rival y con qué probabilidad (p. ej. `Mewtwo: Rayo Hielo? -> Garchomp 45%`). El `?` indica un movimiento que todavía no ha usado.
5. **Cuidado**: qué Pokémon tuyo puede caer antes de moverse, y por qué movimiento.
6. PS restantes de cada lado, contados en Pokémon.

Todo esto se escribe también en `latest.log` con la etiqueta `[AI-ADVICE]`, para revisar los combates perdidos.

## Log 21: derrotas contra Experto Cirilo (#22) y Vigilante Cira (#4)

**Cirilo.** En el turno 2 Torkoal recibió un Psíquico antes de usar Estallido, y su Estallido salió más débil.
El calibrador (que aprende lo resistente o lo fuerte que es cada rival comparando el daño real con el previsto)
lo interpretó como que Latios y Metagross eran casi el doble de resistentes (x1.44 y x1.49), y además lo
**guardó en la memoria** para los siguientes combates contra ese entrenador. Con un Torkoal tan lento esto
pasaba en casi todos los combates. Ese mismo turno, Charizard entró delante de Metagross: Psicocolmillo más
el daño de Poder Solar al final del turno lo dejaron al 1%, y la comprobación de peligro solo miraba el golpe.

- `Readings.reliable` (nuevo, usado por el calibrador): descarta las lecturas cuyo poder o stats cambiaron
  durante el turno: Estallido/Salpicar/Energía Dragón con el usuario ya golpeado, movimientos de poder variable
  (Pataleta, Avalancha, Castigo...) y cambios de nivel de ataque o defensa a mitad de turno (Alarido, Intimidación).
  Sí acepta Cometa Draco o Sofoco, que bajan el stat después de golpear.
- La memoria (`cobblebattleai-memory.json`) ya no carga los factores de poder y resistencia aprendidos con el
  calibrador anterior; los movimientos, objetos y velocidades aprendidos se conservan.
- El peligro de un Pokémon que entra cuenta también el daño de final de turno (Poder Solar o Piel Seca con sol,
  tormenta de arena, quemadura, veneno).

**Cira.** Somnífero (75 % de precisión) falló, y Envite Ígneo con crítico debilitó a Venusaur. La IA sigue
eligiendo Somnífero ahí (con sol Venusaur es más rápido y dormir a Arcanine vale mucho, y en el combate #6 de
este mismo log le salió bien), pero ahora el panel lo avisa:
"Jugada arriesgada: si Somnífero falla (25%), Arcanine puede debilitar a Venusaur". Ahí puedes decidir tú.

## Log 22: derrota contra Ranger Talia (#10)

En el turno 1, Somnífero falló contra Charizard y Onda Ígnea debilitó a Venusaur. Después, su Venusaur con
Clorofila (más rápido que todo tu equipo con sol) durmió a un Pokémon por turno, y su Hitmontop con Vastaguardia
bloqueó tus ataques de área. Contra esa combinación tu equipo lo tiene muy difícil, pero la derrota empezó por
el fallo de Somnífero, y no por pura mala suerte:

- Es la **tercera derrota en los últimos logs que empieza igual** (Somnífero falla y cae Venusaur). En los tres
  logs: 31 Somníferos, 9 fallos y 3 Venusaur debilitados después de fallar, los tres en combates perdidos.
- El fallo de fondo: la IA solo tenía en cuenta el "caso de mala suerte" si una rama de la simulación
  tenía al menos un 20 % de probabilidad. Un fallo del 25 % que después se dividía por la tirada de daño (en
  ramas de 7 % y 18 %) quedaba invisible.
- Ahora las ramas se agrupan según qué Pokémon tuyos caen antes de aplicar el umbral, así que "25 % de perder a
  Venusaur" cuenta entero. Esto vale para cualquier movimiento impreciso, no solo para Somnífero.
- Resultado: contra Talia (turno 1) ahora elige Protección + Estallido en vez de Somnífero. En la repetición de
  Cira (#4, turno 2) Somnífero sigue ganando por poco, pero el panel avisa del riesgo.

## Log 23: derrota contra Experto Cirilo (#26)

Es el mismo rival que en el log 21 y el combate se torció en el mismo punto. Turno 1 bien: Estallido tumbó a
Tornadus, pero Tornadus ya había puesto Viento Afín (Bromista). Con viento a favor, Latios y Metagross son más
rápidos que todo tu equipo y los dos tienen ataques Psíquicos contra Venusaur. Lo que pasó:

- **Turno 2:** Charizard entró en lugar de Venusaur y cayó por Psicocolmillo más Poder Solar. Para la IA era casi
  un empate con Garchomp (diferencia de 0.02): los dos estaban al alcance de Cometa Draco de Latios, y el modelo daba
  un 28 % de que Charizard cayera. Salió la tirada alta. No es un fallo claro.
- **Turno 3:** Latios usó Cometa Draco (con Gema Dragón) sobre Garchomp en vez de repetir Psíquico contra Torkoal
  (la IA daba un 89 % a esto último). En los cuatro logs, un rival con su objetivo anterior todavía en el campo
  vuelve a atacarlo un 73 % de las veces, así que esto era el 27 % restante.
- **Turno 4:** Venusaur atacó y Metagross lo debilitó antes con Psicocolmillo, cuando un Protección lo habría
  salvado. Medido en los logs: cuando acabas de meter un Pokémon nuevo al lado del objetivo anterior, el rival
  cambia de objetivo un 40 % de las veces (frente al 27 % normal).

Cambio general: la costumbre de "repetir objetivo" del modelo del rival cuenta la mitad cuando hay un Pokémon
recién entrado a su lado. En una repetición del turno 4 la IA elige Protección con Venusaur y Onda Ígnea con
Torkoal. Probé también a hacer el modelo del rival menos predecible en general, pero rompía una repetición
anterior, así que lo descarté.

Este rival es un mal emparejamiento para un equipo de sol: Viento Afín con Bromista, un Latios que resiste el
fuego y dos atacantes Psíquicos contra Venusaur.

## Log 24: derrota contra Barón Evaristo (#33, Kyogre Primigenio)

Pregunta: ¿por qué no priorizó debilitar a Kyogre para recuperar el sol? Sí lo intentó: en 4 de los 5 turnos
atacó a Kyogre (Gigadrenado en T1 y T3, Onda Certera en T4, Tierra Viva en T5). No llegó a tiempo porque, tras el
Viento Hielo de Cresselia (Venusaur a -1 de Velocidad) y el Viento Afín de Crobat, Kyogre se movía antes que todo
tu equipo. En el turno 2 nadie podía debilitarlo antes de que atacara. Pero la revisión destapó tres fallos reales:

1. **Sol imposible durante la lluvia primigenia.** La IA daba por hecho que la Megaevolución de Charizard pondría sol
   con Sequía, pero Mar del Albor bloquea cualquier otro clima mientras Kyogre esté en el campo. Por eso valoraba
   un sol que no podía llegar, veía la Megaevolución como una pérdida y no le daba tanta urgencia a debilitar a Kyogre.
   Ahora, con clima primigenio activo, no cuenta con ese sol. Si Kyogre cae, la simulación quita la lluvia y la
   Megaevolución o el regreso de Torkoal vuelven a valer sol.
2. **No esperaba Pulso Primigenio.** Salpicar contaba como "su ataque fuerte de tipo Agua", así que no se
   imaginaba otro. Pero Salpicar pierde fuerza con los PS (con Kyogre al 38 % es flojo), y Kyogre usó Pulso
   Primigenio, que debilitó a Garchomp al entrar. Ahora Salpicar, Estallido y Energía Dragón no cuentan como el STAB
   fijo, y se añade un STAB probable.
3. **Peligro al entrar contra un rival más lento.** El Pokémon que entra recibe el golpe de ese turno, sea el
   rival más rápido o no. Ahora el STAB probable de un rival más lento también cuenta como peligro (la cobertura
   adivinada sigue sin contar, porque casi nunca aparece).

Además, la simulación reconoce a un Kyogre o Groudon como fuente de clima primigenio aunque no se haya leído su
habilidad. Antes, en ese caso, la lluvia "se acababa" sola cada turno y debilitar a Kyogre no parecía valer nada.
