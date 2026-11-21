package com.example.edmotor.dashboard

internal val statusDashboardIndicators: List<DashboardIndicator> = listOf(
    DashboardIndicator(
        id = "low_beam",
        title = "Luces bajas o de cruce",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "Indica que las luces bajas están encendidas.",
        causes = listOf(
            "Luces bajas seleccionadas en el mando.",
            "Encendido automático por poca luz, si el vehículo dispone de esa función."
        ),
        advice = "Usa las luces bajas cuando la visibilidad o las normas de tránsito lo requieran. Comprueba que ambas funcionen si notas una iluminación desigual.",
        marks = listOf(
            DashboardMark("M25 10 H32 C40 10 45 15 45 22 C45 29 40 34 32 34 H25 Z"),
            DashboardMark("M4 21 L19 12 M4 28 L19 19 M4 35 L19 26 M4 42 L19 33", strokeWidth = 2.8f)
        )
    ),
    DashboardIndicator(
        id = "front_fog",
        title = "Faros antiniebla delanteros",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "Indica que los faros antiniebla delanteros están encendidos.",
        causes = listOf("Activación desde el mando de luces."),
        advice = "Úsalos con visibilidad reducida y según las normas de tránsito. Apágalos cuando mejore la visibilidad.",
        marks = listOf(
            DashboardMark("M30 12 H35 C42 12 46 17 46 24 C46 31 42 36 35 36 H30 Z"),
            DashboardMark("M3 20 L24 15 M3 28 L24 23 M3 36 L24 31", strokeWidth = 2.8f),
            DashboardMark("M13 7 C8 12 8 16 13 21 C18 26 18 30 13 35 C10 38 10 41 12 44", strokeWidth = 2.8f)
        )
    ),
    DashboardIndicator(
        id = "rear_fog",
        title = "Luz antiniebla trasera",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.INFORMATION,
        summary = "Indica que la luz antiniebla trasera está encendida para que otros conductores puedan ver el vehículo.",
        causes = listOf("Activación desde el mando de luces."),
        advice = "Úsala cuando la visibilidad sea muy baja. Apágala al recuperar visibilidad para evitar deslumbrar a quienes circulan detrás.",
        marks = listOf(
            DashboardMark("M18 12 H13 C6 12 2 17 2 24 C2 31 6 36 13 36 H18 Z"),
            DashboardMark("M24 16 H45 M24 24 H45 M24 32 H45", strokeWidth = 2.8f),
            DashboardMark("M34 7 C39 12 39 16 34 21 C29 26 29 30 34 35 C37 38 37 41 35 44", strokeWidth = 2.8f)
        )
    ),
    DashboardIndicator(
        id = "turn_left",
        title = "Direccional izquierda",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "La flecha suele parpadear cuando está activa la señal de giro a la izquierda.",
        causes = listOf(
            "Direccional izquierda activada con la palanca.",
            "Luces de emergencia activadas, si también parpadea la flecha derecha."
        ),
        advice = "Comprueba el funcionamiento de las luces exteriores si el parpadeo es inusualmente rápido o la flecha queda fija.",
        marks = listOf(
            DashboardMark("M3 24 L20 9 V17 H45 V31 H20 V39 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "turn_right",
        title = "Direccional derecha",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "La flecha suele parpadear cuando está activa la señal de giro a la derecha.",
        causes = listOf(
            "Direccional derecha activada con la palanca.",
            "Luces de emergencia activadas, si también parpadea la flecha izquierda."
        ),
        advice = "Comprueba el funcionamiento de las luces exteriores si el parpadeo es inusualmente rápido o la flecha queda fija.",
        marks = listOf(
            DashboardMark("M45 24 L28 9 V17 H3 V31 H28 V39 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "cruise_control",
        title = "Control de crucero",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "Suele indicar que el control de crucero está activo. Según el modelo, el tablero también muestra la velocidad seleccionada.",
        causes = listOf("Activación del control de crucero desde el volante o la palanca."),
        advice = "Comprueba la velocidad seleccionada y mantén el control del vehículo. Desactívalo cuando el tráfico o el estado de la vía requieran variar la velocidad con frecuencia.",
        marks = listOf(
            DashboardMark("M8 34 A18 18 0 1 1 40 34"),
            DashboardMark("M24 8 V13 M10 14 L14 18 M38 14 L34 18 M6 26 H11 M37 26 H42", strokeWidth = 2.8f),
            DashboardMark("M23 27 L33 17", strokeWidth = 3.5f),
            DashboardMark("M24 24 A3 3 0 1 1 24 30 A3 3 0 1 1 24 24 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "coolant_cold",
        title = "Motor frío",
        lightColor = DashboardLightColor.BLUE,
        urgency = DashboardUrgency.INFORMATION,
        summary = "En algunos vehículos, el termómetro azul indica que el refrigerante aún está por debajo de la temperatura normal de funcionamiento.",
        causes = listOf("Arranque con el motor frío."),
        advice = "Evita aceleraciones fuertes mientras el motor alcanza su temperatura normal. Si el aviso dura más de lo habitual, consulta el manual. Si aparece el termómetro rojo, detente en un lugar seguro.",
        marks = listOf(
            DashboardMark("M19 8 Q19 5 23 5 Q27 5 27 8 V23 A7 7 0 1 1 19 23 Z"),
            DashboardMark("M23 15 V29 M32 11 H38 M32 17 H36 M32 23 H38", strokeWidth = 2.8f),
            DashboardMark("M4 39 Q9 34 14 39 T24 39 T34 39 T44 39 M4 44 Q9 39 14 44 T24 44 T34 44 T44 44", strokeWidth = 2.5f)
        )
    ),
    DashboardIndicator(
        id = "start_stop_active",
        title = "Parada y arranque automáticos (Start/Stop)",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "En vehículos equipados con Start/Stop, este símbolo puede indicar que el sistema detuvo temporalmente el motor durante una parada.",
        causes = listOf("Parada del vehículo con el sistema Start/Stop activado y las condiciones de funcionamiento cumplidas."),
        advice = "Mantén el control del vehículo mientras está detenido. Consulta el manual para saber cuándo se reinicia el motor y cómo funciona el sistema en tu modelo.",
        marks = listOf(
            DashboardMark("M34 9 A18 18 0 1 0 41 23"),
            DashboardMark("M26 8 L36 10 L35 2"),
            DashboardMark("M15 32 L23 15 L31 32 M18 26 H28", strokeWidth = 2.8f)
        )
    )
)
