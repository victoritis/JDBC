/*
 * Autor: Victor Gonzalez Del Campo.
 * Versión: 1.0
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

	// Logger para registrar la actividad de la clase.
	private static final Logger LOGGER = LoggerFactory.getLogger(ServicioImpl.class);

	// Método que implementa la lógica de modificación de billetes de tren.
	@Override
	public void modificarBillete(int billeteId, int nuevoNroPlazas) throws SQLException {
		PoolDeConexiones pool = PoolDeConexiones.getInstance();
		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		int precioActual;
		int idViaje;
		int nplazas;
		int plazasReservadas;
		try {
			// Obtenemos una conexión del pool.
			con = pool.getConnection();
			// Consulta para obtener la información del billete actual.
			String select_ticket = "SELECT IDVIAJE, CANTIDAD, PRECIO FROM tickets WHERE IDTICKET = ?";
			st = con.prepareStatement(select_ticket);
			st.setInt(1, billeteId);
			rs = st.executeQuery();
			// Verificamos si el billete existe.
			if (rs.next()) {
				idViaje = rs.getInt("IDVIAJE");
				plazasReservadas = rs.getInt("CANTIDAD");
				precioActual = rs.getInt("PRECIO");
			} else {
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_TICKET);
				throw (e);
			}
			// Consulta para obtener el número de plazas libres del viaje.
			String select_viaje = "SELECT NPLAZASLIBRES FROM viajes WHERE IDVIAJE = ?";
			st = con.prepareStatement(select_viaje);
			st.setInt(1, idViaje);
			rs = st.executeQuery();
			if (rs.next()) {
				nplazas = rs.getInt("NPLAZASLIBRES");
			} else {
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE);
				throw (e);
			}
			// Verificamos si es posible realizar la modificación.
			int diferenciaPlazas = nuevoNroPlazas - plazasReservadas;
			if (diferenciaPlazas > 0 && diferenciaPlazas > nplazas) {
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_PLAZAS);
				throw (e);
			} else if (nuevoNroPlazas < 0) {
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_RESERVAS);
				throw (e);
			}
			// Actualizamos el número de plazas libres del viaje.
			st = con.prepareStatement("UPDATE viajes SET NPLAZASLIBRES = ? WHERE IDVIAJE = ?");
			st.setInt(1, nplazas - diferenciaPlazas);
			st.setInt(2, idViaje);
			st.executeUpdate();
			// Actualizamos el ticket con el nuevo número de plazas y el precio actualizado.
			int nuevoPrecio = (precioActual / plazasReservadas) * nuevoNroPlazas;
			st = con.prepareStatement("UPDATE tickets SET CANTIDAD = ?, PRECIO = ? WHERE IDTICKET = ?");
			st.setInt(1, nuevoNroPlazas);
			st.setInt(2, nuevoPrecio);
			st.setInt(3, billeteId);
			st.executeUpdate();
			// Hacemos commit para confirmar los cambios.
			con.commit();
		} catch (SQLException e) {
			con.rollback();
			LOGGER.error(e.getMessage()); // Registramos el mensaje de error
			throw (e); // Relanzamos la excepción
		} finally {
			// Cerramos las conexiones y liberamos recursos.
			if (rs != null) rs.close();
			if (st != null) st.close();
			if (con != null) con.close();
		}
	}

	// Método que implementa la lógica de anulación de billetes de tren.
	@Override
	public void anularBillete(Time hora, java.util.Date fecha, String origen, String destino, int nroPlazas, int ticket)
			throws SQLException {

		// Obtenemos la instancia del pool de conexiones.
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		/* Conversiones de fecha y hora */
		java.sql.Date fechaSqlDate = new java.sql.Date(fecha.getTime());
		java.sql.Timestamp horaTimestamp = new java.sql.Timestamp(hora.getTime());

		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		int precio;
		int idrecorrido;
		int idViaje;
		int nplazas;
		int plazasReservadas;

		// Extraemos la hora en formato HH:MM.
		String horaDef = hora.toString().substring(0, 5);

		try {
			// Tomamos una conexión del pool.
			con = pool.getConnection();

			// Consultas SQL para obtener y actualizar la información.
			String select_viaje = "SELECT IDVIAJE, NPLAZASLIBRES " +
					"FROM viajes a " +
					"JOIN recorridos b ON a.IDRECORRIDO = b.IDRECORRIDO " +
					"WHERE b.ESTACIONORIGEN = ? " +
					"AND b.ESTACIONDESTINO = ? " +
					"AND a.FECHA = ? " +
					"AND trunc(b.horaSalida) = trunc(?)";

			String select_ticket = "SELECT CANTIDAD FROM tickets WHERE IDTICKET = ?";
			String update_plazasLibres = "UPDATE viajes SET NPLAZASLIBRES = ? WHERE IDVIAJE = ?";
			String update_cantidadTicket = "UPDATE tickets SET CANTIDAD = ? WHERE IDTICKET = ?";
			String delete_ticket = "DELETE FROM tickets WHERE IDTICKET = ?";

			// Obtenemos el ID del viaje y el número de plazas libres.
			st = con.prepareStatement(select_viaje);
			st.setString(1, origen);
			st.setString(2, destino);
			st.setDate(3, fechaSqlDate);
			st.setTimestamp(4, horaTimestamp);
			rs = st.executeQuery();

			// Verificamos si el viaje existe.
			if (rs.next()) {
				idViaje = rs.getInt(1);
				nplazas = rs.getInt(2);
			} else {
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE);
				throw (e);
			}

			// Obtenemos el número de plazas reservadas en el ticket.
			st = con.prepareStatement(select_ticket);
			st.setInt(1, ticket);
			rs = st.executeQuery();

			// Verificamos si el ticket existe y si es posible anular el número de plazas solicitado.
			if (rs.next()) {
				plazasReservadas = rs.getInt(1);
				if (nroPlazas > plazasReservadas) {
					// Creamos y lanzamos la excepción correspondiente.
					CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_RESERVAS);
					throw (e);
				}
			} else {
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_TICKET);
				throw (e);
			}

			// Actualizamos el número de plazas libres del viaje.
			st = con.prepareStatement(update_plazasLibres);
			st.setInt(1, nplazas + nroPlazas);
			st.setInt(2, idViaje);
			st.executeUpdate();

			// Actualizamos la cantidad de plazas en el ticket.
			if (rs.getInt("cantidad") - nroPlazas > 0) {
				st = con.prepareStatement(update_cantidadTicket);
				st.setInt(1, rs.getInt("cantidad") - nroPlazas);
				st.setInt(2, ticket);
				st.executeUpdate();
			}
			// Si el ticket se queda sin plazas, lo eliminamos.
			else if (rs.getInt("cantidad") - nroPlazas == 0) {
				st = con.prepareStatement(delete_ticket);
				st.setInt(1, ticket);
				st.executeUpdate();
			}

			// Hacemos commit para confirmar los cambios.
			con.commit();
		} catch (SQLException e) {
			con.rollback();
			LOGGER.error(e.getMessage()); // Registramos el mensaje de error
			throw (e); // Relanzamos la excepción
		} finally {
			// Cerramos las conexiones y liberamos recursos.
			if (rs != null) rs.close();
			if (st != null) st.close();
			if (con != null) con.close();
		}
	}

	// Método que implementa la lógica de compra de billetes de tren.
	@Override
	public void comprarBillete(Time hora, Date fecha, String origen, String destino, int nroPlazas)
			throws SQLException {
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		java.sql.Date fechaSqlDate = new java.sql.Date(fecha.getTime());
		java.sql.Timestamp horaTimestamp = new java.sql.Timestamp(hora.getTime());

		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;
		int precio;
		int idViaje;
		int nplazas;

		// Extraemos la hora en formato HH:MM.
		String horaDef = hora.toString().substring(0, 5);
		con = pool.getConnection(); // Tomamos una conexión del pool

		try {
			// Consulta para encontrar el viaje solicitado por el usuario.
			st = con.prepareStatement("SELECT PRECIO,IDVIAJE,NPLAZASLIBRES FROM viajes a JOIN recorridos b ON a.IDRECORRIDO = b.IDRECORRIDO "
					+ "WHERE b.ESTACIONORIGEN = ? AND b.ESTACIONDESTINO = ? AND a.FECHA = ? AND trunc(b.horaSalida) = trunc(?)");
			st.setString(1, origen); // Establecemos la ciudad de origen
			st.setString(2, destino); // Establecemos la ciudad de destino
			st.setDate(3, fechaSqlDate); // Establecemos la fecha de salida
			st.setTimestamp(4, horaTimestamp); // Establecemos la hora de salida
			rs = st.executeQuery(); // Ejecutamos la consulta

		} catch (SQLException e) { // En caso de error SQL
			con.rollback(); // Hacemos rollback de la transacción
			LOGGER.error(e.getMessage()); // Registramos el mensaje de error
			throw (e); // Relanzamos la excepción
		}

		if (!rs.next()) { // Si no se encuentra un viaje con los parámetros proporcionados
			con.rollback(); // Hacemos rollback
			// Creamos y lanzamos la excepción correspondiente.
			CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE);
			LOGGER.error(e.getMessage()); // Registramos el mensaje de error
			throw (e);

		} else { // Si se encuentra el viaje
			precio = rs.getInt(1); // Obtenemos el precio del viaje
			idViaje = rs.getInt(2); // Obtenemos el ID del viaje
			nplazas = rs.getInt(3); // Obtenemos el número de plazas libres

			if (nplazas >= nroPlazas) { // Si hay suficientes plazas disponibles
				try {
					nplazas = nplazas - nroPlazas; // Restamos el número de plazas reservadas
					// Actualizamos el número de plazas libres del viaje
					st = con.prepareStatement("UPDATE viajes SET NPLAZASLIBRES = ? WHERE IDVIAJE = ?");
					st.setInt(1, nplazas); // Establecemos el nuevo número de plazas libres
					st.setInt(2, idViaje); // Establecemos el ID del viaje
					st.executeUpdate(); // Ejecutamos la actualización

					// Insertamos el nuevo ticket en la base de datos
					st = con.prepareStatement("INSERT INTO tickets VALUES(seq_tickets.nextval, ?, CURRENT_DATE, ?, ?)");
					st.setInt(1, idViaje); // Establecemos el ID del viaje
					st.setInt(2, nroPlazas); // Establecemos el número de plazas reservadas
					precio = precio * nroPlazas; // Calculamos el precio total
					st.setInt(3, precio); // Establecemos el precio total
					st.executeUpdate(); // Ejecutamos la inserción
					con.commit(); // Confirmamos los cambios

				} catch (SQLException e) { // En caso de error SQL
					con.rollback(); // Hacemos rollback de la transacción
					LOGGER.error(e.getMessage()); // Registramos el mensaje de error
					throw (e); // Relanzamos la excepción
				}
			} else { // Si no hay suficientes plazas disponibles
				con.rollback(); // Hacemos rollback
				// Creamos y lanzamos la excepción correspondiente.
				CompraBilleteTrenException e = new CompraBilleteTrenException(CompraBilleteTrenException.NO_PLAZAS);
				LOGGER.error(e.getMessage()); // Registramos el mensaje de error
				throw (e);
			}
		}

		if (st != null) st.close(); // Cerramos la declaración preparada
		if (con != null) con.close(); // Cerramos la conexión
	}
}
