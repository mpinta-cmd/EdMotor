
package com.example.edmotor.data

import com.example.edmotor.R
import com.example.edmotor.model.Product

val productList = listOf(

    // ==========================================
    // 1. ACEITES Y LUBRICANTES
    // ==========================================

    Product(
        id = 1,
        name = "Aceite Mobil 20W-50",
        description = "Lubricante multigrado SAE 20W-50 diseñado para motores a gasolina que requieren esta viscosidad. Ayuda a reducir la fricción entre las piezas internas y contribuye a proteger el motor durante su funcionamiento. Presentación de referencia: 4 litros.",
        price = "$109.900",
        imageRes = R.drawable.mobil_20_w_50,
        contentDescription = "Aceite Mobil 20W-50",
        rating = 4.5,
        category = "Aceites y Lubricantes"
    ),

    Product(
        id = 2,
        name = "Aceite Havoline 20W-50",
        description = "Aceite lubricante multigrado de base mineral para motores a gasolina. Su formulación está orientada a proporcionar lubricación y protección frente al desgaste durante las condiciones habituales de funcionamiento. Presentación de referencia: 4 litros.",
        price = "$99.900",
        imageRes = R.drawable.havoline_20_w_50,
        contentDescription = "Aceite Havoline 20W-50",
        rating = 4.6,
        category = "Aceites y Lubricantes"
    ),

    Product(
        id = 3,
        name = "Filtro de aceite A-111",
        description = "Elemento filtrante encargado de retener partículas, residuos e impurezas presentes en el aceite del motor. Contribuye a mantener limpio el sistema de lubricación y proteger sus componentes internos. Verifica la referencia antes de realizar la compra.",
        price = "$24.900",
        imageRes = R.drawable.filtro_a,
        contentDescription = "Filtro de aceite A-111",
        rating = 4.7,
        badge = "Agotado",
        category = "Filtros"
    ),

    Product(
        id = 4,
        name = "Filtro de aire Aveo",
        description = "Filtro de admisión diseñado para retener polvo y partículas del aire que ingresa al motor de tu Chevrolet Aveo. Ayuda a mantener una correcta filtración del aire y contribuye al funcionamiento adecuado del sistema de admisión. Confirma el año y la versión del vehículo.",
        price = "$29.900",
        imageRes = R.drawable.filtro_de_aire_aveo,
        contentDescription = "Filtro de aire para Chevrolet Aveo",
        rating = 4.6,
        badge = "Nuevo",
        category = "Filtros"
    ),

    Product(
        id = 5,
        name = "Bujías NGK x 4",
        description = "Juego de cuatro bujías NGK para el sistema de encendido del motor. Generan la chispa necesaria para iniciar la combustión de la mezcla de aire y combustible. Una referencia adecuada contribuye a un encendido eficiente y un funcionamiento estable. Verifica la compatibilidad con tu motor.",
        price = "$89.900",
        imageRes = R.drawable.bujias_ngk,
        contentDescription = "Juego de cuatro bujías NGK",
        rating = 5.0,
        badge = "Oferta",
        category = "Bujias"
    ),

    Product(
        id = 6,
        name = "Aceite Shell Helix 20W-50",
        description = "Lubricante multigrado Shell Helix SAE 20W-50 para motores que requieren esta especificación. Contribuye a reducir el desgaste por fricción y mantener lubricadas las piezas internas del motor. Ideal para mantenimiento periódico cuando el fabricante recomienda esta viscosidad. Presentación de referencia: 4 litros.",
        price = "$124.900",
        imageRes = R.drawable.shell_helix_20_w_50,
        contentDescription = "Aceite Shell Helix 20W-50",
        rating = 4.8,
        badge = "Oferta",
        category = "Aceites y Lubricantes"
    ),


    // ==========================================
    // 2. FILTROS
    // ==========================================

    Product(
        id = 7,
        name = "Filtro de aceite A-18",
        description = "Filtro de lubricación diseñado para retener partículas e impurezas que circulan por el aceite del motor. Ayuda a proteger los componentes internos y mantener una adecuada filtración durante los ciclos de funcionamiento. Comprueba la referencia y las dimensiones antes de comprar.",
        price = "$26.900",
        imageRes = R.drawable.filtro_a_18,
        contentDescription = "Filtro de aceite A-18",
        rating = 4.6,
        category = "Filtros"
    ),

    Product(
        id = 8,
        name = "Filtro de aceite A-223",
        description = "Filtro de aceite metálico diseñado para el sistema de lubricación del motor. Su función es retener residuos y partículas contaminantes para contribuir a la limpieza del lubricante. Verifica que la referencia A-223 corresponda a tu vehículo.",
        price = "$28.900",
        imageRes = R.drawable.filtro_a_223,
        contentDescription = "Filtro de aceite A-223",
        rating = 4.5,
        category = "Filtros"
    ),

    Product(
        id = 9,
        name = "Filtro de aceite A-14476",
        description = "Elemento filtrante para el circuito de lubricación, diseñado para ayudar a retener partículas presentes en el aceite del motor. Su correcta selección permite mantener el sistema de lubricación protegido. Consulta la referencia antes de instalarlo.",
        price = "$31.900",
        imageRes = R.drawable.filtro_a_14476,
        contentDescription = "Filtro de aceite A-14476",
        rating = 4.7,
        category = "Filtros"
    ),

    Product(
        id = 10,
        name = "Filtro de aire Corsa",
        description = "Filtro de aire para Chevrolet Corsa, encargado de retener polvo y partículas del ambiente antes de que ingresen al motor. Ayuda a mantener limpio el sistema de admisión y favorece un flujo de aire correctamente filtrado. Confirma la versión y motorización.",
        price = "$29.900",
        imageRes = R.drawable.filtro_de_aire_corsa,
        contentDescription = "Filtro de aire Chevrolet Corsa",
        rating = 4.5,
        category = "Filtros"
    ),

    Product(
        id = 11,
        name = "Filtro de aire Mazda 2",
        description = "Elemento filtrante para el sistema de admisión del Mazda 2. Retiene partículas de polvo y suciedad del aire exterior, ayudando a proteger el motor y conservar la limpieza del sistema. Verifica el año y la motorización para asegurar un ajuste correcto.",
        price = "$42.900",
        imageRes = R.drawable.filtro_de_aire_mazda_2,
        contentDescription = "Filtro de aire Mazda 2",
        rating = 4.8,
        badge = "Nuevo",
        category = "Filtros"
    ),

    Product(
        id = 12,
        name = "Filtro de aire Sandero",
        description = "Filtro de admisión para Renault Sandero y Logan, diseñado para retener polvo y partículas contaminantes antes de que ingresen al motor. Contribuye a proteger los componentes internos y mantener una adecuada filtración del aire. Comprueba la compatibilidad según el año y la versión.",
        price = "$34.900",
        imageRes = R.drawable.filtro_de_aire_sandero,
        contentDescription = "Filtro de aire Renault Sandero",
        rating = 4.6,
        category = "Filtros"
    ),

    Product(
        id = 13,
        name = "Filtro de aire Spark",
        description = "Filtro de aire tipo panel para Chevrolet Spark Life y Spark GT. Su función es retener polvo e impurezas del ambiente para evitar que ingresen al motor. Una filtración adecuada contribuye al buen funcionamiento del sistema de admisión. Verifica la referencia correspondiente a tu versión.",
        price = "$24.900",
        imageRes = R.drawable.filtro_de_aire_spark,
        contentDescription = "Filtro de aire Chevrolet Spark",
        rating = 4.4,
        category = "Filtros"
    ),


    // ==========================================
    // 3. BUJÍAS
    // ==========================================

    Product(
        id = 14,
        name = "Bujías Bosch Super Plus x 4",
        description = "Juego de cuatro bujías Bosch Super Plus para sistemas de encendido de motores a gasolina. Incorporan un diseño de electrodo orientado a proporcionar una chispa adecuada para la combustión. Una buena elección de referencia favorece el encendido y la estabilidad del motor.",
        price = "$99.900",
        imageRes = R.drawable.bujias_bosch,
        contentDescription = "Juego de cuatro bujías Bosch Super Plus",
        rating = 4.9,
        badge = "Top Ventas",
        category = "Bujias"
    ),

    Product(
        id = 15,
        name = "Bujías Champion Copper x 4",
        description = "Juego de cuatro bujías Champion Copper con núcleo de cobre, diseñadas para el sistema de encendido de motores a gasolina. Contribuyen a generar la chispa necesaria para la combustión y mantener un funcionamiento adecuado del motor. Verifica la referencia según el vehículo.",
        price = "$84.900",
        imageRes = R.drawable.bujias_champion,
        contentDescription = "Juego de cuatro bujías Champion Copper",
        rating = 4.6,
        category = "Bujias"
    ),

    Product(
        id = 16,
        name = "Bujías Motorcraft Platinum x 4",
        description = "Juego de cuatro bujías Motorcraft Platinum con electrodo de platino. Diseñadas para ofrecer un encendido confiable y una buena resistencia al desgaste del electrodo. Ideales para vehículos compatibles con esta referencia. Consulta el código de motor antes de realizar la compra.",
        price = "$169.900",
        imageRes = R.drawable.bujias_motorcraft,
        contentDescription = "Juego de cuatro bujías Motorcraft Platinum",
        rating = 4.8,
        category = "Bujias"
    ),


    // ==========================================
    // 4. REFRIGERACIÓN
    // ==========================================

    Product(
        id = 17,
        name = "Refrigerante ACDelco 50/50",
        description = "Líquido refrigerante ACDelco con formulación 50/50 para el sistema de enfriamiento del vehículo. Contribuye a regular la temperatura de funcionamiento del motor y proteger los componentes del circuito contra la corrosión. Comprueba las especificaciones recomendadas por el fabricante.",
        price = "$59.900",
        imageRes = R.drawable.refrigerante_acdelco,
        contentDescription = "Refrigerante ACDelco 50/50",
        rating = 4.7,
        category = "Refrigeración"
    ),

    Product(
        id = 18,
        name = "Refrigerante Lubristone Coolant",
        description = "Refrigerante automotriz Lubristone para sistemas de enfriamiento. Ayuda a mantener una temperatura de funcionamiento adecuada y contribuye a proteger el radiador y los conductos del circuito. Verifica la concentración, dilución y compatibilidad antes de utilizarlo.",
        price = "$39.900",
        imageRes = R.drawable.refrigerante_lubristone_colant,
        contentDescription = "Refrigerante Lubristone Coolant",
        rating = 4.5,
        category = "Refrigeración"
    ),

    Product(
        id = 19,
        name = "Refrigerante PEAK Long Life",
        description = "Refrigerante PEAK Long Life diseñado para el sistema de enfriamiento automotriz. Su formulación está orientada a proporcionar protección prolongada contra la corrosión y ayudar a controlar la temperatura del motor. Consulta las especificaciones de compatibilidad y el intervalo de mantenimiento recomendado.",
        price = "$79.900",
        imageRes = R.drawable.refrigerante_peak,
        contentDescription = "Refrigerante PEAK Long Life",
        rating = 4.9,
        badge = "Oferta",
        category = "Refrigeración"
    ),


    // ==========================================
    // 5. BATERÍAS E ILUMINACIÓN
    // ==========================================

    Product(
        id = 20,
        name = "Batería ACDelco 12V",
        description = "Batería automotriz ACDelco de 12 voltios diseñada para suministrar energía al sistema de arranque y los componentes eléctricos del vehículo. Una batería adecuada proporciona la corriente necesaria para encender el motor y alimentar los sistemas eléctricos. Verifica capacidad, dimensiones y polaridad.",
        price = "$420.000",
        imageRes = R.drawable.bateria_acdelco,
        contentDescription = "Batería ACDelco 12 voltios",
        rating = 4.8,
        category = "Baterías e Iluminación"
    ),

    Product(
        id = 21,
        name = "Batería Extrema 12V",
        description = "Batería automotriz de 12 voltios de la línea Extrema, destinada a proporcionar energía para el arranque y funcionamiento de los sistemas eléctricos. Su elección debe corresponder a las necesidades eléctricas del vehículo. Confirma amperaje, capacidad de arranque, dimensiones y polaridad.",
        price = "$380.000",
        imageRes = R.drawable.bateria_extrema,
        contentDescription = "Batería Extrema 12 voltios",
        rating = 4.6,
        category = "Baterías e Iluminación"
    ),

    Product(
        id = 22,
        name = "Batería MAC Gold 12V",
        description = "Batería MAC Gold de 12 voltios para aplicaciones automotrices. Diseñada para suministrar energía al motor de arranque y los diferentes componentes eléctricos del vehículo. Su instalación debe realizarse verificando las dimensiones, capacidad, terminales y polaridad correspondientes.",
        price = "$410.000",
        imageRes = R.drawable.bateria_mac,
        contentDescription = "Batería MAC Gold 12 voltios",
        rating = 4.9,
        badge = "Garantía 18m",
        category = "Baterías e Iluminación"
    ),

    Product(
        id = 23,
        name = "Batería Varta 12V",
        description = "Batería automotriz Varta de 12 voltios, diseñada para proporcionar energía de arranque y alimentación eléctrica al vehículo. Una opción para quienes buscan una solución de una marca reconocida en el sector automotor. Verifica capacidad, dimensiones y compatibilidad antes de instalar.",
        price = "$450.000",
        imageRes = R.drawable.bateria_varta,
        contentDescription = "Batería Varta 12 voltios",
        rating = 5.0,
        category = "Baterías e Iluminación"
    ),

    Product(
        id = 24,
        name = "Bombillo Farola H4 Halógeno",
        description = "Bombillo halógeno H4 de 12 voltios y potencia 60/55 W, diseñado para sistemas de farolas que utilizan doble función de iluminación. Integra luz alta y luz baja en una misma unidad. Verifica el tipo de base y las especificaciones eléctricas de tu vehículo.",
        price = "$22.900",
        imageRes = R.drawable.bombillo_farola_h4,
        contentDescription = "Bombillo halógeno H4 12V 60/55W",
        rating = 4.5,
        category = "Baterías e Iluminación"
    ),

    Product(
        id = 25,
        name = "Bombillo para Farola Principal",
        description = "Bombillo de reemplazo para el sistema de iluminación principal del vehículo. Diseñado para recuperar la iluminación delantera cuando la unidad original presenta fallas. Antes de comprar, comprueba el tipo de conexión, potencia y referencia requerida por tu automóvil.",
        price = "$24.900",
        imageRes = R.drawable.bombillo_para_farola,
        contentDescription = "Bombillo para farola principal",
        rating = 4.4,
        category = "Baterías e Iluminación"
    ),


    // ==========================================
    // 6. SUSPENSIÓN Y DIRECCIÓN
    // ==========================================

    Product(
        id = 26,
        name = "Amortiguadores Aveo / Sail",
        description = "Juego de amortiguadores delanteros a gas para Chevrolet Aveo y Sail. Diseñados para controlar los movimientos de la suspensión, mejorar la estabilidad y mantener el contacto de las ruedas con la carretera. Precio referencial por el juego delantero. Confirma año, versión y referencia.",
        price = "$420.000",
        imageRes = R.drawable.amortiguadores_aveo_sail,
        contentDescription = "Juego de amortiguadores delanteros Chevrolet Aveo y Sail",
        rating = 4.7,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 27,
        name = "Amortiguadores Captiva",
        description = "Amortiguador de suspensión para Chevrolet Captiva, diseñado para controlar las oscilaciones producidas durante la conducción y mejorar la estabilidad del vehículo. Contribuye a una marcha más controlada y confortable. Precio por unidad; confirma el eje, lado y versión de tu Captiva.",
        price = "$320.000",
        imageRes = R.drawable.amortiguadores_captiva,
        contentDescription = "Amortiguador Chevrolet Captiva",
        rating = 4.8,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 28,
        name = "Amortiguadores Spark GT",
        description = "Amortiguador hidráulico para Chevrolet Spark GT, encargado de controlar el movimiento de la suspensión y reducir las oscilaciones generadas por las irregularidades del terreno. Ayuda a conservar la estabilidad y el confort de marcha. Precio por unidad; verifica el eje y el lado correspondiente.",
        price = "$240.000",
        imageRes = R.drawable.amortiguadores_spark_gt,
        contentDescription = "Amortiguador Chevrolet Spark GT",
        rating = 4.6,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 29,
        name = "Amortiguadores Sprint",
        description = "Amortiguador de suspensión compatible con Chevrolet Sprint según su referencia. Su función es controlar los rebotes y movimientos de la carrocería para contribuir a una conducción más estable y confortable. Precio por unidad; comprueba el eje y las especificaciones antes de comprar.",
        price = "$185.000",
        imageRes = R.drawable.amortiguadores_sprint,
        contentDescription = "Amortiguador Chevrolet Sprint",
        rating = 4.5,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 30,
        name = "Terminales de Dirección Aveo",
        description = "Terminal de dirección para Chevrolet Aveo, componente que conecta la barra de dirección con el conjunto de la rueda y permite transmitir los movimientos del volante. Contribuye a conservar la precisión del sistema de dirección. Precio por unidad; verifica el lado y la referencia.",
        price = "$65.000",
        imageRes = R.drawable.terminales_de_direccion_aveo,
        contentDescription = "Terminal de dirección Chevrolet Aveo",
        rating = 4.6,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 31,
        name = "Terminales de Dirección Logan",
        description = "Terminal de dirección para Renault Logan y Sandero, diseñado para transmitir el movimiento del sistema de dirección hacia las ruedas delanteras. Es un componente importante para mantener una respuesta adecuada del volante y la geometría de dirección. Precio por unidad; confirma la referencia y el lado.",
        price = "$68.000",
        imageRes = R.drawable.terminales_de_direccion_logan,
        contentDescription = "Terminal de dirección Renault Logan",
        rating = 4.7,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 32,
        name = "Terminales de Dirección Corsa",
        description = "Terminal de dirección para Chevrolet Corsa, encargado de transmitir el movimiento de la cremallera hacia las ruedas. Su correcto estado ayuda a mantener una dirección precisa y un comportamiento adecuado del vehículo. Precio por unidad; comprueba la versión y el lado de instalación.",
        price = "$58.000",
        imageRes = R.drawable.terminales_de_direccion_corsa,
        contentDescription = "Terminal de dirección Chevrolet Corsa",
        rating = 4.5,
        category = "Suspensión y Dirección"
    ),

    Product(
        id = 33,
        name = "Terminales de Dirección Spark",
        description = "Terminal de dirección compatible con Chevrolet Spark Life y Chronos según referencia. Forma parte del sistema de dirección y permite transmitir los movimientos del volante hacia las ruedas delanteras. Precio por unidad; verifica el modelo, año y lado correspondiente.",
        price = "$55.000",
        imageRes = R.drawable.terminales_de_direccion_spark,
        contentDescription = "Terminal de dirección Chevrolet Spark",
        rating = 4.4,
        category = "Suspensión y Dirección"
    ),


    // ==========================================
    // 7. MOTOR Y TRANSMISIÓN
    // ==========================================

    Product(
        id = 34,
        name = "Kit de Distribución Aveo",
        description = "Kit de distribución para Chevrolet Aveo con motor 1.4 o 1.6, según la referencia. Incluye correa de distribución y tensor para mantener sincronizado el funcionamiento del cigüeñal y el árbol de levas. La composición exacta debe confirmarse con el proveedor antes de comprar.",
        price = "$350.000",
        imageRes = R.drawable.kit_de_distribucion_aveo,
        contentDescription = "Kit de distribución Chevrolet Aveo",
        rating = 4.9,
        badge = "Oferta",
        category = "Motor y Transmisión"
    ),

    Product(
        id = 35,
        name = "Kit de Distribución Logan 8V",
        description = "Kit de distribución para Renault Logan y Sandero con motor 1.6 de 8 válvulas, según referencia. Diseñado para mantener la sincronización entre los componentes internos del motor. Una pieza fundamental para el mantenimiento preventivo del sistema de distribución. Verifica el código de motor y los componentes incluidos.",
        price = "$340.000",
        imageRes = R.drawable.kit_de_distribucion_logan_8v,
        contentDescription = "Kit de distribución Renault Logan 8 válvulas",
        rating = 4.8,
        category = "Motor y Transmisión"
    ),

    Product(
        id = 36,
        name = "Kit de Distribución Spark",
        description = "Kit de distribución para Chevrolet Spark, compuesto según la referencia por los elementos necesarios para el mantenimiento del sistema de tiempo. Contribuye a mantener la sincronización correcta del motor y evitar fallas relacionadas con el desgaste de la correa. Confirma la versión y el contenido del kit.",
        price = "$280.000",
        imageRes = R.drawable.kit_de_distribucion_spark,
        contentDescription = "Kit de distribución Chevrolet Spark",
        rating = 4.6,
        category = "Motor y Transmisión"
    ),

    Product(
        id = 37,
        name = "Kit de Distribución Corsa",
        description = "Kit de distribución para Chevrolet Corsa con motorizaciones 1.3, 1.4 o 1.6, de acuerdo con la referencia. Diseñado para conservar la sincronización entre el cigüeñal y el árbol de levas. Una opción para el mantenimiento preventivo del motor. Comprueba la versión y los componentes incluidos.",
        price = "$310.000",
        imageRes = R.drawable.kit_de_distribucion_corsa,
        contentDescription = "Kit de distribución Chevrolet Corsa",
        rating = 4.7,
        category = "Motor y Transmisión"
    ),

    Product(
        id = 38,
        name = "Bomba de Agua Aveo",
        description = "Bomba de agua para el sistema de refrigeración del Chevrolet Aveo. Su función es impulsar el refrigerante a través del motor y el radiador para ayudar a mantener una temperatura de funcionamiento adecuada. Verifica la motorización y referencia para garantizar una instalación compatible.",
        price = "$145.000",
        imageRes = R.drawable.bomba_de_agua_aveo,
        contentDescription = "Bomba de agua Chevrolet Aveo",
        rating = 4.7,
        category = "Motor y Transmisión"
    ),

    Product(
        id = 39,
        name = "Bomba de Agua Corsa",
        description = "Bomba de circulación de refrigerante para Chevrolet Corsa. Permite mantener el flujo del líquido refrigerante entre el motor y el radiador, contribuyendo al control de la temperatura y al funcionamiento del sistema de enfriamiento. Confirma la referencia y motorización antes de realizar la compra.",
        price = "$135.000",
        imageRes = R.drawable.bomba_de_agua_corsa,
        contentDescription = "Bomba de agua Chevrolet Corsa",
        rating = 4.6,
        category = "Motor y Transmisión"
    ),

    Product(
        id = 40,
        name = "Empaque de Culata Aveo",
        description = "Empaque de culata para Chevrolet Aveo, diseñado para sellar la unión entre el bloque del motor y la culata. Ayuda a mantener la compresión y evitar fugas entre los conductos de aceite, refrigerante y las cámaras de combustión. Verifica el motor y el espesor requerido antes de instalar.",
        price = "$95.000",
        imageRes = R.drawable.empaque_de_culata_aveo,
        contentDescription = "Empaque de culata Chevrolet Aveo",
        rating = 4.8,
        category = "Motor y Transmisión"
    )

)
