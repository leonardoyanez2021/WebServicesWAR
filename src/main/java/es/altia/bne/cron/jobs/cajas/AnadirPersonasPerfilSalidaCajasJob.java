package es.altia.bne.cron.jobs.cajas;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import es.altia.bne.service.model.ICajasCompensacionService;

@Scope("prototype")
@Component("jobAnadirPersonasPerfilSalidaCajas")
@DisallowConcurrentExecution
public class AnadirPersonasPerfilSalidaCajasJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    ICajasCompensacionService cajasService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            this.logger.info("Iniciando job: Añadir personas a perfil de salida de Cajas");

            this.cajasService.jobAddPersonasPerfil();

            this.logger.info("Proceso de añadir personas a perfil de salida de Cajas - Finaliza correctamente");
        } catch (final Exception e) {
            this.logger.info("Proceso de añadir personas a perfil de salida de Cajas - Finaliza con error no controlado", e);

        }
    }

}
