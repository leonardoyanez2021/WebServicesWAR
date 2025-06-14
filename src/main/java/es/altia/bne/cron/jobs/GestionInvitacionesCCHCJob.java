package es.altia.bne.cron.jobs;

import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.model.entities.dto.SctInvitacionesDto;
import es.altia.bne.service.IMailService;
import es.altia.bne.service.model.ISctSectoresService;

@Scope("prototype")
@Component("jobGestionInvitaciones")
@DisallowConcurrentExecution
public class GestionInvitacionesCCHCJob implements Job {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    private IMailService mailService;

    @Autowired
    private ISctSectoresService sctSectoresService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        try {
            this.logger.info("Iniciando job: Gestion Invitaciones CCHC");

            // Se obtienen las invitaciones pendientes de enviar
            final List<SctInvitacionesDto> invitaciones = this.sctSectoresService.findInvitacionesPendientesEnvio();
            // Para cada invitacion, se envía un email avisando de la gestión realizada al usuario responsable del proyecto
            for (final SctInvitacionesDto invitacionPendiente : invitaciones) {
                final String mailAdmin = invitacionPendiente.getEmpresario().getEmail();
                this.mailService.enviarGestionAutomatizadaInvitacionesCCHCBatch(
                        invitacionPendiente.getProyecto().getEmpEmpresarioUsuario().getNombre() + " "
                                + invitacionPendiente.getProyecto().getEmpEmpresarioUsuario().getApellidoPaterno(),
                        mailAdmin, invitacionPendiente);
            }

            // Se actualiza el campo FL_ENVIO_NOTIFICACION de cada una de las invitaciones
            this.sctSectoresService.updateEnvioInvitaciones(invitaciones);

            this.logger.info("Proceso de gestión de invitaciones CCHC - Finaliza correctamente");
        } catch (final Exception e) {
            this.logger.info("Proceso de gestión de invitaciones CCHC - Finaliza con error no controlado", e);

        }
    }

}
