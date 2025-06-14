package es.altia.bne.cron.jobs;

import java.util.concurrent.TimeUnit;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import com.google.common.base.Stopwatch;
import com.google.common.base.Throwables;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaEncolaNotificacionAfcPagosSmsDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.IAFCPagosService;

@DisallowConcurrentExecution
@Component("jobEncolaNotificacionAfcPagosSms")
@Scope("prototype")
public class EncolaNotificacionAfcPagosSmsJob implements Job {

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaEncolaNotificacionAfcPagosSmsDto xmlAuditoria = null;
        try {
            final Stopwatch sw = Stopwatch.createStarted();
            this.logger.info("Iniciando proceso de encolado de SMS con la notificación de un pago desde AFC");
            xmlAuditoria = this.afcPagosService.encolaNotificacionesAfcPagos();
            resultado = ResultadoIntegracionEnum.OK;
            this.logger.info("Envío de SMS encolados finalizado en {}ms", sw.elapsed(TimeUnit.MILLISECONDS));
        } catch (final DataAccessException e) {
            final Throwable rootCause = Throwables.getRootCause(e);
            this.logger.error("Excepción:", rootCause);
            this.logger.error("Error al encolar notificaciones de pagos vía SMS {} ", e.getClass().getSimpleName());
            xmlAuditoria = new AuditoriaEncolaNotificacionAfcPagosSmsDto();
            xmlAuditoria.setTrazaExcepcion(rootCause);

        } finally {
            // Grabamos auditoría
            final BneAuditoriaIntegracionDto<AuditoriaEncolaNotificacionAfcPagosSmsDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENCOLA_NOTIFICACION_AFC_PAGOS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IAFCPagosService afcPagosService;
}
