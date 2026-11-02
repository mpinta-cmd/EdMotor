package com.example.edmotor.data

import com.example.edmotor.model.DashboardLight
import com.example.edmotor.model.Symptom

val dashboardLightList = listOf(
    DashboardLight(
        id = 1,
        icon = "🛢️",
        name = "Presión de Aceite",
        severity = "Rojo 🔴",
        meaning = "La presión del lubricante descendió a niveles críticos.",
        recommendedAction = "¡DETÉN EL VEHÍCULO DE INMEDIATO! Apaga el motor y verifica el nivel de aceite.",
        relatedCategory = "Aceites y Lubricantes"
    ),
    DashboardLight(
        id = 2,
        icon = "🌡️",
        name = "Temperatura del Motor",
        severity = "Rojo 🔴",
        meaning = "El motor supera la temperatura máxima segura de operación.",
        recommendedAction = "Detente en un lugar seguro, apaga el auto y espera a que el motor se enfríe.",
        relatedCategory = "Refrigeración"
    ),
    DashboardLight(
        id = 3,
        icon = "💻",
        name = "Check Engine",
        severity = "Amarillo 🟡",
        meaning = "La computadora detectó una anomalía en la combustión o sensores.",
        recommendedAction = "Conduce a baja velocidad y agenda un diagnóstico con escáner OBD-II.",
        relatedCategory = "Bujias"
    ),
    DashboardLight(
        id = 4,
        icon = "🛑",
        name = "Frenos / ABS",
        severity = "Amarillo 🟡",
        meaning = "Desgaste en pastillas o baja presión en el sistema hidráulico.",
        recommendedAction = "Revisa el nivel de líquido de frenos y la viscosidad de las pastillas.",
        relatedCategory = "Frenos"
    ),
    DashboardLight(
        id = 5,
        icon = "🔋",
        name = "Batería / Alternador",
        severity = "Rojo 🔴",
        meaning = "El alternador no está suministrando energía al sistema eléctrico.",
        recommendedAction = "Apaga el aire acondicionado y radio. Dirígete al taller antes de agotar la batería.",
        relatedCategory = "Baterías e Iluminación"
    )
)

val symptomList = listOf(
    Symptom(
        id = 1,
        title = "Chillido agudo al frenar",
        emoji = "🔊",
        probableCause = "Pastillas de freno desgastadas o cristalizadas.",
        solution = "Cambio de juego de pastillas de freno y rectificado de discos."
    ),
    Symptom(
        id = 2,
        title = "El auto tiembla al acelerar",
        emoji = "📳",
        probableCause = "Falla de encendido en bujías, cables o inyectores sucios.",
        solution = "Remplazo de bujías de encendido y limpieza de inyectores."
    ),
    Symptom(
        id = 3,
        title = "Humo o vapor en el capó",
        emoji = "💨",
        probableCause = "Fuga en manguera de refrigerante o empaque de tapa de válvulas.",
        solution = "Remplazo de empaques o manguera de refrigeración dañada."
    )
)