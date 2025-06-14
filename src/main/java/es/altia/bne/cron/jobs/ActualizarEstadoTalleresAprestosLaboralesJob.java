package es.altia.bne.cron.jobs;

import java.util.List;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import es.altia.bne.comun.util.date.DateUtils;
import es.altia.bne.model.entities.dto.BneAuditoriaIntegracionDto;
import es.altia.bne.model.entities.dto.ParticipantesInscritosTallerAprestosLaboralesDto;
import es.altia.bne.model.entities.dto.auditoria.AuditoriaActualizarEstadoTalleresDto;
import es.altia.bne.model.entities.enumerados.AccionAuditoriaIntegracionEnum;
import es.altia.bne.model.entities.enumerados.ResultadoIntegracionEnum;
import es.altia.bne.service.api.intermediacionweb.auditoriaAcciones.IIlAudAccionesPerService;
import es.altia.bne.service.api.jobs.aprestos.ITallerAprestosJobService;
import es.altia.bne.service.api.jobs.aprestos.IParticipantesInscritosTallerAprestosLaboralesService;

@Component("jobActualizarEstadoTalleres")
@Scope("prototype")
@DisallowConcurrentExecution
public class ActualizarEstadoTalleresAprestosLaboralesJob implements Job {

    @Value("${bne.integraciones.tallerAprestos.numDias.correo}")
    private int diasCorreo;
    @Value("${bne.integraciones.tallerAprestos.numDias.actualizarEstado}")
    private int diasUpdateEstado;

    private static final Logger LOGGER = LoggerFactory.getLogger(ActualizarEstadoTalleresAprestosLaboralesJob.class);

    @Autowired
    ITallerAprestosJobService tallerAprestosJobService;

    @Autowired
    IParticipantesInscritosTallerAprestosLaboralesService participantesInscritosTallerAprestosLaboralesService;

    @Autowired
    IIlAudAccionesPerService audAccionesPerService;

    @Override
    public void execute(final JobExecutionContext context) throws JobExecutionException {
        LOGGER.info("Iniciando proceso de actualización del estado de las talleres que han superado su fecha de fin");
        ResultadoIntegracionEnum resultado = ResultadoIntegracionEnum.ERROR;
        AuditoriaActualizarEstadoTalleresDto xmlAuditoria = new AuditoriaActualizarEstadoTalleresDto();
        try {
            xmlAuditoria.setTalleresTratados(this.tallerAprestosJobService.updateEstados(this.diasUpdateEstado));
            resultado = ResultadoIntegracionEnum.OK;

            LOGGER.info("Auditando datos de participantes que asistieron al taller");
            // Select de los inscritos mayores de edad
            final List<ParticipantesInscritosTallerAprestosLaboralesDto> asistentes = this.participantesInscritosTallerAprestosLaboralesService
                    .selectPersonasAsistentes();
            // Insertar auditoria de personas asistentes
            xmlAuditoria.setPersonasAuditadas(this.audAccionesPerService.saveAsistentes(asistentes));
            // Updatear inscritos
            this.participantesInscritosTallerAprestosLaboralesService.aniadirAuditoriaAsistentes(asistentes);
            // Envío mail de aviso de finalización de cursos
            xmlAuditoria
                    .setCorreosEnviados(this.participantesInscritosTallerAprestosLaboralesService.notificacionFinTaller(this.diasCorreo));
        } catch (final Exception e) {
            LOGGER.error("Proceso nocturno talleres de apresto - Finaliza con error no controlado");
            xmlAuditoria = new AuditoriaActualizarEstadoTalleresDto();
            xmlAuditoria.setTrazaExcepcion(e);
            xmlAuditoria.setDescripcionResultado(e.getMessage());
            resultado = ResultadoIntegracionEnum.ERROR;

        } finally {
            final BneAuditoriaIntegracionDto<AuditoriaActualizarEstadoTalleresDto> a = new BneAuditoriaIntegracionDto<>();
            a.setPersistable(true);
            a.setAccion(AccionAuditoriaIntegracionEnum.ACTUALIZA_ESTADOS_TALLERES_APRESTOS);
            a.setFecha(DateUtils.now());
            a.setResultado(resultado);
            a.setDatosXml(xmlAuditoria);
            context.setResult(a);
        }

    }
}
