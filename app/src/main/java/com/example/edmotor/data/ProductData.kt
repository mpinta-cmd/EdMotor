package com.example.edmotor.data

import com.example.edmotor.R
import com.example.edmotor.model.Product


val productList = listOf(
    // --- PRODUCTOS INICIALES ---
    Product(
        id = 1,
        name = "Aceite Mobil 20W-50",
        description = "Aceite lubricante multigrado de alta viscosidad diseñado para motores",
        price = "$203.900",
        imageRes = R.drawable.mobil_20_w_50,
        contentDescription = "Aceite mobil 20 w 50",
        rating = 4.5,
        category = "Aceites y Lubricantes"
    ),
    Product(
        id = 2,
        name = "Aceite Havoline 20w-50",
        description = "Aceite lubricante multigrado mineral de alta viscosidad diseñado para motores a gasolina",
        price = "$87.200",
        imageRes = R.drawable.havoline_20_w_50,
        contentDescription = "Aceite havoline 20 w 50",
        rating = 4.6,
        category = "Aceites y Lubricantes"
    ),
    Product(
        id = 3,
        name = "Filtro de aceite A-111",
        description = "Diseñado para retener impurezas y partículas en el lubricante",
        price = "$20.140",
        imageRes = R.drawable.filtro_a,
        contentDescription = "Filtro de aceite A 111",
        rating = 4.7,
        badge = "Agotado",
        category = "Filtros"
    ),
    Product(
        id = 4,
        name = "Filtro de aire Aveo",
        description = "Encargado de retener el polvo e impurezas del aire externo antes de que ingresen al motor.",
        price = "$20.000",
        imageRes = R.drawable.filtro_de_aire_aveo,
        contentDescription = "Filtro de aire para aveo",
        rating = 4.6,
        badge = "Nuevo",
        category = "Filtros"
    ),
    Product(
        id = 5,
        name = "Bujias NGK x 4",
        description = "Componentes del sistema de encendido diseñados para generar la chispa eléctrica",
        price = "$14.000",
        imageRes = R.drawable.bujias_ngk,
        contentDescription = "Bujías NGK 4 unidades",
        rating = 5.0,
        badge = "Oferta",
        category = "Bujias"
    ),

    // Categoría: Aceites y Lubricantes
    Product(
        id = 6,
        name = "Aceite Shell Helix 20W-50",
        description = "Aceite multigrado para protección confiable y limpieza del motor",
        price = "$115.000",
        imageRes = R.drawable.shell_helix_20_w_50,
        contentDescription = "Aceite Shell Helix 20W-50",
        rating = 4.8,
        badge = "Oferta",
        category = "Aceites y Lubricantes"
    ),

    // Categoría: Filtros
    Product(
        id = 7,
        name = "Filtro de aceite A-18",
        description = "Filtro de aceite de alto rendimiento para retención de partículas",
        price = "$22.500",
        imageRes = R.drawable.filtro_a_18,
        contentDescription = "Filtro de aceite A-18",
        rating = 4.6,
        category = "Filtros"
    ),
    Product(
        id = 8,
        name = "Filtro de aceite A-223",
        description = "Filtro metálico blindado para protección de sistemas de lubricación",
        price = "$24.000",
        imageRes = R.drawable.filtro_a_223,
        contentDescription = "Filtro de aceite A-223",
        rating = 4.5,
        category = "Filtros"
    ),
    Product(
        id = 9,
        name = "Filtro de aceite A-14476",
        description = "Filtro especial con válvula antiretorno para motores compactos",
        price = "$26.000",
        imageRes = R.drawable.filtro_a_14476,
        contentDescription = "Filtro de aceite A-14476",
        rating = 4.7,
        category = "Filtros"
    ),
    Product(
        id = 10,
        name = "Filtro de aire Corsa",
        description = "Filtro de aire de alta filtración para Chevrolet Corsa",
        price = "$22.000",
        imageRes = R.drawable.filtro_de_aire_corsa,
        contentDescription = "Filtro de aire Chevrolet Corsa",
        rating = 4.5,
        category = "Filtros"
    ),
    Product(
        id = 11,
        name = "Filtro de aire Mazda 2",
        description = "Filtro de aire de flujo optimizado para Mazda 2",
        price = "$35.000",
        imageRes = R.drawable.filtro_de_aire_mazda_2,
        contentDescription = "Filtro de aire Mazda 2",
        rating = 4.8,
        badge = "Nuevo",
        category = "Filtros"
    ),
    Product(
        id = 12,
        name = "Filtro de aire Sandero",
        description = "Filtro de aire especial para Renault Sandero y Logan",
        price = "$28.000",
        imageRes = R.drawable.filtro_de_aire_sandero,
        contentDescription = "Filtro de aire Renault Sandero",
        rating = 4.6,
        category = "Filtros"
    ),
    Product(
        id = 13,
        name = "Filtro de aire Spark",
        description = "Filtro de aire tipo panel para Chevrolet Spark Life / GT",
        price = "$18.000",
        imageRes = R.drawable.filtro_de_aire_spark,
        contentDescription = "Filtro de aire Chevrolet Spark",
        rating = 4.4,
        category = "Filtros"
    ),

    // Categoría: Bujias
    Product(
        id = 14,
        name = "Bujías Bosch Super Plus x 4",
        description = "Set de 4 bujías con electrodo de itrio para mejor encendido",
        price = "$45.000",
        imageRes = R.drawable.bujias_bosch,
        contentDescription = "Bujías Bosch 4 unidades",
        rating = 4.9,
        badge = "Top Ventas",
        category = "Bujias"
    ),
    Product(
        id = 15,
        name = "Bujías Champion Copper x 4",
        description = "Bujías de núcleo de cobre para máxima conductividad térmica",
        price = "$38.000",
        imageRes = R.drawable.bujias_champion,
        contentDescription = "Bujías Champion 4 unidades",
        rating = 4.6,
        category = "Bujias"
    ),
    Product(
        id = 16,
        name = "Bujías Motorcraft Platinum x 4",
        description = "Bujías de platino de alta durabilidad para motores Ford y multimarca",
        price = "$52.000",
        imageRes = R.drawable.bujias_motorcraft,
        contentDescription = "Bujías Motorcraft Platinum 4 unidades",
        rating = 4.8,
        category = "Bujias"
    ),

    // Categoría: Refrigeración
    Product(
        id = 17,
        name = "Refrigerante ACDelco 50/50",
        description = "Líquido refrigerante anticorrosivo formulado para protección de radiador",
        price = "$48.000",
        imageRes = R.drawable.refrigerante_acdelco,
        contentDescription = "Refrigerante ACDelco",
        rating = 4.7,
        category = "Refrigeración"
    ),
    Product(
        id = 18,
        name = "Refrigerante Lubristone Coolant",
        description = "Refrigerante protector para altas temperaturas y prevención de óxido",
        price = "$35.000",
        imageRes = R.drawable.refrigerante_lubristone_colant,
        contentDescription = "Refrigerante Lubristone",
        rating = 4.5,
        category = "Refrigeración"
    ),
    Product(
        id = 19,
        name = "Refrigerante PEAK Long Life",
        description = "Refrigerante de vida extendida compatible con todas las marcas y modelos",
        price = "$55.000",
        imageRes = R.drawable.refrigerante_peak,
        contentDescription = "Refrigerante PEAK Long Life",
        rating = 4.9,
        badge = "Oferta",
        category = "Refrigeración"
    ),

    // Categoría: Baterías e Iluminación
    Product(
        id = 20,
        name = "Batería ACDelco 12V",
        description = "Batería de libre mantenimiento con alta capacidad de arranque en frío",
        price = "$320.000",
        imageRes = R.drawable.bateria_acdelco,
        contentDescription = "Batería ACDelco 12V",
        rating = 4.8,
        category = "Baterías e Iluminación"
    ),
    Product(
        id = 21,
        name = "Batería Extrema 12V",
        description = "Batería de alto rendimiento reforzada para máxima durabilidad",
        price = "$290.000",
        imageRes = R.drawable.bateria_extrema,
        contentDescription = "Batería Extrema 12V",
        rating = 4.6,
        category = "Baterías e Iluminación"
    ),
    Product(
        id = 22,
        name = "Batería MAC Gold 12V",
        description = "Batería sellada con tecnología de rejilla de aleación de plata",
        price = "$340.000",
        imageRes = R.drawable.bateria_mac,
        contentDescription = "Batería MAC Gold 12V",
        rating = 4.9,
        badge = "Garantía 18m",
        category = "Baterías e Iluminación"
    ),
    Product(
        id = 23,
        name = "Batería Varta 12V",
        description = "Batería alemana de máxima confiabilidad eléctrica y larga vida útil",
        price = "$365.000",
        imageRes = R.drawable.bateria_varta,
        contentDescription = "Batería Varta 12V",
        rating = 5.0,
        category = "Baterías e Iluminación"
    ),
    Product(
        id = 24,
        name = "Bombillo Farola H4 Halógeno",
        description = "Bombillo H4 de luz halógena estándar 12V 60/55W para luz alta y baja",
        price = "$15.000",
        imageRes = R.drawable.bombillo_farola_h4,
        contentDescription = "Bombillo Farola H4",
        rating = 4.5,
        category = "Baterías e Iluminación"
    ),
    Product(
        id = 25,
        name = "Bombillo para Farola Principal",
        description = "Lámpara de repuesto multifunción para faros delanteros",
        price = "$18.000",
        imageRes = R.drawable.bombillo_para_farola,
        contentDescription = "Bombillo para Farola Principal",
        rating = 4.4,
        category = "Baterías e Iluminación"
    ),

    // Categoría: Suspensión y Dirección
    Product(
        id = 26,
        name = "Amortiguadores Aveo / Sail",
        description = "Juego de amortiguadores delanteros a gas para Chevrolet Aveo y Sail",
        price = "$210.000",
        imageRes = R.drawable.amortiguadores_aveo_sail,
        contentDescription = "Amortiguadores Chevrolet Aveo Sail",
        rating = 4.7,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 27,
        name = "Amortiguadores Captiva",
        description = "Amortiguadores reforzados para camioneta Chevrolet Captiva",
        price = "$380.000",
        imageRes = R.drawable.amortiguadores_captiva,
        contentDescription = "Amortiguadores Chevrolet Captiva",
        rating = 4.8,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 28,
        name = "Amortiguadores Spark GT",
        description = "Amortiguadores hidráulicos de precisión para Chevrolet Spark GT",
        price = "$185.000",
        imageRes = R.drawable.amortiguadores_spark_gt,
        contentDescription = "Amortiguadores Chevrolet Spark GT",
        rating = 4.6,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 29,
        name = "Amortiguadores Sprint",
        description = "Amortiguadores de suspensión para Chevrolet Sprint",
        price = "$160.000",
        imageRes = R.drawable.amortiguadores_sprint,
        contentDescription = "Amortiguadores Chevrolet Sprint",
        rating = 4.5,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 30,
        name = "Terminales de Dirección Aveo",
        description = "Terminales de dirección de alta resistencia para Chevrolet Aveo",
        price = "$42.000",
        imageRes = R.drawable.terminales_de_direccion_aveo,
        contentDescription = "Terminales de dirección Aveo",
        rating = 4.6,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 31,
        name = "Terminales de Dirección Logan",
        description = "Terminales de la barra de acoplamiento para Renault Logan y Sandero",
        price = "$45.000",
        imageRes = R.drawable.terminales_de_direccion_logan,
        contentDescription = "Terminales de dirección Logan",
        rating = 4.7,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 32,
        name = "Terminales de Dirección Corsa",
        description = "Terminales mecánicos de alineación para Chevrolet Corsa",
        price = "$38.000",
        imageRes = R.drawable.terminales_de_direccion_corsa,
        contentDescription = "Terminales de dirección Corsa",
        rating = 4.5,
        category = "Suspensión y Dirección"
    ),
    Product(
        id = 33,
        name = "Terminales de Dirección Spark",
        description = "Terminales de dirección para Chevrolet Spark Life y Chronos",
        price = "$36.000",
        imageRes = R.drawable.terminales_de_direccion_spark,
        contentDescription = "Terminales de dirección Spark",
        rating = 4.4,
        category = "Suspensión y Dirección"
    ),

    // Categoría: Motor y Transmisión
    Product(
        id = 34,
        name = "Kit de Distribución Aveo",
        description = "Incluye correa de repartición y tensor para Chevrolet Aveo 1.4/1.6",
        price = "$135.000",
        imageRes = R.drawable.kit_de_distribucion_aveo,
        contentDescription = "Kit de distribución Aveo",
        rating = 4.9,
        badge = "Oferta",
        category = "Motor y Transmisión"
    ),
    Product(
        id = 35,
        name = "Kit de Distribución Logan 8V",
        description = "Kit completo de tiempo para motor Renault Logan/Sandero 1.6 8V",
        price = "$142.000",
        imageRes = R.drawable.kit_de_distribucion_logan_8v,
        contentDescription = "Kit de distribución Logan 8V",
        rating = 4.8,
        category = "Motor y Transmisión"
    ),
    Product(
        id = 36,
        name = "Kit de Distribución Spark",
        description = "Kit de repartición con polea tensora para Chevrolet Spark",
        price = "$110.000",
        imageRes = R.drawable.kit_de_distribucion_spark,
        contentDescription = "Kit de distribución Spark",
        rating = 4.6,
        category = "Motor y Transmisión"
    ),
    Product(
        id = 37,
        name = "Kit de Distribución Corsa",
        description = "Correa de tiempo y tensor reforzado para Chevrolet Corsa 1.3/1.4/1.6",
        price = "$125.000",
        imageRes = R.drawable.kit_de_distribucion_corsa,
        contentDescription = "Kit de distribución Corsa",
        rating = 4.7,
        category = "Motor y Transmisión"
    ),
    Product(
        id = 38,
        name = "Bomba de Agua Aveo",
        description = "Bomba de agua de refrigeración del motor para Chevrolet Aveo",
        price = "$85.000",
        imageRes = R.drawable.bomba_de_agua_aveo,
        contentDescription = "Bomba de agua Aveo",
        rating = 4.7,
        category = "Motor y Transmisión"
    ),
    Product(
        id = 39,
        name = "Bomba de Agua Corsa",
        description = "Bomba de circulación de refrigerante para Chevrolet Corsa",
        price = "$78.000",
        imageRes = R.drawable.bomba_de_agua_corsa,
        contentDescription = "Bomba de agua Corsa",
        rating = 4.6,
        category = "Motor y Transmisión"
    ),
    Product(
        id = 40,
        name = "Empaque de Culata Aveo",
        description = "Empaque de empaquetadura de cámara/culata para Chevrolet Aveo",
        price = "$48.000",
        imageRes = R.drawable.empaque_de_culata_aveo,
        contentDescription = "Empaque de culata Aveo",
        rating = 4.8,
        category = "Motor y Transmisión"
    )
)
