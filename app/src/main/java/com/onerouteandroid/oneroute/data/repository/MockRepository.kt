package com.onerouteandroid.oneroute.data.repository

import com.onerouteandroid.oneroute.data.model.*

object MockRepository {
    val user = UserProfile(
        name = "Mateo Mendoza Valdez",
        email = "mateo.mendoza@ejemplo.com",
        phone = "55 4892 1047",
        rating = 4.95,
        reviewCount = 218,
        level = "CONDUCTOR ÉLITE • NIVEL 3",
        memberSince = "Mar 2023",
        sharedTrips = 142,
        punctuality = 98,
        co2SavedKg = 312
    )

    val vehicle = VehicleInfo(
        model = "Volkswagen Jetta Trendline",
        year = 2023,
        plate = "782-XYZ",
        colorIndex = 0,
        vin = "3VW2K7AJ9042",
        seats = 3,
        luggage = "Mediano (1 carry-on por viajero)",
        amenities = setOf("Aire Acondicionado (A/C)", "Cargador USB-C", "Audio Bluetooth", "Cajuela Amplia", "Asientos de Piel", "Sanitizado Diario")
    )

    val documents = listOf(
        VerificationDocument("doc1", "Licencia de Conducir", "Tipo A • CDMX Digital", DocumentStatus.APPROVED, "Folio Oficial", "93821094-B", "Vigencia", "15 Ago 2026"),
        VerificationDocument("doc2", "Tarjeta de Circulación", "VW Jetta 2021 • Gris Platino", DocumentStatus.VALID, "Placas Registradas", "782-XYZ"),
        VerificationDocument("doc3", "Seguro Cobertura Amplia", "GNP Seguros Colectivo", DocumentStatus.EXPIRING, "Póliza", "GNP-48201-MX", "Vence", "18 Dic 2024"),
        VerificationDocument("doc4", "Identificación Oficial", "Credencial INE • Biometría facial", DocumentStatus.VERIFIED, "ID", "8291-MX")
    )

    val carlos = Driver("d1", "Carlos Mendoza", 4.95, 210, "Volkswagen Jetta 2023", "Azul Marino", "782-XYZ", "Élite")
    val roberto = Driver("d2", "Roberto G.", 4.9, 142, "Toyota Corolla 2021", "Blanco", "TCR-210")
    val valeria = Driver("d3", "Valeria P.", 5.0, 88, "Honda Civic 2022", "Gris", "VPE-908", "Top Driver")
    val fernando = Driver("d4", "Fernando R.", 4.7, 54, "Nissan Versa 2020", "Plata", "FRN-455")

    val availableTrips = listOf(
        AvailableTrip("at1", carlos, "HOY", "18:30", "19:15", "Condesa", "Parque México", "Santa Fe", "Torre Arcos Bosques", 45, 65, 2, "Vía Insurgentes", listOf("Equipaje mediano", "Aire acondicionado", "Auto libre de humo", "Carga de celular")),
        AvailableTrip("at2", roberto, "HOY", "18:40", "19:25", "Metro Insurgentes", "Andén dirección sur", "C.C. Santa Fe", "Acceso principal", 45, 60, 2, "Vía Autopista Urbana", listOf("A/C", "No fumar")),
        AvailableTrip("at3", valeria, "HOY", "19:00", "19:40", "Metro Insurgentes", "Salida Chilpancingo", "C.C. Santa Fe", "Plaza central", 40, 55, 1, "Por Constituyentes", listOf("A/C")),
        AvailableTrip("at4", fernando, "HOY", "19:15", "20:05", "Metro Insurgentes", "Glorieta", "C.C. Santa Fe", "Estacionamiento norte", 50, 45, 3, "Ruta Palmas / Bosques", listOf("Cajuela amplia"))
    )

    val scheduledTrips = listOf(
        ScheduledTrip(
            id = "st1", role = TripRole.PASSENGER, dateTag = "HOY", seatInfo = "Asiento 2A", status = "Confirmado",
            departure = "18:30", arrival = "19:15", origin = "Condesa", originDetail = "Parque México (Kiosco)",
            destination = "Santa Fe", destinationDetail = "Torre Arcos Bosques", durationMin = 45, price = 65,
            driver = carlos, boardingCode = "#4192", paymentCard = "Tarjeta Débito •• 8912"
        ),
        ScheduledTrip(
            id = "st2", role = TripRole.DRIVER, dateTag = "MAÑANA", seatInfo = "2 de 3 asientos ocupados", status = "Publicado por ti",
            departure = "08:00", arrival = "08:55", origin = "Coyoacán Centro", originDetail = "Jardín Hidalgo (Kiosco)",
            destination = "Polanco", destinationDetail = "Plaza Carso • Calle Lago Zurich", durationMin = 55, price = 130,
            passengersSummary = "Andrea R. y Luis G."
        )
    )

    val historyTrips = listOf(
        HistoryTrip("ht1", "Octubre 2024", "22 Oct", HistoryStatus.COMPLETED, "Completado", 55, "Roma Norte", "08:15 AM • Av. Álvaro Obregón", "Santa Fe", "09:05 AM • Torre Mayor corporativo", "Valeria P.", "Honda Civic Plateado • 3 acompañantes", 5.0),
        HistoryTrip("ht2", "Octubre 2024", "19 Oct", HistoryStatus.COMPLETED, "Completado", 60, "Polanco", "07:50 AM • Metro Polanco", "Insurgentes Sur", "08:40 AM • Glorieta de Insurgentes", "Roberto G.", "Toyota Corolla Blanco • Compartido", null, canRate = true),
        HistoryTrip("ht3", "Septiembre 2024", "28 Sep", HistoryStatus.CANCELLED, "Cancelado por conductor", 45, "Condesa", "Parque México", "Coyoacán", "Centro de Coyoacán", "Sistema", "Viaje reembolsado", null, refundNote = "Reembolso del 100% • $45.00 MXN acreditados"),
        HistoryTrip("ht4", "Septiembre 2024", "15 Sep", HistoryStatus.AS_DRIVER, "Como Conductor", 195, "Del Valle", "07:30 AM • Parque Hundido", "Santa Fe", "08:25 AM • Centro Comercial Santa Fe", "3 pasajeros confirmados", "Cupo completo • Transferido", null)
    )

    val conversations = listOf(
        Conversation(
            id = "c1", name = "Carlos Mendoza", roleLabel = "Conductor", rating = 4.95, preview = "¡Perfecto! Ya voy saliendo hacia...",
            time = "11:42 hrs", unread = 1, routeLabel = "Condesa → Santa Fe (Hoy 18:30)", active = true, finished = false,
            messages = listOf(
                ChatMessage("m1", false, "¡Hola! Qué tal. Confirmo tu reserva para las 18:30 hrs hacia Santa Fe.", "11:20 hrs"),
                ChatMessage("m2", true, "¡Hola Carlos! Excelente. Una pregunta rápida, ¿el punto de encuentro exacto es por la entrada de Michoacán o frente al quiosco?", "11:25 hrs"),
                ChatMessage("m3", false, "Es exactamente frente al Foro Lindbergh sobre Av. México. Hay una bahía de ascenso donde se puede estacionar sin fotomulta ni tráfico. Te comparto el pin exacto abajo 👇", "11:28 hrs"),
                ChatMessage("m4", false, "Punto de Recogida Confirmado", "11:29 hrs", ChatMessageType.LOCATION_PIN, "Av. México 123 (Foro Lindbergh), Hipódromo Condesa"),
                ChatMessage("m5", true, "Perfecto, ahí estaré 5 minutos antes con mochila negra. ¡Gracias!", "11:32 hrs"),
                ChatMessage("m6", false, "\"Listo, ya voy en ruta por Av. Álvaro Obregón, llego en aprox. 10 minutos en el Jetta azul.\"", "11:40 hrs", ChatMessageType.VOICE_NOTE, "0:18")
            )
        ),
        Conversation(
            id = "c2", name = "Mariana Ortiz", roleLabel = "Pasajera", rating = 4.9, preview = "Hola Carlos, ¿hay espacio para una maleta mediana en cajuela?",
            time = "Ayer", unread = 0, routeLabel = "Coyoacán → Polanco (Mañana 08:00)", active = true, finished = false,
            messages = listOf(ChatMessage("m1", false, "Hola Carlos, ¿hay espacio para una maleta mediana en cajuela?", "Ayer"))
        ),
        Conversation(
            id = "c3", name = "Roberto Gómez", roleLabel = "Viaje Finalizado", rating = 4.8, preview = "Gracias por la puntualidad, ¡buen día!",
            time = "22 Oct", unread = 0, routeLabel = "Polanco → Insurgentes Sur", active = false, finished = true,
            messages = listOf(ChatMessage("m1", false, "Gracias por la puntualidad, ¡buen día!", "22 Oct"))
        ),
        Conversation(
            id = "c4", name = "Valeria Peña", roleLabel = "Viaje Finalizado", rating = 5.0, preview = "¡Excelente viaje compartido!",
            time = "19 Oct", unread = 0, routeLabel = "Roma Norte → Santa Fe", active = false, finished = true,
            messages = listOf(ChatMessage("m1", false, "¡Excelente viaje compartido!", "19 Oct"))
        )
    )

    val supportMessages = listOf(
        ChatMessage("s1", true, "Hola Mariana, ayer completé un viaje como conductor hacia Santa Fe pero veo pendiente la transferencia semanal en mi Billetera. ¿Cuándo se procesa?", "11:22 hrs"),
        ChatMessage("s2", false, "¡Hola! Qué gusto saludarte. Con gusto reviso tu cuenta en este momento. Dame un instante mientras consulto el registro en el sistema bancario SPEI.", "11:23 hrs"),
        ChatMessage("s3", false, "Validado para SPEI hoy a las 15:00 hrs", "11:24 hrs", ChatMessageType.TRIP_SUMMARY, "VIAJE ID #VF-8821|23 Octubre • 08:15 hrs|Condesa (Parque México)|Santa Fe (Corporativo Poniente)|\$195.00"),
        ChatMessage("s4", false, "He verificado que tu depósito de \$195 MXN está programado para la dispersión de las 15:00 hrs de hoy sin comisión. ¿Te gustaría que te envíe el comprobante rastreable de Banxico cuando se complete?", "11:25 hrs")
    )

    val notifications = listOf(
        NotificationItem("n1", "¡Nuevo pasajero confirmado!", "Diego Valdés se ha unido a tu ruta hacia Santa Fe hoy a las 18:30 hrs. Asiento 2B.", "Hace 15 min", "Hoy", NotificationCategory.TRIPS, NotificationKind.TRIP, false, "Ver reserva", "Mensaje"),
        NotificationItem("n2", "Transferencia programada", "Se han acreditado +\$195.00 MXN a tu saldo disponible por tus viajes compartidos de ayer.", "Hace 2 h", "Hoy", NotificationCategory.PAYMENTS, NotificationKind.PAYMENT, false, "Ver Billetera"),
        NotificationItem("n3", "Calificación de 5 estrellas", "\"Excelente conductor, muy puntual y auto impecable.\" — Sofía Morales (+10 Karma)", "Hace 4 h", "Hoy", NotificationCategory.TRIPS, NotificationKind.RATING, false),
        NotificationItem("n4", "Documentación aprobada", "Tu póliza de seguro GNP ha sido validada exitosamente por el Centro de Confianza. Conduce con respaldo total.", "Ayer, 16:40", "Ayer", NotificationCategory.SYSTEM, NotificationKind.DOCUMENT, true),
        NotificationItem("n5", "Aviso vial para tu ruta habitual", "Reportan obras en Av. Constituyentes. Te recomendamos salir 10 minutos antes para llegar a tiempo a tus puntos de encuentro.", "Ayer, 07:15", "Ayer", NotificationCategory.TRIPS, NotificationKind.ALERT, true, "Explorar vías alternas"),
        NotificationItem("n6", "Logro: Guardián del Aire 🌱", "¡Felicidades! Has evitado más de 25 kg de CO₂ este mes compartiendo tu auto con colegas de oficina.", "21 Oct", "Esta semana", NotificationCategory.SYSTEM, NotificationKind.ACHIEVEMENT, true, "Compartir medalla")
    )

    val paymentMethods = listOf(
        PaymentMethod("pm1", "Mastercard Débito", "Terminada en ••8912", PaymentKind.MASTERCARD),
        PaymentMethod("pm2", "Visa Banorte Digital", "Terminada en ••3401", PaymentKind.VISA),
        PaymentMethod("pm3", "Google Pay", "1 toque con autenticación biométrica", PaymentKind.GOOGLE_PAY)
    )

    val walletTransactions = listOf(
        WalletTransaction("w1", "Condesa a Santa Fe", "Conductor • Hace 2 h", 65.0, "Completado"),
        WalletTransaction("w2", "Coyoacán a Polanco", "2 pasajeros • Ayer", 130.0, "Completado"),
        WalletTransaction("w3", "Roma Norte a Santa Fe", "Pasajero • 22 Oct", -55.0, "Tarjeta ••8912"),
        WalletTransaction("w4", "Retiro SPEI a BBVA", "Cuenta ••4109 • 19 Oct", -500.0, "Exitoso")
    )

    const val INITIAL_BALANCE = 845.0

    val faqTopics = listOf(
        FaqTopic("Pagos, Billetera y Reembolsos", "Tarjetas, transferencias y retiros", 8),
        FaqTopic("Reservas, Cancelaciones y Rutas", "Puntos de encuentro y cambios", 12),
        FaqTopic("Seguridad, Verificación y Confianza", "Validación biométrica e historial", 6),
        FaqTopic("Cuenta, Vehículo y Documentos", "Pólizas, licencias y verificación", 5),
        FaqTopic("Normativa de Convivencia y Mascotas", "Equipaje, mascotas y música", 4)
    )

    val faqArticles = listOf(
        FaqArticle("faq1", "Reembolsos", "¿Cómo funciona el reembolso si el conductor cancela?", popular = true),
        FaqArticle("faq2", "Encuentros", "¿Qué hacer si un pasajero no se presenta en el punto de encuentro?"),
        FaqArticle("faq3", "Validación", "¿Cómo puedo verificar los documentos de mi vehículo?"),
        FaqArticle("faq4", "Equipaje", "¿Puedo llevar equipaje grande o bicicleta plegable?")
    )
}
