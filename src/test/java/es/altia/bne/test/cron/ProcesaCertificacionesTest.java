package es.altia.bne.test.cron;

import java.text.ParseException;
import java.text.SimpleDateFormat;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.util.Assert;

import es.altia.bne.cron.jobs.ProcesaCertificacionesJob;
import es.altia.bne.service.IAFCCertificacionesService;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:spring-model-unit-test.xml" })
public class ProcesaCertificacionesTest {
	
	@Autowired 
	ProcesaCertificacionesJob jobProcesaCertificaciones;
	
	@Autowired
	IAFCCertificacionesService afcCertificacionesService;
	
	@Test
	public void testEjecucion () throws JobExecutionException {
		
		jobProcesaCertificaciones.setDiaInicioPeriodoValidacion(18);
		jobProcesaCertificaciones.setDiaFinPeriodoValidacion(18);
		jobProcesaCertificaciones.setMesInicioPeriodoValidacion(-1);
		jobProcesaCertificaciones.setMesFinPeriodoValidacion(0);
		jobProcesaCertificaciones.setDiaInicioPeriodoValidacionPrimerGiro(1);
		jobProcesaCertificaciones.setDiaFinPeriodoValidacionPrimerGiro(18);
		jobProcesaCertificaciones.setMesInicioPeriodoValidacionPrimerGiro(0);
		jobProcesaCertificaciones.setMesFinPeriodoValidacionPrimerGiro(0);
		
		jobProcesaCertificaciones.setMailFrom("soporte.licitacion@altia.es");
		jobProcesaCertificaciones.setMailTo("soporte.licitacion@altia.es");
		jobProcesaCertificaciones.setSmtpHost("smtp.altia.es");
		jobProcesaCertificaciones.setSmtpPort(587);
		jobProcesaCertificaciones.setSmtpUser("soporte.licitacion");
		jobProcesaCertificaciones.setSmtpPassword("soporte00");
		jobProcesaCertificaciones.setSmtpAuthType("USERPASS");
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy"); 
		try {
			jobProcesaCertificaciones.setFechaEjecucion(sdf.parse("22/12/2016"));
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			Assert.isTrue(false);
		}
		jobProcesaCertificaciones.execute(null);
		Assert.isTrue(true);
		
	}
	
	@Test
	public void testEjecucionDiaCinco () throws JobExecutionException {
		
		jobProcesaCertificaciones.setDiaInicioPeriodoValidacion(19);
		jobProcesaCertificaciones.setDiaFinPeriodoValidacion(19);
		jobProcesaCertificaciones.setMesInicioPeriodoValidacion(-1);
		jobProcesaCertificaciones.setMesFinPeriodoValidacion(0);
		jobProcesaCertificaciones.setDiaInicioPeriodoValidacionPrimerGiro(19);
		jobProcesaCertificaciones.setDiaFinPeriodoValidacionPrimerGiro(1);
		jobProcesaCertificaciones.setMesInicioPeriodoValidacionPrimerGiro(-1);
		jobProcesaCertificaciones.setMesFinPeriodoValidacionPrimerGiro(0);
		jobProcesaCertificaciones.setPeriodoCertificacion(true);
		
		jobProcesaCertificaciones.setMailFrom("soporte.licitacion@altia.es");
		jobProcesaCertificaciones.setMailTo("soporte.licitacion@altia.es");
		jobProcesaCertificaciones.setSmtpHost("smtp.altia.es");
		jobProcesaCertificaciones.setSmtpPort(587);
		jobProcesaCertificaciones.setSmtpUser("soporte.licitacion");
		jobProcesaCertificaciones.setSmtpPassword("soporte00");
		jobProcesaCertificaciones.setSmtpAuthType("USERPASS");
		SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy"); 
		try {
			jobProcesaCertificaciones.setFechaEjecucion(sdf.parse("05/01/2017"));
		} catch (ParseException e) {
			// TODO Auto-generated catch block
			Assert.isTrue(false);
		}
		jobProcesaCertificaciones.execute(null);
		Assert.isTrue(true);
		
	}	
	
	@Test
	public void testGenerarFichero ()  throws Exception {
				
		afcCertificacionesService.generarFicheroResultado(10, 21, 1, 5);
	}

}
