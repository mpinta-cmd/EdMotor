
package com.example.edmotor.data

import com.example.edmotor.model.DashboardLight
import com.example.edmotor.model.Symptom

// ======================================================
// 1. TESTIGOS E INDICADORES DEL TABLERO
// ======================================================

val dashboardLightList = listOf(

    // ==============================================
    // SISTEMA DE LUBRICACIÓN
    // ==============================================

    DashboardLight(
        id = 1,
        icon = "🛢️",
        name = "Presión de Aceite",
        severity = "Rojo 🔴",
        meaning = "La presión del aceite del motor ha descendido por debajo del nivel seguro. Esto puede impedir que las piezas internas reciban la lubricación necesaria y provocar daños graves.",
        recommendedAction = "DETÉN EL VEHÍCULO DE FORMA SEGURA Y APAGA EL MOTOR. No continúes conduciendo. Verifica el nivel de aceite cuando sea seguro y solicita asistencia si el testigo permanece encendido.",
        relatedCategory = "Aceites y Lubricantes"
    ),

    DashboardLight(
        id = 2,
        icon = "🔧",
        name = "Cambio de Aceite",
        severity = "Amarillo 🟡",
        meaning = "El sistema de mantenimiento indica que se aproxima o se ha alcanzado el intervalo programado para cambiar el aceite y revisar el filtro.",
        recommendedAction = "Programa el mantenimiento correspondiente. Comprueba el nivel y utiliza el aceite con la viscosidad y especificaciones recomendadas por el fabricante.",
        relatedCategory = "Aceites y Lubricantes"
    ),

    // ==============================================
    // SISTEMA DE REFRIGERACIÓN
    // ==============================================

    DashboardLight(
        id = 3,
        icon = "🌡️",
        name = "Temperatura del Motor",
        severity = "Rojo 🔴",
        meaning = "La temperatura del refrigerante ha superado el límite normal de funcionamiento. Puede existir una falla en el sistema de enfriamiento.",
        recommendedAction = "Detente en un lugar seguro y apaga el motor. Espera a que se enfríe completamente. NUNCA abras la tapa del radiador o depósito mientras esté caliente. Solicita una revisión.",
        relatedCategory = "Refrigeración"
    ),

    DashboardLight(
        id = 4,
        icon = "💧",
        name = "Nivel de Refrigerante",
        severity = "Amarillo 🟡",
        meaning = "El nivel del líquido refrigerante puede encontrarse por debajo del rango recomendado. Una fuga o pérdida de líquido podría afectar la refrigeración del motor.",
        recommendedAction = "Revisa el depósito de expansión cuando el motor esté completamente frío. Si existe una fuga o el nivel vuelve a descender, solicita una revisión del circuito.",
        relatedCategory = "Refrigeración"
    ),

    // ==============================================
    // SISTEMA DE MOTOR Y ENCENDIDO
    // ==============================================

    DashboardLight(
        id = 5,
        icon = "💻",
        name = "Check Engine",
        severity = "Amarillo 🟡",
        meaning = "La computadora del vehículo ha registrado una anomalía relacionada con el motor, la combustión, los sensores o el sistema de emisiones.",
        recommendedAction = "Realiza un diagnóstico con escáner OBD-II para identificar los códigos de falla. Si el testigo parpadea, el motor funciona irregularmente o pierde potencia, detente y solicita asistencia.",
        relatedCategory = "Motor y Transmisión"
    ),

    DashboardLight(
        id = 6,
        icon = "⚡",
        name = "Sistema de Encendido",
        severity = "Amarillo 🟡",
        meaning = "Puede existir una anomalía en el funcionamiento del sistema de encendido, especialmente cuando se presentan fallos de combustión detectados por la computadora.",
        recommendedAction = "Solicita una revisión de bujías, bobinas, cables de encendido y posibles códigos de falla. No sustituyas componentes sin realizar primero un diagnóstico.",
        relatedCategory = "Bujias"
    ),

    DashboardLight(
        id = 7,
        icon = "🌀",
        name = "Sistema de Precalentamiento",
        severity = "Amarillo 🟡",
        meaning = "En motores diésel, este indicador informa sobre el funcionamiento de las bujías de precalentamiento. Si permanece encendido o parpadea durante la marcha, podría existir una anomalía.",
        recommendedAction = "En condiciones normales, espera a que el indicador se apague antes de arrancar. Si permanece encendido o parpadea durante la conducción, solicita un diagnóstico.",
        relatedCategory = "Motor y Transmisión"
    ),

    // ==============================================
    // SISTEMA DE FRENOS
    // ==============================================

    DashboardLight(
        id = 8,
        icon = "🛑",
        name = "Sistema de Frenos",
        severity = "Rojo 🔴",
        meaning = "Puede indicar que el freno de estacionamiento está activado, que existe un nivel insuficiente de líquido de frenos o una anomalía en el sistema hidráulico.",
        recommendedAction = "Comprueba que el freno de estacionamiento esté completamente liberado. Si el testigo continúa encendido, detente y no sigas conduciendo hasta revisar el sistema.",
        relatedCategory = "Frenos"
    ),

    DashboardLight(
        id = 9,
        icon = "⚙️",
        name = "Sistema ABS",
        severity = "Amarillo 🟡",
        meaning = "El sistema antibloqueo de frenos ha detectado una anomalía. El frenado convencional podría continuar funcionando, pero la función ABS puede estar desactivada.",
        recommendedAction = "Solicita una revisión del sistema ABS, sensores de velocidad de las ruedas, cableado y módulo de control. Conduce con precaución y detente si notas problemas de frenado.",
        relatedCategory = "Frenos"
    ),

    DashboardLight(
        id = 10,
        icon = "🔴",
        name = "Desgaste de Pastillas",
        severity = "Amarillo 🟡",
        meaning = "En vehículos equipados con este indicador, un sensor puede estar informando que las pastillas de freno han alcanzado su límite de desgaste.",
        recommendedAction = "Realiza una inspección de pastillas, discos y sensores de desgaste. Sustituye los componentes que estén fuera de especificación.",
        relatedCategory = "Frenos"
    ),

    // ==============================================
    // SISTEMA ELÉCTRICO
    // ==============================================

    DashboardLight(
        id = 11,
        icon = "🔋",
        name = "Batería / Alternador",
        severity = "Rojo 🔴",
        meaning = "El sistema de carga no está suministrando correctamente energía a la batería. Puede existir una falla en el alternador, regulador, correa o conexiones eléctricas.",
        recommendedAction = "Detente en un lugar seguro y solicita una revisión del sistema de carga. Evita apagar y encender repetidamente el vehículo, ya que podría quedarse sin energía para arrancar.",
        relatedCategory = "Baterías e Iluminación"
    ),

    DashboardLight(
        id = 12,
        icon = "💡",
        name = "Falla de Iluminación",
        severity = "Amarillo 🟡",
        meaning = "El sistema eléctrico ha detectado una anomalía en alguna lámpara exterior o circuito de iluminación del vehículo.",
        recommendedAction = "Comprueba las luces delanteras, traseras, direccionales, freno y posición. Revisa bombillos, fusibles, conectores y cableado.",
        relatedCategory = "Baterías e Iluminación"
    ),

    // ==============================================
    // NEUMÁTICOS Y ESTABILIDAD
    // ==============================================

    DashboardLight(
        id = 13,
        icon = "🛞",
        name = "Presión de Neumáticos TPMS",
        severity = "Amarillo 🟡",
        meaning = "Uno o varios neumáticos pueden tener una presión inferior a la recomendada. También puede existir una anomalía en los sensores de presión.",
        recommendedAction = "Comprueba la presión de las cuatro llantas en frío y ajústala según la etiqueta del vehículo. Si alguna pierde aire continuamente, revisa posibles perforaciones.",
        relatedCategory = "Suspensión y Dirección"
    ),

    DashboardLight(
        id = 14,
        icon = "🚗",
        name = "Control de Tracción",
        severity = "Amarillo 🟡",
        meaning = "El sistema de control de tracción está interviniendo para reducir el deslizamiento de las ruedas. Si permanece encendido, puede indicar que el sistema está desactivado o presenta una falla.",
        recommendedAction = "Si parpadea momentáneamente durante una pérdida de adherencia, puede ser normal. Si permanece encendido, solicita una revisión del sistema de tracción y sus sensores.",
        relatedCategory = "Suspensión y Dirección"
    ),

    DashboardLight(
        id = 15,
        icon = "⚠️",
        name = "Control de Estabilidad ESP",
        severity = "Amarillo 🟡",
        meaning = "El sistema electrónico de estabilidad está interviniendo o ha detectado una anomalía que podría afectar su funcionamiento.",
        recommendedAction = "Reduce la velocidad y evita maniobras bruscas. Si el indicador permanece encendido, solicita una revisión del sistema ESP, sensores y conexiones.",
        relatedCategory = "Suspensión y Dirección"
    ),

    // ==============================================
    // DIRECCIÓN ASISTIDA
    // ==============================================

    DashboardLight(
        id = 16,
        icon = "🛞",
        name = "Dirección Asistida EPS",
        severity = "Amarillo 🟡",
        meaning = "El sistema de dirección asistida eléctricamente ha detectado una anomalía. El volante podría requerir mayor esfuerzo para girar.",
        recommendedAction = "Reduce la velocidad y busca un lugar seguro. Si la dirección se vuelve difícil de controlar, detente y solicita asistencia. Realiza una revisión del sistema EPS.",
        relatedCategory = "Suspensión y Dirección"
    ),

    // ==============================================
    // TRANSMISIÓN
    // ==============================================

    DashboardLight(
        id = 17,
        icon = "⚙️",
        name = "Temperatura de Transmisión",
        severity = "Rojo 🔴",
        meaning = "La temperatura del fluido de transmisión ha superado el rango recomendado. Puede producirse un desgaste acelerado o daños en los componentes internos.",
        recommendedAction = "Detente en un lugar seguro y sigue las indicaciones del fabricante para enfriar la transmisión. No continúes conduciendo si el aviso persiste.",
        relatedCategory = "Motor y Transmisión"
    ),

    // ==============================================
    // SEGURIDAD
    // ==============================================

    DashboardLight(
        id = 18,
        icon = "🛡️",
        name = "Sistema de Airbag SRS",
        severity = "Amarillo 🟡",
        meaning = "El sistema de airbags o pretensores de los cinturones ha detectado una anomalía. En caso de accidente, los dispositivos de seguridad podrían no funcionar correctamente.",
        recommendedAction = "Solicita un diagnóstico del sistema SRS. No manipules conectores, airbags ni componentes pirotécnicos sin personal capacitado.",
        relatedCategory = "Servicios"
    ),

    DashboardLight(
        id = 19,
        icon = "🚪",
        name = "Puerta Abierta",
        severity = "Rojo 🔴",
        meaning = "Una o varias puertas, el capó o el baúl pueden no estar completamente cerrados, dependiendo del sistema de monitoreo del vehículo.",
        recommendedAction = "Detén el vehículo cuando sea seguro y verifica que todas las puertas, el capó y el baúl estén correctamente cerrados antes de continuar.",
        relatedCategory = "Servicios"
    ),

    DashboardLight(
        id = 20,
        icon = "💺",
        name = "Cinturón de Seguridad",
        severity = "Rojo 🔴",
        meaning = "El sistema detecta que el conductor o alguno de los pasajeros no tiene abrochado correctamente el cinturón de seguridad.",
        recommendedAction = "Abrocha correctamente los cinturones de todos los ocupantes antes de iniciar la marcha. No transportes pasajeros sin el cinturón asegurado.",
        relatedCategory = "Servicios"
    ),

    // ==============================================
    // SISTEMA DE EMISIONES
    // ==============================================

    DashboardLight(
        id = 21,
        icon = "🌫️",
        name = "Filtro de Partículas DPF",
        severity = "Amarillo 🟡",
        meaning = "En vehículos diésel equipados con filtro de partículas, el indicador puede señalar acumulación de hollín y la necesidad de realizar una regeneración.",
        recommendedAction = "Consulta el manual del vehículo y sigue el procedimiento de regeneración indicado por el fabricante. Si el testigo parpadea o aparece acompañado de otras alertas, acude a un taller.",
        relatedCategory = "Motor y Transmisión"
    ),

    DashboardLight(
        id = 22,
        icon = "⛽",
        name = "Reserva de Combustible",
        severity = "Amarillo 🟡",
        meaning = "El nivel de combustible se encuentra cerca del mínimo disponible en el depósito.",
        recommendedAction = "Abastece combustible lo antes posible. Evita conducir habitualmente con el depósito en reserva para reducir el riesgo de quedarte detenido.",
        relatedCategory = "Motor y Transmisión"
    )

)


// ======================================================
// 2. DIAGNÓSTICO DE SÍNTOMAS DEL VEHÍCULO
// ======================================================

val symptomList = listOf(

    // ==============================================
    // MOTOR Y ENCENDIDO
    // ==============================================

    Symptom(
        id = 1,
        title = "El motor tiembla al acelerar",
        emoji = "📳",
        probableCause = "Posibles fallas de encendido, bujías desgastadas, bobinas defectuosas, inyectores obstruidos o problemas en la alimentación de combustible.",
        solution = "Realiza un escaneo OBD-II y revisa bujías, bobinas, inyectores y presión de combustible. Sustituye únicamente los componentes que presenten fallas."
    ),

    Symptom(
        id = 2,
        title = "El motor tarda en encender",
        emoji = "🔑",
        probableCause = "Batería con carga insuficiente, bujías deterioradas, baja presión de combustible o problemas en el sistema de arranque.",
        solution = "Comprueba el voltaje de la batería, el estado del motor de arranque y la presión de combustible. Revisa el sistema de encendido si el problema continúa."
    ),

    Symptom(
        id = 3,
        title = "El vehículo no enciende",
        emoji = "🚫",
        probableCause = "Puede existir una batería descargada, terminales sulfatados, motor de arranque defectuoso, falla del sistema de combustible o del inmovilizador.",
        solution = "Verifica la batería y sus conexiones. Determina si el motor gira al intentar arrancar. Si gira pero no enciende, realiza un diagnóstico del encendido y alimentación de combustible."
    ),

    Symptom(
        id = 4,
        title = "El motor pierde potencia",
        emoji = "📉",
        probableCause = "Filtro de aire obstruido, problemas de alimentación de combustible, fallas de encendido, sensores defectuosos o restricciones en el escape.",
        solution = "Inspecciona el filtro de aire, sistema de combustible y sensores. Realiza un escaneo OBD-II para identificar posibles códigos de falla."
    ),

    Symptom(
        id = 5,
        title = "El motor se apaga inesperadamente",
        emoji = "⚠️",
        probableCause = "Posibles fallas en sensores del cigüeñal, alimentación eléctrica, bomba de combustible, sistema de encendido o control electrónico.",
        solution = "Evita continuar conduciendo si el vehículo se apaga repetidamente. Solicita un diagnóstico de sensores, presión de combustible y conexiones eléctricas."
    ),

    Symptom(
        id = 6,
        title = "El motor vibra cuando está detenido",
        emoji = "〰️",
        probableCause = "Soportes de motor deteriorados, combustión irregular, inyectores sucios o entrada de aire no medida en la admisión.",
        solution = "Realiza una revisión de los soportes, sistema de encendido, inyectores y admisión. Comprueba si la vibración cambia al acelerar."
    ),

    Symptom(
        id = 7,
        title = "Aumentó el consumo de combustible",
        emoji = "⛽",
        probableCause = "Filtro de aire obstruido, presión incorrecta de neumáticos, sensores defectuosos, inyectores con problemas o fallas de combustión.",
        solution = "Verifica la presión de las llantas, el filtro de aire y los parámetros del motor con escáner. Compara el consumo real con el recomendado para el vehículo."
    ),

    Symptom(
        id = 8,
        title = "Se escucha un golpeteo en el motor",
        emoji = "🔊",
        probableCause = "Puede relacionarse con detonación, lubricación insuficiente, desgaste de componentes internos o elementos del sistema de distribución.",
        solution = "Detén el motor si el ruido es fuerte o aparece acompañado de baja presión de aceite. Comprueba el nivel de lubricante y solicita una inspección mecánica."
    ),

    // ==============================================
    // HUMO Y ESCAPE
    // ==============================================

    Symptom(
        id = 9,
        title = "Sale humo blanco por el escape",
        emoji = "💨",
        probableCause = "Puede ser vapor de condensación durante el arranque en frío. Si es abundante y persistente, podría existir ingreso de refrigerante a las cámaras de combustión.",
        solution = "Comprueba el nivel de refrigerante cuando el motor esté frío y observa si hay pérdida de líquido o sobrecalentamiento. Solicita una prueba del sistema de refrigeración y de la culata."
    ),

    Symptom(
        id = 10,
        title = "Sale humo azul por el escape",
        emoji = "🌫️",
        probableCause = "Puede indicar que el motor está quemando aceite debido al desgaste de anillos, sellos de válvulas o componentes del sistema de sobrealimentación.",
        solution = "Verifica el consumo y nivel de aceite. Solicita una prueba de compresión y una inspección del motor para identificar el origen de la fuga de lubricante."
    ),

    Symptom(
        id = 11,
        title = "Sale humo negro por el escape",
        emoji = "⬛",
        probableCause = "Puede existir una mezcla de combustible demasiado rica, inyectores con fugas, filtro de aire obstruido o problemas en los sensores de admisión.",
        solution = "Revisa el filtro de aire, inyectores y sensores. Realiza un diagnóstico de la mezcla de combustible mediante escáner."
    ),

    // ==============================================
    // SISTEMA DE REFRIGERACIÓN
    // ==============================================

    Symptom(
        id = 12,
        title = "El motor se sobrecalienta",
        emoji = "🌡️",
        probableCause = "Nivel bajo de refrigerante, fuga en mangueras, termostato defectuoso, radiador obstruido, ventilador averiado o falla de la bomba de agua.",
        solution = "Detente y apaga el motor. Espera a que se enfríe completamente. Solicita una revisión del radiador, ventilador, termostato, bomba y circuito de refrigeración. No abras el radiador caliente."
    ),

    Symptom(
        id = 13,
        title = "El refrigerante desaparece constantemente",
        emoji = "💧",
        probableCause = "Puede existir una fuga en mangueras, radiador, depósito, bomba de agua o una filtración interna hacia el motor.",
        solution = "Realiza una prueba de presión del sistema de refrigeración e inspecciona conexiones y componentes. Si no hay fugas externas, solicita una revisión de la culata."
    ),

    // ==============================================
    // FRENOS
    // ==============================================

    Symptom(
        id = 14,
        title = "Chillido agudo al frenar",
        emoji = "🔊",
        probableCause = "Pastillas de freno desgastadas, cristalizadas, contaminadas o vibraciones entre las pastillas y el conjunto de freno.",
        solution = "Inspecciona pastillas, discos y herrajes. Sustituye los elementos desgastados y verifica el estado de los discos según las especificaciones del fabricante."
    ),

    Symptom(
        id = 15,
        title = "El pedal de freno se siente esponjoso",
        emoji = "🛑",
        probableCause = "Posible presencia de aire en el circuito hidráulico, fuga de líquido de frenos o deterioro de componentes del sistema.",
        solution = "No continúes conduciendo. Solicita una inspección inmediata del circuito hidráulico, nivel de líquido, cilindro maestro y conexiones. El sistema debe repararse y purgarse correctamente."
    ),

    Symptom(
        id = 16,
        title = "El volante vibra al frenar",
        emoji = "🛞",
        probableCause = "Puede existir variación de espesor en los discos de freno, desgaste irregular, problemas de montaje o componentes de suspensión deteriorados.",
        solution = "Solicita una inspección de discos, pastillas, rodamientos y suspensión delantera. Comprueba las tolerancias antes de rectificar o reemplazar piezas."
    ),

    // ==============================================
    // SUSPENSIÓN Y DIRECCIÓN
    // ==============================================

    Symptom(
        id = 17,
        title = "Golpes al pasar por baches",
        emoji = "🔨",
        probableCause = "Amortiguadores desgastados, bujes deteriorados, rótulas con holgura, terminales de dirección o soportes de suspensión defectuosos.",
        solution = "Realiza una inspección del tren delantero y trasero. Comprueba amortiguadores, bujes, rótulas y terminales. Sustituye los elementos que presenten desgaste."
    ),

    Symptom(
        id = 18,
        title = "El vehículo se desvía hacia un lado",
        emoji = "↔️",
        probableCause = "Presión desigual en neumáticos, alineación incorrecta, desgaste de suspensión o problemas en los frenos.",
        solution = "Comprueba la presión de las llantas y solicita una revisión de alineación, suspensión y frenos para identificar la causa de la desviación."
    ),

    Symptom(
        id = 19,
        title = "El volante está demasiado duro",
        emoji = "🛞",
        probableCause = "Falla en la dirección asistida, bajo nivel de fluido en sistemas hidráulicos, problemas eléctricos en EPS o presión insuficiente de neumáticos.",
        solution = "Comprueba la presión de las llantas y revisa el sistema de dirección asistida. Si el volante pierde asistencia repentinamente, detente y solicita asistencia."
    ),

    Symptom(
        id = 20,
        title = "Zumbido que aumenta con la velocidad",
        emoji = "🔉",
        probableCause = "Puede relacionarse con rodamientos de rueda desgastados, neumáticos deformados o desgaste irregular de la banda de rodadura.",
        solution = "Solicita una inspección de rodamientos, llantas y suspensión. No ignores los ruidos que aumentan progresivamente durante la marcha."
    ),

    Symptom(
        id = 21,
        title = "Desgaste irregular de las llantas",
        emoji = "🛞",
        probableCause = "Presión incorrecta, alineación desajustada, balanceo deficiente o desgaste de componentes de suspensión y dirección.",
        solution = "Verifica la presión, realiza alineación y balanceo cuando corresponda e inspecciona amortiguadores, terminales y demás componentes de suspensión."
    ),

    // ==============================================
    // TRANSMISIÓN Y EMBRAGUE
    // ==============================================

    Symptom(
        id = 22,
        title = "La caja presenta dificultad para cambiar",
        emoji = "⚙️",
        probableCause = "Puede existir bajo nivel o deterioro del fluido de transmisión, desgaste del embrague, problemas en sincronizadores o mecanismos de selección.",
        solution = "Identifica si ocurre en una transmisión manual o automática. Solicita una inspección del sistema de embrague, mecanismo de cambios y fluido según las especificaciones."
    ),

    Symptom(
        id = 23,
        title = "El embrague patina",
        emoji = "🔄",
        probableCause = "Disco de embrague desgastado o contaminado, problemas en el sistema de accionamiento o pérdida de capacidad de acoplamiento.",
        solution = "Solicita una inspección del conjunto de embrague, disco, prensa y volante. Evita exigir el motor para prevenir un mayor deterioro."
    ),

    // ==============================================
    // SISTEMA ELÉCTRICO
    // ==============================================

    Symptom(
        id = 24,
        title = "La batería se descarga constantemente",
        emoji = "🔋",
        probableCause = "Batería deteriorada, alternador con baja carga, conexiones sulfatadas o consumo eléctrico cuando el vehículo está apagado.",
        solution = "Realiza una prueba de batería y alternador. Comprueba el voltaje de carga y mide posibles consumos eléctricos parásitos."
    ),

    // ==============================================
    // SISTEMAS AUXILIARES
    // ==============================================

    Symptom(
        id = 25,
        title = "El aire acondicionado no enfría",
        emoji = "❄️",
        probableCause = "Posible nivel insuficiente de refrigerante, fuga en el circuito, compresor defectuoso, ventilador averiado o filtro de cabina obstruido.",
        solution = "Solicita una revisión de presión del sistema, fugas, compresor, ventiladores y filtro de cabina. No recargues refrigerante sin comprobar primero el estado del circuito."
    )

)
