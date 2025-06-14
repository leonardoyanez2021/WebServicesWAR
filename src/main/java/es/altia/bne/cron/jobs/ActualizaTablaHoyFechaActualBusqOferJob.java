package es.altia.bne.cron.jobs;

import java.text.SimpleDateFormat;
import java.util.Date;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaActualizarFechaTablaHoy;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.IBuscadorOfertasService;

@DisallowConcurrentExecution
@Component("jobActualizaTablaHoyFechaActualBusqOfer")
@Scope("prototype")
public class ActualizaTablaHoyFechaActualBusqOferJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    @Autowired
    private IBuscadorOfertasService busquedaOfertasService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        final SimpleDateFormat sdf = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss.SSS");
        this.logger.info("Inicia Job actualizar tabla HOY - " + sdf.format(new Date()));
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaActualizarFechaTablaHoy xmlAuditoria = null;
        final Date horaInicio;
        final Date horaFin;
        try {
            horaInicio = DateUtils.now();
            String respuestaProcedure = "";
            this.busquedaOfertasService.updateFechaActualTablaHoy();
            respuestaProcedure = sdf.format(new Date());
            horaFin = DateUtils.now();
            xmlAuditoria = new AuditoriaActualizarFechaTablaHoy(horaInicio, horaFin, ResultadoIntegracionEnum.OK.getId(),
                    ResultadoIntegracionEnum.OK.getDescripcion(), respuestaProcedure);
            resultado = ResultadoIntegracionEnum.OK;
        } catch (final Exception e) {
            this.logger.error("Job actualizar tabla HOY - Error " + e.getMessage(), e);
            xmlAuditoria = new AuditoriaActualizarFechaTablaHoy();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;
        } finally {
            this.logger.info("Proceso Job actualizar tabla HOY - Fin " + sdf.format(new Date()));
            final BneAuditoriaIntegracionDto<AuditoriaActualizarFechaTablaHoy> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ACTUALIZA_TABLA_HOY);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }

}
