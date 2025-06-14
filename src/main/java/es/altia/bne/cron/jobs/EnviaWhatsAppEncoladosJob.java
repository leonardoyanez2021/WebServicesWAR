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
import es.altia.bne.model.entities.dto.auditoria.AuditoriaEnviaWhatsAppEncoladosDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.whatsApp.ICloudAPIGatewayService;

@DisallowConcurrentExecution
@Component("jobEnviaWhatsAppEncolados")
@Scope("prototype")
public class EnviaWhatsAppEncoladosJob implements Job
{
    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException
    {
        final ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaEnviaWhatsAppEncoladosDto xmlAuditoria = new AuditoriaEnviaWhatsAppEncoladosDto();

        try
        {
            final Stopwatch sw = Stopwatch.createStarted();
            LOGGER.info("Iniciando proceso de envio de WhatsApp encolados");
            xmlAuditoria = this.cloudAPIGatewayService.envioWhatsAppEnColaJob();

            LOGGER.info("Envío de WhatsApp encolados finalizado en {}ms", sw.elapsed(TimeUnit.MILLISECONDS));
        }
        catch (final Exception e)
        {
            final Throwable rootCause = Throwables.getRootCause(e);
            LOGGER.error("Error al enviar WhatsApp encolados {}", e.getClass().getSimpleName());
            LOGGER.error("Excepcion:", rootCause);
            xmlAuditoria.setTrazaExcepcion(rootCause);
        }
        finally
        {
            // Grabamos auditoria
            final BneAuditoriaIntegracionDto<AuditoriaEnviaWhatsAppEncoladosDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ENVIA_WHATSAPP_ENCOLADOS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(EnviaWhatsAppEncoladosJob.class);

    @Autowired
    private ICloudAPIGatewayService cloudAPIGatewayService;
}
