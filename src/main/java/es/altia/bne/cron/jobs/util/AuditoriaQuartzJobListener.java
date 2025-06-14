package es.altia.bne.cron.jobs.util;

import java.io.IOException;

import javax.mail.MessagingException;
import javax.xml.bind.JAXBException;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.auditoria.DatosXmlBneAuditoriaIntegracionDto;
import es.altia.bne.model.exception.DataAccessException;
import es.altia.bne.service.IBneAuditoriaIntegracionService;
import es.altia.bne.service.IMailService;

@Component("quartzJobListener")
public class AuditoriaQuartzJobListener implements JobListener {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public static final String LISTENER_NAME = "ListenerProcesosBatchBNE";

    @Autowired
    IBneAuditoriaIntegracionService auditoriaIntegracionService;

    @Autowired
    private IMailService mailService;

    @Override
    public String getName() {
        return LISTENER_NAME;
    }

    @Override
    public void jobExecutionVetoed(final JobExecutionContext context) {
        this.logger.info("jobExecutionVetoed");
    }

    @Override
    public void jobToBeExecuted(final JobExecutionContext context) {
        this.logger.info("jobToBeExecuted");
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void jobWasExecuted(final JobExecutionContext context, final JobExecutionException ex) {
        this.logger.info("jobWasExecuted");

        final Object result = context.getResult();
        if (result instanceof BneAuditoriaIntegracionDto) {
            final BneAuditoriaIntegracionDto<? extends DatosXmlBneAuditoriaIntegracionDto> aud = (BneAuditoriaIntegracionDto) result;
            if (aud.isPersistable()) {
                try {
                    this.auditoriaIntegracionService.saveAuditoriaProceso(aud);
                    this.logger.info("Resultado del proceso: {}", aud.getResultado().getCodigo());
                } catch (final JAXBException | DataAccessException e) {
                    this.logger.error("Error almacenando resultado del proceso", e);
                    // Do not throw
                }
            }
            if (aud.isToReport()) {
                try {
                    this.mailService.enviarResultadoJob(aud);
                } catch (IOException | MessagingException e) {
                    this.logger.error("Error enviando email con el resultado del proceso", e);
                    // Do not throw
                }
            }
        }
    }

}
