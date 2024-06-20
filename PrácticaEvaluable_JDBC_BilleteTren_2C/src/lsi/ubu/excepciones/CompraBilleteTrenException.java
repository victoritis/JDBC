/*
 * Nombre: CompraBilleteTrenException
 * Descripción: Clase de excepciones personalizadas para la compra de billetes de tren
 * Autor: Victor Gonzalez Del Campo
 * Versión: 1.0
 */
package lsi.ubu.excepciones;

import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CompraBilleteTrenException: Implementa las excepciones específicas de la transacción de compra de billetes de tren
 *
 * @autor <a href="mailto:victor@example.com">Victor Gonzalez Del Campo</a>
 * @versión 1.0
 * @desde 1.0
 */
public class CompraBilleteTrenException extends SQLException {

	private static final long serialVersionUID = 1L;

	private static final Logger LOGGER = LoggerFactory.getLogger(CompraBilleteTrenException.class);

	public static final int NO_PLAZAS = 1; // No hay suficientes plazas para completar la compra
	public static final int NO_EXISTE_VIAJE = 2; // El viaje solicitado no existe
	public static final int NO_RESERVAS = 3; // No se han realizado reservas
	public static final int NO_TICKET = 4; // El ticket seleccionado no se encuentra

	private int codigo;
	private String mensaje;

	public CompraBilleteTrenException(int codigo) {
		this.codigo = codigo; // Almacenamos el código de error proporcionado
		switch (codigo) {
			case NO_PLAZAS:
				mensaje = "El viaje seleccionado no tiene suficientes plazas disponibles.";
				break;
			case NO_EXISTE_VIAJE:
				mensaje = "El viaje seleccionado no existe.";
				break;
			case NO_RESERVAS:
				mensaje = "El número de plazas a anular es mayor que las plazas reservadas en el ticket.";
				break;
			case NO_TICKET:
				mensaje = "El ticket seleccionado no se ha encontrado.";
				break;
			default:
				mensaje = "Error desconocido.";
				break;
		}

		LOGGER.debug(mensaje); // Registramos el mensaje de error

		// Imprimimos la traza de la pila
		for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
			LOGGER.debug(ste.toString());
		}
	}

	@Override
	public String getMessage() { // Redefinición del método getMessage de la clase Exception
		return mensaje;
	}

	@Override
	public int getErrorCode() { // Redefinición del método getErrorCode de la clase SQLException
		return codigo;
	}
}
