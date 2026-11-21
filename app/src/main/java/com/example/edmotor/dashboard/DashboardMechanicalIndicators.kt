package com.example.edmotor.dashboard

internal val mechanicalDashboardIndicators: List<DashboardIndicator> = listOf(
    DashboardIndicator(
        id = "low_fuel",
        title = "Nivel bajo de combustible",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El nivel de combustible se acerca a la reserva. La autonomía restante depende del vehículo y de las condiciones de manejo.",
        causes = listOf(
            "Poco combustible en el tanque.",
            "Lectura irregular del nivel si el aviso continúa después de abastecer."
        ),
        advice = "Abastece en la próxima estación que puedas alcanzar con seguridad. Si el aviso sigue activo con el tanque abastecido, programa una revisión.",
        marks = listOf(
            DashboardMark("M9 40 V8 Q9 6 11 6 H26 Q28 6 28 8 V40 M5 42 H32"),
            DashboardMark("M13 11 H24 V21 H13 Z", strokeWidth = 2.5f),
            DashboardMark("M28 24 H31 Q35 24 35 28 V33 Q35 38 39 38 Q43 38 43 33 V19 L35 11 M39 15 V20 L43 23")
        )
    ),
    DashboardIndicator(
        id = "glow_plug",
        title = "Precalentamiento diésel",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "La espiral puede encenderse unos segundos antes de arrancar un diésel frío. Si persiste o parpadea durante la marcha, puede señalar un fallo.",
        causes = listOf(
            "Precalentamiento normal antes del arranque.",
            "Fallo en las bujías de precalentamiento, su alimentación o la gestión del motor."
        ),
        advice = "Para arrancar, sigue el manual de tu vehículo. Si la luz queda fija o parpadea con el motor en marcha, consulta el manual y programa una revisión.",
        marks = listOf(
            DashboardMark(
                "M3 28 H8 C16 28 20 23 20 18 C20 14 17 12 14 12 C10 12 8 16 8 21 " +
                    "C8 27 13 31 20 31 H22 C31 31 35 24 35 18 C35 14 32 12 29 12 C25 12 22 16 22 21 " +
                    "C22 27 28 31 35 31 H41 L45 27"
            )
        )
    ),
    DashboardIndicator(
        id = "particulate_filter",
        title = "Filtro de partículas diésel (DPF)",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El filtro de partículas puede estar acumulando hollín y requerir una regeneración o revisión.",
        causes = listOf(
            "Trayectos cortos que interrumpen regeneraciones.",
            "Acumulación de partículas en el filtro.",
            "Fallo del sistema de regeneración o de sus sensores."
        ),
        advice = "Sigue el procedimiento indicado en el manual. Si el aviso parpadea, se combina con otras alertas o hay pérdida de potencia, solicita una revisión.",
        marks = listOf(
            DashboardMark("M3 21 H10 L15 12 H33 L39 21 H45 M3 27 H10 L15 36 H33 L39 27 H45 M16 12 V36 M32 12 V36"),
            DashboardMark("M21 18 V19 M27 18 V19 M24 23 V25 M21 29 V30 M27 29 V30", strokeWidth = 2.5f)
        )
    ),
    DashboardIndicator(
        id = "water_in_fuel",
        title = "Agua en el filtro de combustible",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "En vehículos diésel, el separador del filtro de combustible puede haber detectado agua acumulada.",
        causes = listOf(
            "Agua o contaminación en el combustible.",
            "Separador que requiere mantenimiento.",
            "Fallo en el sensor de agua del filtro."
        ),
        advice = "Consulta el manual y pide una revisión pronta. Si el motor pierde potencia o funciona de forma irregular, detente en un lugar seguro y solicita asistencia.",
        marks = listOf(
            DashboardMark("M11 10 H37 M15 10 V25 L19 30 H29 L33 25 V10 M19 13 V16 M24 13 V16 M29 13 V16"),
            DashboardMark("M16 23 Q20 19 24 23 Q28 27 32 23", strokeWidth = 2.4f),
            DashboardMark("M24 33 C21 37 20 39 20 41 A4 4 0 0 0 28 41 C28 39 27 37 24 33 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "automatic_transmission",
        title = "Advertencia de la transmisión automática",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El vehículo ha detectado una anomalía en la transmisión automática. El significado exacto del engranaje con signo de exclamación depende del fabricante.",
        causes = listOf(
            "Calentamiento de la transmisión.",
            "Fallo en el control de los cambios.",
            "Problema eléctrico o de lubricación de la transmisión."
        ),
        advice = "Consulta el manual y programa una revisión. Si hay tirones fuertes, pérdida de tracción o una alerta roja, detente en un lugar seguro y solicita asistencia.",
        marks = listOf(
            DashboardMark(
                "M20 4 H28 L29 10 L33 12 L39 9 L44 14 L41 20 L43 24 L46 25 V31 L40 32 L37 36 " +
                    "L38 41 L32 44 L28 40 H20 L16 44 L10 41 L11 36 L8 32 L2 31 V25 L5 24 L7 20 L4 14 L9 9 L15 12 L19 10 Z",
                strokeWidth = 2.5f
            ),
            DashboardMark("M24 16 V25", strokeWidth = 3.3f),
            DashboardMark("M24 28 A2 2 0 1 1 24 32 A2 2 0 1 1 24 28 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "power_steering",
        title = "Dirección asistida",
        lightColor = DashboardLightColor.RED,
        urgency = DashboardUrgency.STOP_SAFELY,
        summary = "El testigo rojo puede indicar pérdida de asistencia en la dirección. El volante puede volverse más difícil de girar.",
        causes = listOf(
            "Fallo o desconexión de la asistencia eléctrica o hidráulica, según el vehículo.",
            "Problema eléctrico o de alimentación."
        ),
        advice = "Detente en un lugar seguro y consulta el manual. Si la luz persiste o notas un cambio en la dirección, solicita asistencia antes de continuar.",
        marks = listOf(
            DashboardMark("M19 8 A16 16 0 1 1 19 40 A16 16 0 1 1 19 8 Z"),
            DashboardMark("M4 21 L15 24 L19 30 L23 24 L34 21 M19 30 V39", strokeWidth = 3f),
            DashboardMark("M42 14 V27", strokeWidth = 3.3f),
            DashboardMark("M42 31 A2.1 2.1 0 1 1 42 35.2 A2.1 2.1 0 1 1 42 31 Z", filled = true)
        )
    ),
    DashboardIndicator(
        id = "oil_level",
        title = "Nivel de aceite del motor",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "La aceitera ámbar con una línea ondulada puede indicar nivel bajo de aceite o un fallo en su medición.",
        causes = listOf(
            "Pérdida o consumo de aceite.",
            "Lectura errónea del sensor de nivel."
        ),
        advice = "Comprueba el nivel en un lugar seguro según el manual y usa el aceite especificado. Si también se enciende la advertencia roja de presión, detente y apaga el motor.",
        marks = listOf(
            DashboardMark("M10 17 H28 L42 12 L44 15 L31 23 L27 30 H11 Z M10 19 H5 V25 L11 28 M15 17 V12 H23 V17"),
            DashboardMark("M5 37 Q9.75 32 14.5 37 T24 37 T33.5 37 T43 37", strokeWidth = 2.8f)
        )
    ),
    DashboardIndicator(
        id = "washer_fluid",
        title = "Líquido del limpiaparabrisas",
        lightColor = DashboardLightColor.AMBER,
        urgency = DashboardUrgency.CHECK_SOON,
        summary = "El depósito de líquido limpiaparabrisas puede tener un nivel bajo.",
        causes = listOf(
            "Consumo normal del líquido.",
            "Fuga del depósito o fallo de su sensor si el aviso persiste."
        ),
        advice = "Rellena el depósito con el producto recomendado en el manual. Si la visibilidad se reduce, detente en un lugar seguro para limpiar el parabrisas.",
        marks = listOf(
            DashboardMark("M4 25 L11 40 Q24 35 37 40 L44 25 Q24 20 4 25 Z"),
            DashboardMark("M17 20 L12 14 M24 18 V8 M31 20 L36 14", strokeWidth = 2.8f),
            DashboardMark("M10 9 L8 7 M38 9 L40 7", strokeWidth = 2.5f)
        )
    )
)
