package com.example.edmotor.dashboard

internal enum class DashboardLightColor { RED, AMBER, GREEN, BLUE }

internal enum class DashboardUrgency { STOP_SAFELY, CHECK_SOON, INFORMATION }

internal data class DashboardMark(
    val path: String,
    val filled: Boolean = false,
    val strokeWidth: Float = 3f
)

internal data class DashboardIndicator(
    val id: String,
    val title: String,
    val lightColor: DashboardLightColor,
    val urgency: DashboardUrgency,
    val summary: String,
    val causes: List<String>,
    val advice: String,
    val marks: List<DashboardMark>
)

/** Vectores de referencia originales sobre una cuadrícula de 48 × 48. */
internal val dashboardIndicators: List<DashboardIndicator> = listOf(
    DashboardIndicator(
        id = "oil_pressure",
        title = "Presión de aceite",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "Si permanece encendido con el motor en marcha, puede indicar pérdida de presión de lubricación.",
        causes = listOf(
            "Nivel de aceite bajo o pérdida de aceite.",
            "Circulación de aceite insuficiente.",
            "Fallo en el sistema de advertencia."
        ),
        advice = "Detente en un lugar seguro y apaga el motor. Consulta el manual del vehículo y solicita asistencia antes de volver a ponerlo en marcha.",
        marks = listOf(
            DashboardMark("M7 21 H31 L43 14 L45 17 L33 25 L29 34 H10 L6 29 Z"),
            DashboardMark("M10 21 V16 H23 V21"),
            DashboardMark("M42 25 C39 30 37 33 37 35 C37 38 39 40 42 40 C45 40 47 38 47 35 C47 33 45 30 42 25 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "coolant_temperature",
        title = "Temperatura del refrigerante",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "Puede indicar que el motor se está sobrecalentando.",
        causes = listOf(
            "Pérdida o nivel bajo de refrigerante.",
            "Circulación o enfriamiento insuficientes.",
            "Esfuerzo prolongado del motor con calor intenso."
        ),
        advice = "Estaciónate en un lugar seguro, apaga el motor y déjalo enfriar. Sigue el manual del vehículo y solicita asistencia si el aviso persiste. Abre el depósito solo con el motor frío.",
        marks = listOf(
            DashboardMark("M21 8 Q21 5 24 5 Q27 5 27 8 V22 C30 24 31 26 31 29 A7 7 0 0 1 17 29 C17 26 18 24 21 22 Z"),
            DashboardMark("M24 14 V28 M32 10 H37 M32 16 H37 M32 22 H37", strokeWidth = 2.5f),
            DashboardMark("M4 39 Q9 35 14 39 T24 39 T34 39 T44 39 M4 44 Q9 40 14 44 T24 44 T34 44 T44 44", strokeWidth = 2.5f)
        )
    ),
    DashboardIndicator(
        id = "battery_charging",
        title = "Sistema de carga",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "Si sigue encendido al circular, el vehículo podría estar perdiendo alimentación eléctrica.",
        causes = listOf(
            "Correa de accesorios floja o deteriorada, según el vehículo.",
            "Conexión eléctrica deficiente.",
            "Desgaste en el sistema de carga."
        ),
        advice = "Busca un lugar seguro para detenerte. Consulta el manual y solicita asistencia, porque el vehículo podría apagarse si se agota la batería.",
        marks = listOf(
            DashboardMark("M5 17 H43 V37 H5 Z"),
            DashboardMark("M10 17 V12 H17 V17 M31 17 V12 H38 V17"),
            DashboardMark("M11 27 H19 M29 27 H37 M33 23 V31")
        )
    ),
    DashboardIndicator(
        id = "brake_system",
        title = "Sistema de frenos",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "Puede indicar que el freno de estacionamiento está aplicado o que el sistema de frenos necesita atención inmediata.",
        causes = listOf(
            "Freno de estacionamiento aplicado.",
            "Nivel bajo de líquido de frenos.",
            "Fallo en el sistema de frenado."
        ),
        advice = "Si sigue encendido con el freno de estacionamiento liberado, reduce la velocidad con suavidad, detente en un lugar seguro y solicita asistencia. Consulta el manual del vehículo.",
        marks = listOf(
            DashboardMark("M24 10 A14 14 0 1 1 24 38 A14 14 0 1 1 24 10 Z"),
            DashboardMark("M7 12 C2 19 2 29 7 36 M41 12 C46 19 46 29 41 36"),
            DashboardMark("M24 16 V25"),
            DashboardMark("M24 28 A2 2 0 1 1 24 32 A2 2 0 1 1 24 28 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "check_engine",
        title = "Advertencia del motor",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El vehículo detectó una anomalía relacionada con el funcionamiento del motor o las emisiones.",
        causes = listOf(
            "Combustión irregular.",
            "Problema de mezcla o suministro de combustible.",
            "Fallo en el control de emisiones."
        ),
        advice = "Si parpadea, reduce la exigencia al motor, detente en un lugar seguro y solicita asistencia. Si queda fijo, consulta el manual y programa una revisión pronto.",
        marks = listOf(
            DashboardMark("M7 19 H13 L17 15 H31 L35 19 H38 V22 H41 V18 H45 V36 H41 V32 H38 L34 38 H15 L11 33 H7 Z"),
            DashboardMark("M17 10 H29 M23 10 V15 M3 22 V30 M3 26 H7")
        )
    ),
    DashboardIndicator(
        id = "tire_pressure",
        title = "Presión de los neumáticos",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El aviso puede señalar presión baja en uno o más neumáticos o un problema del sistema de monitoreo.",
        causes = listOf(
            "Pérdida de aire o pinchazo.",
            "Descenso de temperatura que reduce la presión.",
            "Calibración pendiente tras cambiar un neumático o una rueda."
        ),
        advice = "Revísalos en un lugar seguro. Comprueba en frío las presiones indicadas por el fabricante. Si notas un neumático desinflado o cambios en el manejo, detente y solicita asistencia.",
        marks = listOf(
            DashboardMark("M10 11 Q3 17 5 27 L10 37 H38 L43 27 Q45 17 38 11"),
            DashboardMark("M13 37 V41 M20 37 V41 M28 37 V41 M35 37 V41", strokeWidth = 2.5f),
            DashboardMark("M24 15 V25"),
            DashboardMark("M24 28 A2 2 0 1 1 24 32 A2 2 0 1 1 24 28 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "abs",
        title = "Sistema antibloqueo de frenos (ABS)",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El sistema antibloqueo podría estar inactivo. El comportamiento al frenar puede variar según el vehículo.",
        causes = listOf(
            "Fallo en el sistema antibloqueo.",
            "Alimentación eléctrica insuficiente.",
            "Lecturas inconsistentes de velocidad de las ruedas."
        ),
        advice = "Aumenta la distancia de seguridad y consulta el manual para programar una revisión. Si también se enciende el aviso rojo de frenos o cambia la respuesta del pedal, detente en un lugar seguro y solicita asistencia.",
        marks = listOf(
            DashboardMark("M24 8 A16 16 0 1 1 24 40 A16 16 0 1 1 24 8 Z", strokeWidth = 2.5f),
            DashboardMark("M6 12 C1 19 1 29 6 36 M42 12 C47 19 47 29 42 36", strokeWidth = 2.5f),
            DashboardMark(
                "M12 28 L15.5 20 L19 28 M13.5 25 H17.5 M21.5 28 V20 H23.5 Q26.5 20 26.5 22 Q26.5 24 23.5 24 H21.5 M23.5 24 Q27 24 27 26 Q27 28 23.5 28 H21.5 M35.5 21 Q34.5 20 32.5 20 Q29.5 20 29.5 22 Q29.5 23.5 32.5 24 Q35.5 24.5 35.5 26 Q35.5 28 32.5 28 Q30.5 28 29.5 27",
                strokeWidth = 1.8f
            )
        )
    ),
    DashboardIndicator(
        id = "seat_belt",
        title = "Cinturón de seguridad",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "El vehículo solicita comprobar si un ocupante lleva el cinturón correctamente abrochado.",
        causes = listOf(
            "Cinturón sin abrochar o hebilla sin encajar.",
            "Equipaje que activa el aviso en un asiento."
        ),
        advice = "Abrocha los cinturones antes de iniciar la marcha. Si ya estás circulando, detente en un lugar seguro para comprobarlos. Consulta el manual si el aviso sigue activo.",
        marks = listOf(
            DashboardMark("M16 6 A4 4 0 1 1 16 14 A4 4 0 1 1 16 6 Z", filled = true),
            DashboardMark("M14 18 Q8 21 8 29 V39 H37 M25 22 L30 27 H34 L40 39", strokeWidth = 2.8f),
            DashboardMark("M13 32 L32 12 L36 15 L17 36 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "high_beam",
        title = "Luces altas",
        lightColor = DashboardLightColor.BLUE,
        urgency = DashboardUrgency.INFORMATION,
        summary = "Indica que el haz de carretera está activado.",
        causes = listOf(
            "Luces altas activadas con el mando.",
            "Activación automática, según el equipamiento del vehículo."
        ),
        advice = "Cambia a luces bajas cuando puedas deslumbrar a otros usuarios. Consulta el manual para comprobar cómo funciona el mando en tu modelo.",
        marks = listOf(
            DashboardMark("M24 13 H31 C39 13 44 18 44 24 C44 30 39 35 31 35 H24 Z"),
            DashboardMark("M4 15 H17 M4 21 H17 M4 27 H17 M4 33 H17", strokeWidth = 2.5f)
        )
    ),
    DashboardIndicator(
        id = "position_lights",
        title = "Luces de posición",
        lightColor = DashboardLightColor.GREEN,
        urgency = DashboardUrgency.INFORMATION,
        summary = "Indica que las luces de posición están encendidas.",
        causes = listOf(
            "Selección de estas luces en el mando.",
            "Activación junto con otras luces, según el vehículo."
        ),
        advice = "Usa las luces de cruce cuando necesites iluminar la vía. Consulta el manual para comprobar los modos disponibles en tu vehículo.",
        marks = listOf(
            DashboardMark("M16 17 C12 17 9 20 9 24 C9 28 12 31 16 31 H20 V17 Z M32 17 C36 17 39 20 39 24 C39 28 36 31 32 31 H28 V17 Z", strokeWidth = 2.5f),
            DashboardMark("M2 16 L6 18 M2 24 H5 M2 32 L6 30 M46 16 L42 18 M46 24 H43 M46 32 L42 30", strokeWidth = 2.5f)
        )
    )
) + mechanicalDashboardIndicators + safetyDashboardIndicators + statusDashboardIndicators
