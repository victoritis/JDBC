package lsi.ubu.servicios;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.util.Date;
import lsi.ubu.excepciones.CompraBilleteTrenException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lsi.ubu.util.PoolDeConexiones;

public class ServicioImpl implements Servicio {
	private static final Logger LOGGER = LoggerFactory.getLogger(ServicioImpl.class);

	@Override
	public void anularBillete(Time hora, java.util.Date fecha, String origen, String destino, int nroPlazas, int ticket)
			throws SQLException {
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		/* Conversiones de fechas y horas */
		java.sql.Date fechaSqlDate = new java.sql.Date(fecha.getTime());
		java.sql.Timestamp horaTimestamp = new java.sql.Timestamp(hora.getTime());

		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		// A completar por el alumno
	}

	@Override
	public void comprarBillete(Time hora, Date fecha, String origen, String destino, int nroPlazas)
			throws SQLException {
		PoolDeConexiones pool = PoolDeConexiones.getInstance();

		/* Conversiones de fechas y horas */
		java.sql.Date fechaSqlDate = new java.sql.Date(fecha.getTime());
		java.sql.Timestamp horaTimestamp = new java.sql.Timestamp(hora.getTime());

		Connection con = null;
		PreparedStatement st = null;
		ResultSet rs = null;

		try {
			con = pool.getConnection();

			// Verificar si el viaje existe
			st = con.prepareStatement(
					"SELECT v.idViaje " +
							"FROM viajes v " +
							"INNER JOIN recorridos r ON v.idRecorrido = r.idRecorrido " +
							"WHERE r.estacionOrigen = ? AND r.estacionDestino = ? AND v.fecha = ?");

			st.setString(1, origen);
			st.setString(2, destino);
			st.setDate(3, fechaSqlDate);

			rs = st.executeQuery();

			if (!rs.next()) {
				// No se encontró el viaje
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_EXISTE_VIAJE);
			}

			int idViaje = rs.getInt("idViaje");

			// Verificar si hay plazas disponibles
			st = con.prepareStatement(
					"SELECT (m.nPlazas - COALESCE(SUM(t.cantidad), 0)) AS plazas_disponibles " +
							"FROM modelos m " +
							"INNER JOIN trenes tr ON m.idModelo = tr.modelo " +
							"LEFT JOIN viajes v ON tr.idTren = v.idTren " +
							"LEFT JOIN tickets t ON v.idViaje = t.idViaje " +
							"WHERE v.idViaje = ? " +
							"GROUP BY m.nPlazas");

			st.setInt(1, idViaje);

			rs = st.executeQuery();

			if (!rs.next() || rs.getInt("plazas_disponibles") < nroPlazas) {
				// No hay plazas disponibles
				throw new CompraBilleteTrenException(CompraBilleteTrenException.NO_PLAZAS);
			}

			// Restamos las plazas compradas
			st = con.prepareStatement("update viajes set nPlazasLibres = nPlazasLibres - ? where idViaje= ?");
			st.setInt(1,nroPlazas);
			st.setInt(2,idViaje);

			st.executeUpdate();

			// Obtenemos el valor del precio para calcular
			st = con.prepareStatement(
					"SELECT r.precio " +
							"FROM recorridos r " +
							"INNER JOIN viajes v ON r.idRecorrido = v.idRecorrido " +
							"WHERE v.idViaje = ?");

			st.setInt(1, idViaje);

			rs = st.executeQuery();

			int precioRecorrido = 0;

			if (rs.next()) {
				precioRecorrido = rs.getInt("precio");
			}

			// Calculamos el precio total del viaje
			int precioTotal = nroPlazas * precioRecorrido; // Calcular el precio total

			// Insertamos la fila en la tabla de tickets
			st = con.prepareStatement(
					"INSERT INTO tickets (idTicket, idViaje, fechaCompra, cantidad, precio) " +
							"VALUES (seq_tickets.nextval, ?, ?, ?, ?)");

			st.setInt(1, idViaje);
			st.setDate(2, fechaSqlDate);
			st.setInt(3, nroPlazas);
			st.setInt(4, precioTotal); // Multiplicar el precio por el número de plazas

			st.executeUpdate();

			con.commit();

		} catch (SQLException e) {
			LOGGER.error("Error al realizar la compra de billetes: " + e.getMessage());
			if (con != null) {
				con.rollback(); // Realizamos el rollback de la transacción
			}
			throw e;

		} finally {
			// Cerrar recursos
			if (rs != null) {
				rs.close();
			}
			if (st != null) {
				st.close();
			}
			if (con != null) {
				con.close();
			}
		}
	}

}
