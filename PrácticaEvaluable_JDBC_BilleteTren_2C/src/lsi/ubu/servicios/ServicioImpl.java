/*
 * Autores:Victor Gozalez del Campo
 * Ver: 1.0
 */
package lsi.ubu.servicios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.excepciones.CompraBilleteTrenException;
import lsi.ubu.util.PoolDeConexiones;

public class ServicioImpl implements Servicio {

	// Logger.
	private static final Logger LOGGER = LoggerFactory.getLogger(ServicioImpl.class);

	// Método que implementa la lógica de modificación de billetes de tren.
	@Override
	public void modificarBillete(int idBillete, int nuevasPlazas) throws SQLException {
		PoolDeConexiones poolDeConexiones = PoolDeConexiones.getInstance(); // Obtenemos la instancia del pool de conexiones
		Connection conexion = null; // Variable para la conexión
		PreparedStatement pstmt = null; // Variable para la consulta preparada
		ResultSet rs = null; // Variable para el resultado de la consulta
		int precioActual; // Variable para almacenar el precio actual del billete
		int idViaje; // Variable para almacenar el ID del viaje
		int plazasLibres; // Variable para almacenar el número de plazas disponibles
		int plazasReservadas; // Variable para almacenar el número de plazas reservadas

		try {
			conexion = poolDeConexiones.getConnection(); // Obtenemos una conexión del pool
			conexion.setAutoCommit(false); // Deshabilitamos el auto-commit para manejar la transacción manualmente

			// Consulta para obtener la información del billete actual
			String consultaBillete = "SELECT IDVIAJE, CANTIDAD, PRECIO FROM tickets WHERE IDTICKET = ?";
			pstmt = conexion.prepareStatement(consultaBillete); // Preparamos la consulta
			pstmt.setInt(1, idBillete); // Establecemos el parámetro de la consulta
			rs = pstmt.executeQuery(); // Ejecutamos la consulta

			// Verificamos si el billete existe
			if (!rs.next()) {
				// Creamos y lanzamos la excepción correspondiente si el billete no existe
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_TICKET);
			}
			idViaje = rs.getInt("IDVIAJE"); // Obtenemos el ID del viaje
			plazasReservadas = rs.getInt("CANTIDAD"); // Obtenemos la cantidad de plazas reservadas
			precioActual = rs.getInt("PRECIO"); // Obtenemos el precio actual

			// Consulta para obtener el número de plazas libres del viaje
			String consultaViaje = "SELECT NPLAZASLIBRES FROM viajes WHERE IDVIAJE = ?";
			pstmt = conexion.prepareStatement(consultaViaje); // Preparamos la consulta
			pstmt.setInt(1, idViaje); // Establecemos el parámetro de la consulta
			rs = pstmt.executeQuery(); // Ejecutamos la consulta

			// Verificamos si el viaje existe
			if (!rs.next()) {
				// Creamos y lanzamos la excepción correspondiente si el viaje no existe
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE);
			}
			plazasLibres = rs.getInt("NPLAZASLIBRES"); // Obtenemos el número de plazas disponibles

			// Calculamos la diferencia de plazas
			int diferenciaPlazas = nuevasPlazas - plazasReservadas;

			// Verificamos si hay suficientes plazas disponibles para realizar la modificación
			if (nuevasPlazas < 0) {
				// Creamos y lanzamos la excepción correspondiente si el nuevo número de plazas es negativo
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_RESERVAS);
			} else if (diferenciaPlazas > 0 && diferenciaPlazas > plazasLibres) {
				// Creamos y lanzamos la excepción correspondiente si no hay suficientes plazas
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_PLAZAS);
			}

			// Actualizamos el número de plazas libres del viaje
			String actualizarViaje = "UPDATE viajes SET NPLAZASLIBRES = ? WHERE IDVIAJE = ?";
			pstmt = conexion.prepareStatement(actualizarViaje); // Preparamos la consulta
			pstmt.setInt(1, plazasLibres - diferenciaPlazas); // Establecemos el nuevo número de plazas libres
			pstmt.setInt(2, idViaje); // Establecemos el ID del viaje
			pstmt.executeUpdate(); // Ejecutamos la actualización

			// Calculamos el nuevo precio del billete
			int nuevoPrecio = (precioActual / plazasReservadas) * nuevasPlazas;

			// Actualizamos el ticket con el nuevo número de plazas y el precio actualizado
			String actualizarBillete = "UPDATE tickets SET CANTIDAD = ?, PRECIO = ? WHERE IDTICKET = ?";
			pstmt = conexion.prepareStatement(actualizarBillete); // Preparamos la consulta
			pstmt.setInt(1, nuevasPlazas); // Establecemos el nuevo número de plazas
			pstmt.setInt(2, nuevoPrecio); // Establecemos el nuevo precio
			pstmt.setInt(3, idBillete); // Establecemos el ID del billete
			pstmt.executeUpdate(); // Ejecutamos la actualización

			conexion.commit(); // Confirmamos los cambios
		} catch (SQLException e) {
			if (conexion != null) {
				conexion.rollback(); // Revertimos los cambios en caso de error
			}
			LOGGER.error(e.getMessage()); // Registramos el mensaje de error
			throw e; // Relanzamos la excepción
		} finally {
			// Cerramos las conexiones y liberamos recursos
			if (rs != null) rs.close();
			if (pstmt != null) pstmt.close();
			if (conexion != null) conexion.close();
		}
	}


	// Método que implementa la lógica de anular billetes de tren.
	@Override
	public void anularBillete(Time hora, java.util.Date fecha, String origen, String destino, int nroPlazas, int ticket)
			throws SQLException {

		// Obtenemos la instancia del pool de conexiones
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		/* Conversiones de fechas y horas */
		java.sql.Date fechaSql = new java.sql.Date(fecha.getTime());
		java.sql.Timestamp horaSql = new java.sql.Timestamp(hora.getTime());

		Connection conexion = null; // Variable para la conexión
		PreparedStatement pstmt = null; // Variable para la consulta preparada
		ResultSet rs = null; // Variable para el resultado de la consulta

		int plazasLibres; // Variable para el número de plazas libres
		int plazasReservadas; // Variable para el número de plazas reservadas
		int idViaje; // Variable para el ID del viaje

		try {
			// Obtenemos una conexión del pool
			conexion = pool.getConnection();

			// Consulta para obtener el ID del viaje y las plazas libres
			String consultaViaje = "SELECT IDVIAJE, NPLAZASLIBRES FROM viajes v JOIN recorridos r ON v.IDRECORRIDO = r.IDRECORRIDO "
					+ "WHERE r.ESTACIONORIGEN = ? AND r.ESTACIONDESTINO = ? AND v.FECHA = ? AND TO_CHAR(r.horaSalida, 'HH24:MI') = ?";
			pstmt = conexion.prepareStatement(consultaViaje);
			pstmt.setString(1, origen);
			pstmt.setString(2, destino);
			pstmt.setDate(3, fechaSql);
			pstmt.setString(4, hora.toString().substring(0, 5));
			rs = pstmt.executeQuery();

			// Verificamos si el viaje existe
			if (rs.next()) {
				idViaje = rs.getInt("IDVIAJE");
				plazasLibres = rs.getInt("NPLAZASLIBRES");
			} else {
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE); // Lanza excepción si el viaje no existe
			}

			// Consulta para obtener la cantidad de plazas reservadas en el ticket
			String consultaTicket = "SELECT CANTIDAD FROM tickets WHERE IDTICKET = ?";
			pstmt = conexion.prepareStatement(consultaTicket);
			pstmt.setInt(1, ticket);
			rs = pstmt.executeQuery();

			// Verificamos si el ticket existe y si se puede anular la cantidad de plazas solicitadas
			if (rs.next()) {
				plazasReservadas = rs.getInt("CANTIDAD");
				if (nroPlazas > plazasReservadas) {
					throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_RESERVAS); // Lanza excepción si se intenta anular más plazas de las reservadas
				}
			} else {
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_TICKET); // Lanza excepción si el ticket no existe
			}

			// Actualizamos el número de plazas libres del viaje
			String actualizarViaje = "UPDATE viajes SET NPLAZASLIBRES = ? WHERE IDVIAJE = ?";
			pstmt = conexion.prepareStatement(actualizarViaje);
			pstmt.setInt(1, plazasLibres + nroPlazas);
			pstmt.setInt(2, idViaje);
			pstmt.executeUpdate();

			// Actualizamos la cantidad de plazas en el ticket o eliminamos el ticket si no quedan plazas
			if (plazasReservadas - nroPlazas > 0) {
				String actualizarTicket = "UPDATE tickets SET CANTIDAD = ? WHERE IDTICKET = ?";
				pstmt = conexion.prepareStatement(actualizarTicket);
				pstmt.setInt(1, plazasReservadas - nroPlazas);
				pstmt.setInt(2, ticket);
				pstmt.executeUpdate();
			} else {
				String eliminarTicket = "DELETE FROM tickets WHERE IDTICKET = ?";
				pstmt = conexion.prepareStatement(eliminarTicket);
				pstmt.setInt(1, ticket);
				pstmt.executeUpdate();
			}

			conexion.commit(); // Confirmamos los cambios
		} catch (SQLException e) {
			if (conexion != null) {
				conexion.rollback(); // Revertimos los cambios en caso de error
			}
			LOGGER.error(e.getMessage()); // Registramos el mensaje de error
			throw e; // Relanzamos la excepción
		} finally {
			// Cerramos las conexiones y liberamos los recursos
			if (rs != null) rs.close();
			if (pstmt != null) pstmt.close();
			if (conexion != null) conexion.close();
		}
	}


	// Método que implementa la lógica de comprar billetes de tren.
	@Override
	public void comprarBillete(Time hora, Date fecha, String origen, String destino, int nroPlazas)
			throws SQLException {
		// Obtiene la instancia del pool de conexiones
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		// Conversión de fecha y hora
		java.sql.Date fechaSql = new java.sql.Date(fecha.getTime());
		String horaStr = hora.toString().substring(0, 5); // Extrae la hora y los minutos

		Connection conexion = null; // Conexión a la base de datos
		PreparedStatement pstmt = null; // Sentencia preparada
		ResultSet rs = null; // Resultado de la consulta
		int precioUnitario; // Precio por plaza
		int idViaje; // ID del viaje
		int plazasDisponibles; // Número de plazas disponibles

		try {
			// Obtiene una conexión del pool
			conexion = pool.getConnection();

			// Consulta para obtener los detalles del viaje
			String consultaViaje = "SELECT PRECIO, IDVIAJE, NPLAZASLIBRES FROM viajes v "
					+ "JOIN recorridos r ON v.IDRECORRIDO = r.IDRECORRIDO "
					+ "WHERE r.ESTACIONORIGEN = ? AND r.ESTACIONDESTINO = ? "
					+ "AND v.FECHA = ? AND TO_CHAR(r.horaSalida, 'HH24:MI') = ?";
			pstmt = conexion.prepareStatement(consultaViaje);
			pstmt.setString(1, origen); // Estación de origen
			pstmt.setString(2, destino); // Estación de destino
			pstmt.setDate(3, fechaSql); // Fecha del viaje
			pstmt.setString(4, horaStr); // Hora del viaje
			rs = pstmt.executeQuery(); // Ejecuta la consulta

			if (!rs.next()) {
				// Si no hay resultados, se lanza excepción por no existir el viaje
				conexion.rollback(); // Deshace los cambios
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE);
			} else {
				// Si el viaje existe, obtiene los detalles
				precioUnitario = rs.getInt("PRECIO"); // Precio por plaza
				idViaje = rs.getInt("IDVIAJE"); // ID del viaje
				plazasDisponibles = rs.getInt("NPLAZASLIBRES"); // Plazas disponibles

				if (plazasDisponibles >= nroPlazas) {
					// Si hay suficientes plazas, realiza la compra
					plazasDisponibles -= nroPlazas; // Actualiza las plazas disponibles
					try {
						// Actualiza el número de plazas libres
						String actualizarViaje = "UPDATE viajes SET NPLAZASLIBRES = ? WHERE IDVIAJE = ?";
						pstmt = conexion.prepareStatement(actualizarViaje);
						pstmt.setInt(1, plazasDisponibles);
						pstmt.setInt(2, idViaje);
						pstmt.executeUpdate();

						// Inserta el nuevo billete
						String insertarTicket = "INSERT INTO tickets VALUES(seq_tickets.nextval, ?, CURRENT_DATE, ?, ?)";
						pstmt = conexion.prepareStatement(insertarTicket);
						pstmt.setInt(1, idViaje);
						pstmt.setInt(2, nroPlazas);
						pstmt.setInt(3, precioUnitario * nroPlazas); // Precio total
						pstmt.executeUpdate();

						conexion.commit(); // Confirma los cambios
					} catch (SQLException e) {
						// Manejo de excepciones SQL durante la compra
						conexion.rollback(); // Deshace los cambios
						throw e;
					}
				} else {
					// Si no hay suficientes plazas, se lanza excepción
					conexion.rollback(); // Deshace los cambios
					throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_PLAZAS);
				}
			}
		} catch (SQLException e) {
			// Manejo de excepciones SQL durante la consulta del viaje
			if (conexion != null) {
				conexion.rollback(); // Deshace los cambios
			}
			throw e;
		} finally {
			// Cierre de recursos
			if (rs != null) rs.close();
			if (pstmt != null) pstmt.close();
			if (conexion != null) conexion.close();
		}
	}

}
