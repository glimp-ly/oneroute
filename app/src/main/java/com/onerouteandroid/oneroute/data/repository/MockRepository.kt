package com.onerouteandroid.oneroute.data.repository

import com.onerouteandroid.oneroute.data.model.*

object MockRepository {
    val user = UserProfile(
        name = "Mateo Mendoza Valdez",
        email = "mateo.mendoza@ejemplo.com",
        phone = "987 654 321",
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
        plate = "ABC-782",
        colorIndex = 0,
        vin = "3VW2K7AJ9042",
        seats = 3,
        luggage = "Mediano (1 carry-on por viajero)",
        amenities = setOf("Aire Acondicionado (A/C)", "Cargador USB-C", "Audio Bluetooth", "Cajuela Amplia", "Asientos de Piel", "Sanitizado Diario")
    )

    val documents = listOf(
        VerificationDocument("doc1", "Licencia de Conducir", "A-I • MTC Digital", DocumentStatus.APPROVED, "N° Licencia", "Q72381920", "Vigencia", "15 Ago 2026"),
        VerificationDocument("doc2", "Tarjeta de Propiedad", "VW Jetta 2023 • Gris Platino", DocumentStatus.VALID, "Placa Registrada", "ABC-782"),
        VerificationDocument("doc3", "SOAT Vigente", "Rímac Seguros Colectivo", DocumentStatus.EXPIRING, "Póliza", "SOAT-48201-PE", "Vence", "18 Dic 2026"),
        VerificationDocument("doc4", "Identificación Oficial (DNI)", "RENIEC • Validación biométrica", DocumentStatus.VERIFIED, "DNI", "7238••••")
    )

    val carlos = Driver("d1", "Carlos Mendoza", 4.95, 210, "Volkswagen Jetta 2023", "Azul Marino", "ABC-782", "Élite")
    val roberto = Driver("d2", "Roberto G.", 4.9, 142, "Toyota Corolla 2021", "Blanco", "TCR-210")
    val valeria = Driver("d3", "Valeria P.", 5.0, 88, "Honda Civic 2022", "Gris", "VPE-908", "Top Driver")
    val fernando = Driver("d4", "Fernando R.", 4.7, 54, "Nissan Versa 2020", "Plata", "FRN-455")

    val availableTrips = listOf(
        AvailableTrip("at1", carlos, "HOY", "18:30", "19:15", "Miraflores", "Óvalo de Miraflores", "San Isidro", "Centro Empresarial", 45, 24, 2, "Vía Av. Arequipa", listOf("Equipaje mediano", "Aire acondicionado", "Auto libre de humo", "Carga de celular"), autoAccept = false, gpsReliable = true, distanceKm = 12.0),
        AvailableTrip("at2", roberto, "HOY", "18:40", "19:25", "Barranco", "Parque Municipal", "San Borja", "La Rambla", 45, 22, 2, "Vía Circuito / Javier Prado", listOf("A/C", "No fumar"), autoAccept = true, gpsReliable = true, distanceKm = 11.4),
        AvailableTrip("at3", valeria, "HOY", "19:00", "19:40", "Surco", "Óvalo Higuereta", "San Isidro", "Plaza Begonias", 40, 20, 1, "Por Av. Tomás Marsano", listOf("A/C"), autoAccept = false, gpsReliable = true, distanceKm = 9.8),
        AvailableTrip("at4", fernando, "HOY", "19:15", "20:05", "Magdalena", "Plaza Túpac Amaru", "La Molina", "Óvalo Fontana", 50, 28, 3, "Vía Javier Prado", listOf("Cajuela amplia"), autoAccept = false, gpsReliable = false, distanceKm = 14.5)
    )

    val scheduledTrips = listOf(
        ScheduledTrip(
            id = "st1", role = TripRole.PASSENGER, dateTag = "HOY", seatInfo = "Asiento 2A", status = "Confirmado",
            departure = "18:30", arrival = "19:15", origin = "Miraflores", originDetail = "Óvalo de Miraflores",
            destination = "San Isidro", destinationDetail = "Centro Empresarial", durationMin = 45, price = 24,
            driver = carlos, boardingCode = "#4192", paymentCard = "Tarjeta Débito •• 8912"
        ),
        ScheduledTrip(
            id = "st2", role = TripRole.DRIVER, dateTag = "MAÑANA", seatInfo = "2 de 3 asientos ocupados", status = "Publicado por ti",
            departure = "08:00", arrival = "08:55", origin = "Surco", originDetail = "Óvalo Higuereta",
            destination = "San Isidro", destinationDetail = "Av. Rivera Navarrete", durationMin = 55, price = 48,
            passengersSummary = "Andrea R. y Luis G.", seatsCount = 2
        )
    )

    val historyTrips = listOf(
        HistoryTrip("ht1", "Noviembre 2026", "22 Nov", HistoryStatus.COMPLETED, "Completado", 22, "Barranco", "08:15 AM • Parque Municipal", "San Isidro", "09:05 AM • Av. Camino Real", "Valeria P.", "Honda Civic Plateado • 3 acompañantes", 5.0),
        HistoryTrip("ht2", "Noviembre 2026", "19 Nov", HistoryStatus.COMPLETED, "Completado", 20, "Magdalena", "07:50 AM • Plaza Túpac Amaru", "Miraflores", "08:40 AM • Parque Kennedy", "Roberto G.", "Toyota Corolla Blanco • Compartido", null, canRate = true),
        HistoryTrip("ht3", "Octubre 2026", "28 Oct", HistoryStatus.CANCELLED, "Cancelado por conductor", 18, "Miraflores", "Parque Kennedy", "Surco", "Óvalo Higuereta", "Sistema", "Viaje reembolsado", null, refundNote = "Reembolso del 100% • S/ 18.00 PEN acreditados a Saldo Pasajero"),
        HistoryTrip("ht4", "Octubre 2026", "15 Oct", HistoryStatus.AS_DRIVER, "Como Conductor", 72, "San Miguel", "07:30 AM • Plaza San Miguel", "San Isidro", "08:25 AM • Centro Financiero", "3 pasajeros confirmados", "Cupo completo • Transferido", null)
    )

    val incomingDriverRequests = listOf(
        RideRequest(
            id = "req-in-1",
            tripId = "st2",
            tripTitle = "Surco → San Isidro (08:00)",
            passengerName = "Lucía Paredes",
            driverName = user.name,
            pickupPoint = "Av. Benavides cdra. 34",
            destinationPoint = "Av. Rivera Navarrete 450",
            seatsRequested = 1,
            estimatedPrice = 24,
            paymentMethod = "Efectivo al abordar",
            status = RideRequestStatus.PENDIENTE,
            attemptsUsed = 0,
            isIncomingForDriver = true,
            remainingSeconds = 60
        )
    )

    val conversations = listOf(
        Conversation(
            id = "c1", name = "Carlos Mendoza", roleLabel = "Conductor", rating = 4.95, preview = "¡Perfecto! Ya voy saliendo hacia...",
            time = "11:42 hrs", unread = 1, routeLabel = "Miraflores → San Isidro (Hoy 18:30)", active = true, finished = false,
            messages = listOf(
                ChatMessage("m1", false, "¡Hola! Qué tal. Confirmo tu reserva para las 18:30 hrs hacia San Isidro.", "11:20 hrs"),
                ChatMessage("m2", true, "¡Hola Carlos! Excelente. ¿El punto de encuentro es en el paradero frente al parque?", "11:25 hrs"),
                ChatMessage("m3", false, "Exactamente en la bahía lateral del Óvalo. Te comparto el pin exacto abajo 👇", "11:28 hrs"),
                ChatMessage("m4", false, "Punto de Recogida Confirmado", "11:29 hrs", ChatMessageType.LOCATION_PIN, "Av. José Larco 123, Miraflores"),
                ChatMessage("m5", true, "Perfecto, ahí estaré 5 minutos antes con mochila negra. ¡Gracias!", "11:32 hrs"),
                ChatMessage("m6", false, "\"Listo, ya voy en ruta por Av. Arequipa, llego en aprox. 10 minutos.\"", "11:40 hrs", ChatMessageType.VOICE_NOTE, "0:18")
            )
        ),
        Conversation(
            id = "c2", name = "Mariana Ortiz", roleLabel = "Pasajera", rating = 4.9, preview = "Hola, ¿hay espacio para una mochila mediana?",
            time = "Ayer", unread = 0, routeLabel = "Surco → San Isidro (Mañana 08:00)", active = true, finished = false,
            messages = listOf(ChatMessage("m1", false, "Hola, ¿hay espacio para una mochila mediana?", "Ayer"))
        ),
        Conversation(
            id = "c3", name = "Roberto Gómez", roleLabel = "Viaje Finalizado", rating = 4.8, preview = "Gracias por la puntualidad, ¡buen día!",
            time = "22 Nov", unread = 0, routeLabel = "Magdalena → Miraflores", active = false, finished = true,
            messages = listOf(ChatMessage("m1", false, "Gracias por la puntualidad, ¡buen día!", "22 Nov"))
        )
    )

    val supportMessages = listOf(
        ChatMessage("s1", true, "Hola, ayer completé un viaje como conductor hacia San Isidro pero veo pendiente el abono en mi billetera de ganancias.", "11:22 hrs"),
        ChatMessage("s2", false, "¡Hola! Con gusto reviso tu cuenta en este momento mientras consulto la liquidación.", "11:23 hrs"),
        ChatMessage("s3", false, "Validado para abono hoy a las 15:00 hrs", "11:24 hrs", ChatMessageType.TRIP_SUMMARY, "VIAJE ID #VF-8821|23 Noviembre • 08:15 hrs|Miraflores|San Isidro|S/ 72.00"),
        ChatMessage("s4", false, "He verificado que tu depósito de S/ 72.00 PEN ya está programado a tu cuenta CCI sin comisión adicional.", "11:25 hrs")
    )

    val notifications = listOf(
        NotificationItem("n1", "¡Nueva solicitud entrante!", "Lucía Paredes solicita 1 asiento en tu ruta Surco → San Isidro (60 s para responder).", "Hace 1 min", "Hoy", NotificationCategory.TRIPS, NotificationKind.TRIP, false, "Responder", "Ver viaje"),
        NotificationItem("n2", "Abono a Ganancias", "Se acreditaron +S/ 72.00 PEN netos en tu billetera de ganancias por tus viajes compartidos.", "Hace 2 h", "Hoy", NotificationCategory.PAYMENTS, NotificationKind.PAYMENT, false, "Ver Billetera"),
        NotificationItem("n3", "Calificación de 5 estrellas", "\"Excelente conductor, muy puntual y auto impecable.\" — Sofía Morales", "Hace 4 h", "Hoy", NotificationCategory.TRIPS, NotificationKind.RATING, false),
        NotificationItem("n4", "Documentación aprobada", "Tu SOAT y Licencia han sido validados correctamente.", "Ayer, 16:40", "Ayer", NotificationCategory.SYSTEM, NotificationKind.DOCUMENT, true)
    )

    val paymentMethods = listOf(
        PaymentMethod("pm1", "Mastercard Débito", "Terminada en ••8912", PaymentKind.MASTERCARD),
        PaymentMethod("pm2", "Visa Digital", "Terminada en ••3401", PaymentKind.VISA),
        PaymentMethod("pm3", "Google Pay", "1 toque con autenticación biométrica", PaymentKind.GOOGLE_PAY),
        PaymentMethod("pm4", "Efectivo al abordar", "Pago directo al conductor (requiere confirmación)", PaymentKind.CASH),
        PaymentMethod("pm5", "Saldo Billetera Pasajero", "Usa tu crédito interno de viajes futuros", PaymentKind.WALLET),
        PaymentMethod("pm6", "Pago Mixto (Saldo + Digital/Efectivo)", "Combina saldo interno con tarjeta o efectivo", PaymentKind.MIXED)
    )

    val walletTransactions = listOf(
        WalletTransaction("w1", "Viaje Miraflores → San Isidro", "Ganancia neta digital • Hace 2 h", 48.0, "Exitoso", WalletType.GANANCIAS),
        WalletTransaction("w2", "Viaje Surco → San Isidro", "2 pasajeros • Ayer", 72.0, "Exitoso", WalletType.GANANCIAS),
        WalletTransaction("w3", "Retiro CCI a BCP ••4109", "Transferencia • 19 Nov", -200.0, "Exitoso", WalletType.GANANCIAS),
        WalletTransaction("w4", "Reembolso por reducción de ruta", "Crédito interno • Ayer", 15.0, "Disponible", WalletType.PASAJERO),
        WalletTransaction("w5", "Uso de saldo en reserva", "Miraflores → San Isidro • Hoy", -10.0, "Aplicado", WalletType.PASAJERO),
        WalletTransaction("w6", "Recarga Yape / Plin", "Billetera de comisiones • Ayer", 50.0, "Exitoso", WalletType.COMISIONES),
        WalletTransaction("w7", "Comisión viaje en efectivo (10%)", "Reserva #4102 • Hoy", -15.0, "Descontado", WalletType.COMISIONES)
    )

    const val INITIAL_PASSENGER_BALANCE = 45.0
    const val INITIAL_EARNINGS_BALANCE = 845.0
    const val INITIAL_COMMISSION_BALANCE = 35.0
    const val INITIAL_BALANCE = INITIAL_EARNINGS_BALANCE

    val emergencyContacts = listOf(
        EmergencyContact("ec1", "Elena Mendoza", "Madre", "+51 987 111 222", isPrimary = true),
        EmergencyContact("ec2", "Diego Valdez", "Hermano", "+51 999 333 444", isPrimary = false)
    )

    val receivedReportCases = listOf(
        ReceivedReportCase(
            id = "rep-case-1",
            tripLabel = "Magdalena → Miraflores • 19 Nov",
            category = "Incumplimiento",
            summary = "El usuario reportó un retraso de 8 minutos en el punto de encuentro acordado.",
            notifiedAt = "22 Nov 2026",
            remainingCalendarDays = 4
        )
    )

    val incidentCategories = listOf(
        "Falla mecánica",
        "Comportamiento sospechoso",
        "Conflicto",
        "Desvío de ruta",
        "Acoso",
        "Otro"
    )

    val reportCategories = listOf(
        "Acoso",
        "Amenazas",
        "Mal comportamiento",
        "Incumplimiento",
        "Seguridad",
        "Otro"
    )

    val faqTopics = listOf(
        FaqTopic("Pagos, Billetera y Reembolsos", "3 billeteras, recargas y retiros", 8),
        FaqTopic("Reservas, Cancelaciones y Rutas", "Hold de 60 s, cambios y no-show", 12),
        FaqTopic("Seguridad, Verificación y Confianza", "SOS, DNI y reportes inmutables", 6),
        FaqTopic("Cuenta, Vehículo y Documentos", "SOAT, licencias y tarjeta de propiedad", 5),
        FaqTopic("Normativa de Convivencia y Mascotas", "Equipaje, mascotas y música", 4)
    )

    val faqArticles = listOf(
        FaqArticle("faq1", "Reembolsos", "¿Cómo funciona el reembolso si el conductor cancela?", popular = true),
        FaqArticle("faq2", "Encuentros", "¿Qué hacer si un pasajero no se presenta en el punto de encuentro?"),
        FaqArticle("faq3", "Validación", "¿Cómo puedo verificar los documentos de mi vehículo?"),
        FaqArticle("faq4", "Equipaje", "¿Puedo llevar equipaje grande o bicicleta plegable?")
    )
}
