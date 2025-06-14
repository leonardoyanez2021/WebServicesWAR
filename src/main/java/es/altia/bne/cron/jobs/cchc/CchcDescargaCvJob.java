package es.altia.bne.cron.jobs.cchc;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.service.model.ICchcDescargaCvService;

@Component("jobCchcDescargaCv")
@Scope("prototype")
@DisallowConcurrentExecution
public class CchcDescargaCvJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private ICchcDescargaCvService cchcDescargaCvService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        this.logger.info("Iniciando proceso batch jobCchcGenerarZipCVYEnviarEmail");
        try {
            this.cchcDescargaCvService.jobCchcGenerarZipCVYEnviarEmail();
        } catch (final Exception e) {
            this.logger.error("Proceso batch jobCchcGenerarZipCVYEnviarEmail - Finaliza con error no controlado", e);
        }
    }
}
