package com.example.edmotor.dashboard

internal val safetyDashboardIndicators: List<DashboardIndicator> = listOf(
    DashboardIndicator(
        id = "airbag",
        title = "Airbags y pretensores (SRS)",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "Si permanece encendido después del arranque, puede indicar un problema en el sistema de protección de los ocupantes.",
        causes = listOf(
            "Fallo en un airbag, pretensor o sensor del sistema.",
            "Conexión eléctrica deficiente en el sistema de retención."
        ),
        advice = "Solicita una revisión cuanto antes. El airbag o el pretensor podrían fallar en un choque. Consulta el manual antes de utilizar el vehículo.",
        marks = listOf(
            DashboardMark("M12 7 A4 4 0 1 1 12 15 A4 4 0 1 1 12 7 Z", filled = true),
            DashboardMark("M12 20 Q14 17 17 20 L20 28 H27 Q30 29 31 33 L34 41 H27 L23 34 H16 Q12 34 11 30 L9 24 Q9 21 12 20 Z", filled = true),
            DashboardMark("M5 21 L9 38 H22 M36 12 A8 8 0 1 1 36 28 A8 8 0 1 1 36 12 Z", strokeWidth = 2.8f)
        )
    ),
    DashboardIndicator(
        id = "parking_brake",
        title = "Freno de estacionamiento",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "El símbolo de la P rodeada suele indicar que el freno de estacionamiento está aplicado.",
        causes = listOf(
            "Freno de estacionamiento activado o parcialmente liberado.",
            "Aviso del sistema de freno de estacionamiento, según el vehículo."
        ),
        advice = "Comprueba el freno antes de iniciar la marcha. Si el testigo sigue encendido tras liberarlo, detente de forma segura, consulta el manual y solicita asistencia.",
        marks = listOf(
            DashboardMark("M24 9 A15 15 0 1 1 24 39 A15 15 0 1 1 24 9 Z", strokeWidth = 2.5f),
            DashboardMark("M7 11 C2 18 2 30 7 37 M41 11 C46 18 46 30 41 37", strokeWidth = 2.5f),
            DashboardMark("M17.5 32 V15 H25.5 C34 15 34 25.5 25.5 25.5 H17.5", strokeWidth = 3.4f)
        )
    ),
    DashboardIndicator(
        id = "brake_pad_wear",
        title = "Desgaste de pastillas de freno",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El círculo con trazos a los lados puede indicar que se alcanzó el límite de desgaste de las pastillas.",
        causes = listOf(
            "Pastillas próximas al límite de desgaste.",
            "Sensor de desgaste o cableado deteriorado."
        ),
        advice = "Programa una revisión de los frenos cuanto antes. Si cambia la respuesta al frenar o se enciende también el testigo rojo de frenos, detente de forma segura y solicita asistencia.",
        marks = listOf(
            DashboardMark("M24 11 A13 13 0 1 1 24 37 A13 13 0 1 1 24 11 Z", strokeWidth = 2.8f),
            DashboardMark("M10 10 L6 16 M4 21 V27 M6 32 L10 38 M38 10 L42 16 M44 21 V27 M42 32 L38 38", strokeWidth = 3f)
        )
    ),
    DashboardIndicator(
        id = "stability_control",
        title = "Control de estabilidad y tracción",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "Si parpadea, el sistema puede estar corrigiendo una pérdida de adherencia. Si queda fijo, el sistema puede estar desactivado o presentar una falla.",
        causes = listOf(
            "Pérdida momentánea de adherencia.",
            "Control de estabilidad o tracción desactivado.",
            "Fallo del sistema o de uno de sus sensores."
        ),
        advice = "Conduce con suavidad y adapta la velocidad al estado de la vía. Consulta el manual y solicita una revisión si el aviso sigue fijo.",
        marks = listOf(
            DashboardMark("M7 18 L11 13 L15 7 H33 L37 13 L41 18 V26 H7 Z M11 16 H37 M11 26 V29 M37 26 V29", strokeWidth = 2.6f),
            DashboardMark("M16 30 C16 34 9 35 9 38 C9 41 17 42 17 45 M32 30 C32 34 25 35 25 38 C25 41 33 42 33 45", strokeWidth = 2.8f)
        )
    ),
    DashboardIndicator(
        id = "key_immobilizer",
        title = "Llave o inmovilizador",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "Un símbolo de llave puede indicar que el vehículo no reconoce la llave. En ciertos modelos, un parpadeo con el vehículo apagado es normal.",
        causes = listOf(
            "Llave fuera del habitáculo o con pila baja.",
            "Problema de reconocimiento de la llave o del inmovilizador."
        ),
        advice = "Si el aviso aparece al intentar arrancar, comprueba que tienes la llave y consulta el procedimiento del manual. Si persiste o el vehículo no arranca, solicita asistencia.",
        marks = listOf(
            DashboardMark("M7 19 Q7 11 15 11 Q22 11 24 17 H42 V24 H38 V29 H32 V24 H24 Q21 30 15 30 Q7 30 7 22 Z", strokeWidth = 3.2f),
            DashboardMark("M14 18 A2.7 2.7 0 1 1 14 23.4 A2.7 2.7 0 1 1 14 18 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "door_ajar",
        title = "Puerta abierta",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "El dibujo del vehículo visto desde arriba indica que una puerta podría estar abierta o mal cerrada.",
        causes = listOf(
            "Puerta abierta o cierre incompleto.",
            "Interruptor del cierre de una puerta averiado."
        ),
        advice = "Detente en un lugar seguro antes de revisar las puertas. Comprueba que todas cierren bien antes de seguir circulando.",
        marks = listOf(
            DashboardMark("M15 9 Q24 4 33 9 L35 15 V38 Q35 43 30 43 H18 Q13 43 13 38 V15 Z", strokeWidth = 2.7f),
            DashboardMark("M14 18 Q24 15 34 18 M14 35 Q24 32 34 35 M13 22 L4 17 V26 L13 33 M35 22 L44 17 V26 L35 33", strokeWidth = 2.7f)
        )
    ),
    DashboardIndicator(
        id = "lane_departure",
        title = "Aviso de salida de carril",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El vehículo puede estar avisando de una salida de carril o de que la asistencia de carril está limitada.",
        causes = listOf(
            "Desplazamiento hacia la línea del carril.",
            "Marcas viales poco visibles o cámara del sistema obstruida.",
            "Asistencia de carril temporalmente no disponible."
        ),
        advice = "Mantén la atención en la vía y corrige la trayectoria con suavidad si es seguro. Si el testigo permanece encendido, consulta el manual del vehículo.",
        marks = listOf(
            DashboardMark("M14 6 L11 16 M9 23 L6 33 M4 39 L3 43 M33 6 L45 43", strokeWidth = 2.8f),
            DashboardMark("M19 20 L22 16 H28 L31 20 L34 31 Q34 35 30 35 H20 Q16 35 16 31 Z M19 23 H31 M20 35 V38 M30 35 V38", strokeWidth = 2.7f)
        )
    ),
    DashboardIndicator(
        id = "forward_collision_warning",
        title = "Aviso de colisión frontal",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "Puede aparecer cuando el sistema de asistencia detecta un riesgo inmediato de choque por delante.",
        causes = listOf(
            "Acercamiento rápido a un vehículo u obstáculo.",
            "Aviso inesperado por condiciones que limitan la cámara o el radar."
        ),
        advice = "Presta atención a la vía y frena según las condiciones del tránsito. Si el aviso persiste sin un peligro visible, consulta el manual y solicita una revisión.",
        marks = listOf(
            DashboardMark("M5 14 L9 8 H19 L23 14 V21 H5 Z M7 21 V24 M21 21 V24 M5 15 H23", strokeWidth = 2.5f),
            DashboardMark("M25 33 L29 27 H39 L43 33 V40 H25 Z M27 40 V43 M41 40 V43 M25 34 H43", strokeWidth = 2.5f),
            DashboardMark("M32 8 L34 14 L40 12 L37 17 L42 21 L35 21 L33 26 L31 20 L25 21 L29 16 L26 12 L31 14 Z", filled = true)
        )
    )
)
